package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Persistent order state; business operations live in TerminalOrders. */
final class TerminalOrderStore extends SavedData {
    private static final Factory<TerminalOrderStore> FACTORY =
            new Factory<>(TerminalOrderStore::new, TerminalOrderStore::load);
    final Map<UUID, Order> orders = new LinkedHashMap<>();
    final TerminalOrders service = new TerminalOrders(this);
    private int nextLogisticsId = Integer.MIN_VALUE;

    static TerminalOrderStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "ShHsTerminalOrders");
    }

    static final class Order {
        final UUID id;
        final UUID owner;
        final int netId;
        boolean ended;
        final List<ItemStack> held = new ArrayList<>();
        final List<TerminalPackageEntry> heldResources = new ArrayList<>();
        final List<ItemStack> packages = new ArrayList<>();
        final List<Shipment> shipments = new ArrayList<>();

        Order(UUID id, UUID owner, int netId) {
            this.id = id;
            this.owner = owner;
            this.netId = netId;
        }

        boolean ready() {
            return ended || shipments.stream().allMatch(shipment -> shipment.remaining.isEmpty());
        }

        boolean visible() {
            return !ready() || !held.isEmpty() || !heldResources.isEmpty() || !packages.isEmpty();
        }
    }

    static final class Shipment {
        int id;
        UUID network;
        GlobalPos station;
        String address;
        final List<ItemStack> requested = new ArrayList<>();
        final List<ItemStack> remaining = new ArrayList<>();
        final Set<String> received = new HashSet<>();
    }

    int allocateId() {
        Set<Integer> used = new HashSet<>();
        orders.values().forEach(order -> order.shipments.forEach(shipment -> used.add(shipment.id)));
        while (nextLogisticsId == -1 || used.contains(nextLogisticsId))
            nextLogisticsId++;
        return nextLogisticsId++;
    }

    List<Order> forPlayer(UUID player) {
        return orders.values().stream().filter(o -> o.owner.equals(player) && o.visible()).toList();
    }

    private static TerminalOrderStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalOrderStore data = new TerminalOrderStore();
        data.nextLogisticsId = tag.contains("NextId") ? tag.getInt("NextId") : Integer.MIN_VALUE;
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id") || !t.hasUUID("Owner"))
                continue;
            Order order = new Order(t.getUUID("Id"), t.getUUID("Owner"), t.getInt("Net"));
            order.ended = t.getBoolean("Ended");
            order.held.addAll(TerminalData.stacks(t.getList("Held", Tag.TAG_COMPOUND), registries));
            ListTag heldResources = t.getList("HeldResources", Tag.TAG_COMPOUND);
            for (int j = 0; j < heldResources.size(); j++) {
                CompoundTag resource = heldResources.getCompound(j);
                ItemStack key = ItemStack.parseOptional(registries, resource.getCompound("Key"));
                ResourceLocation type = ResourceLocation.tryParse(resource.getString("Type"));
                int amount = resource.getInt("Amount");
                if (!key.isEmpty() && type != null && amount > 0)
                    order.heldResources.add(new TerminalPackageEntry(key, amount, type));
            }
            order.packages.addAll(TerminalData.stacks(t.getList("Packages", Tag.TAG_COMPOUND), registries));
            ListTag shipments = t.getList("Shipments", Tag.TAG_COMPOUND);
            for (int j = 0; j < shipments.size(); j++) {
                CompoundTag s = shipments.getCompound(j);
                Shipment shipment = new Shipment();
                shipment.id = s.getInt("Id");
                shipment.network = s.getUUID("Network");
                shipment.station = GlobalPos.of(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(s.getString("Dimension"))), BlockPos.of(s.getLong("Pos")));
                shipment.address = s.getString("Address");
                shipment.requested.addAll(TerminalData.stacks(s.getList("Requested", Tag.TAG_COMPOUND), registries));
                shipment.remaining.addAll(TerminalData.stacks(s.getList("Remaining", Tag.TAG_COMPOUND), registries));
                for (Tag fragment : s.getList("Received", Tag.TAG_STRING))
                    shipment.received.add(fragment.getAsString());
                order.shipments.add(shipment);
            }
            migrateRequested(order);
            data.orders.put(order.id, order);
        }
        return data;
    }

    private static void migrateRequested(Order order) {
        if (order.shipments.stream().noneMatch(shipment -> shipment.requested.isEmpty()))
            return;
        for (Shipment shipment : order.shipments) {
            shipment.requested.clear();
            shipment.remaining.forEach(stack -> TerminalData.append(shipment.requested, stack, stack.getCount()));
        }
        if (order.shipments.isEmpty())
            return;
        // Old saves did not retain the originating network for delivered items. Assign them
        // deterministically to a matching shipment, falling back to the first shipment.
        List<ItemStack> delivered = order.held.stream().map(ItemStack::copy)
                .collect(Collectors.toCollection(ArrayList::new));
        for (ItemStack box : order.packages) {
            var contents = PackageItem.getContents(box);
            for (int i = 0; i < contents.getSlots(); i++)
                if (!contents.getStackInSlot(i).isEmpty())
                    delivered.add(contents.getStackInSlot(i).copy());
        }
        for (ItemStack stack : delivered) {
            Shipment target = order.shipments.stream().filter(shipment -> shipment.requested.stream()
                    .anyMatch(expected -> ItemStack.isSameItemSameComponents(expected, stack)))
                    .findFirst().orElse(order.shipments.getFirst());
            TerminalData.append(target.requested, stack, stack.getCount());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", nextLogisticsId);
        ListTag list = new ListTag();
        for (Order order : orders.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", order.id);
            t.putUUID("Owner", order.owner);
            t.putInt("Net", order.netId);
            t.putBoolean("Ended", order.ended);
            t.put("Held", TerminalData.stacks(order.held, registries));
            ListTag heldResources = new ListTag();
            for (TerminalPackageEntry entry : order.heldResources) {
                CompoundTag resource = new CompoundTag();
                resource.put("Key", entry.key().save(registries));
                resource.putString("Type", entry.resourceType().toString());
                resource.putInt("Amount", entry.amount());
                heldResources.add(resource);
            }
            t.put("HeldResources", heldResources);
            t.put("Packages", TerminalData.stacks(order.packages, registries));
            ListTag shipments = new ListTag();
            for (Shipment shipment : order.shipments) {
                CompoundTag s = new CompoundTag();
                s.putInt("Id", shipment.id);
                s.putUUID("Network", shipment.network);
                s.putString("Dimension", shipment.station.dimension().location().toString());
                s.putLong("Pos", shipment.station.pos().asLong());
                s.putString("Address", shipment.address);
                s.put("Requested", TerminalData.stacks(shipment.requested, registries));
                s.put("Remaining", TerminalData.stacks(shipment.remaining, registries));
                ListTag fragments = new ListTag();
                shipment.received.stream().sorted().forEach(fragment -> fragments.add(StringTag.valueOf(fragment)));
                s.put("Received", fragments);
                shipments.add(s);
            }
            t.put("Shipments", shipments);
            list.add(t);
        }
        tag.put("Orders", list);
        return tag;
    }
}
