package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Real items live here after submission, never in a Screen or a menu instance. */
public final class TerminalOrders extends SavedData {
    private static final Factory<TerminalOrders> FACTORY = new Factory<>(TerminalOrders::new, TerminalOrders::load);
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private int nextLogisticsId = Integer.MIN_VALUE;

    public static TerminalOrders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "ShHsTerminalOrders");
    }

    public static final class Order {
        public final UUID id;
        public final UUID owner;
        public final int netId;
        public boolean ended;
        public final List<ItemStack> held = new ArrayList<>();
        public final List<ItemStack> packages = new ArrayList<>();
        public final List<Shipment> shipments = new ArrayList<>();
        public Order(UUID id, UUID owner, int netId) {
            this.id = id; this.owner = owner; this.netId = netId;
        }
        public boolean ready() { return ended || shipments.stream().allMatch(s -> s.remaining.isEmpty()); }
        public boolean visible() { return !ready() || !held.isEmpty() || !packages.isEmpty(); }
    }

    private static final class Shipment {
        int id;
        UUID network;
        GlobalPos station;
        String address;
        final List<ItemStack> remaining = new ArrayList<>();
        final Set<String> received = new HashSet<>();
    }

    private int allocateId() {
        Set<Integer> used = new HashSet<>();
        orders.values().forEach(o -> o.shipments.forEach(s -> used.add(s.id)));
        while (nextLogisticsId == -1 || used.contains(nextLogisticsId)) nextLogisticsId++;
        return nextLogisticsId++;
    }

    public List<Order> forPlayer(UUID player) {
        return orders.values().stream().filter(o -> o.owner.equals(player) && o.visible()).toList();
    }

    public boolean ownsLogisticsId(int id) {
        return orders.values().stream().anyMatch(order -> order.shipments.stream().anyMatch(shipment -> shipment.id == id));
    }

    public boolean submit(ServerPlayer player, DimensionsNet net, UUID submission, List<TerminalStock.Entry> entries) {
        if (orders.containsKey(submission)) return orders.get(submission).owner.equals(player.getUUID());
        if (entries.isEmpty() || entries.size() > TerminalData.MAX_LINES
                || forPlayer(player.getUUID()).size() >= TerminalData.MAX_ACTIVE_ORDERS) return false;
        Map<TerminalData.Selection, Long> demand = new LinkedHashMap<>();
        long total = 0;
        for (var entry : entries) {
            if (entry.stack().isEmpty() || entry.amount() <= 0 || entry.amount() > TerminalData.MAX_ITEMS) return false;
            total += entry.amount();
            if (total > TerminalData.MAX_ITEMS) return false;
            demand.merge(new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network()), entry.amount(), Long::sum);
        }
        var routes = TerminalStock.routes(player, net.getId());
        Map<UUID, List<BigItemStack>> external = new LinkedHashMap<>();
        for (var entry : demand.entrySet()) {
            var selection = entry.getKey();
            if (selection.network() == null) {
                if (net.getUnifiedStorage().extract(selection.key(), entry.getValue(), true, false).amount() != entry.getValue()) return false;
            } else {
                var route = routes.get(selection.network());
                if (route == null || route.address().isBlank()) return false;
                external.computeIfAbsent(selection.network(), ignored -> new ArrayList<>())
                        .add(new BigItemStack(selection.key().copyStackWithCount(1), entry.getValue().intValue()));
            }
        }
        Order order = new Order(submission, player.getUUID(), net.getId());
        Multimap<PackagerBlockEntity, PackagingRequest> planned = ArrayListMultimap.create();
        // All networks are planned before any inventory is changed.
        Map<Object, Map<ItemStackKey, Long>> plannedAmounts = new HashMap<>();
        for (var request : external.entrySet()) {
            var route = routes.get(request.getKey());
            var packages = LogisticsManager.findPackagersForRequest(route.network(),
                    PackageOrderWithCrafts.simple(request.getValue()), route.ignored(), route.address());
            Map<ItemStackKey, Long> totals = new HashMap<>();
            for (var entry : packages.entries()) {
                PackagerBlockEntity packager = entry.getKey();
                if (packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.PLAYER)) return false;
                var key = new ItemStackKey(entry.getValue().item());
                long amount = entry.getValue().getCount();
                totals.merge(key, amount, Long::sum);
                var inventory = packager.targetInventory.getIdentifiedInventory();
                Object identity = inventory != null && inventory.identifier() != null ? inventory.identifier() : packager;
                long reserved = plannedAmounts.computeIfAbsent(identity, ignored -> new HashMap<>()).merge(key, amount, Long::sum);
                if (reserved > packager.getAvailableItems().getCountOf(key.copyStackWithCount(1))) return false;
            }
            for (var stack : request.getValue())
                if (totals.getOrDefault(new ItemStackKey(stack.stack), 0L) != stack.count) return false;
            Shipment shipment = new Shipment();
            shipment.id = allocateId(); shipment.network = route.network(); shipment.station = route.station(); shipment.address = route.address();
            for (var stack : request.getValue()) TerminalData.append(shipment.remaining, stack.stack, stack.count);
            order.shipments.add(shipment);
            for (var entry : packages.entries()) {
                PackagingRequest r = entry.getValue();
                planned.put(entry.getKey(), new PackagingRequest(r.item(), r.count(), r.address(), r.linkIndex(),
                        r.finalLink(), r.packageCounter(), shipment.id, r.context()));
            }
        }
        orders.put(order.id, order);
        setDirty();
        for (var entry : demand.entrySet()) {
            if (entry.getKey().network() != null) continue;
            var key = entry.getKey().key();
            long extracted = net.getUnifiedStorage().extract(key, entry.getValue(), false, false).amount();
            TerminalData.append(order.held, key.copyStackWithCount(1), extracted);
            if (extracted != entry.getValue()) {
                // A storage event vetoed extraction: return what was actually taken; retain overflow.
                for (ItemStack stack : order.held) {
                    stack.setCount((int) net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount());
                }
                order.held.removeIf(ItemStack::isEmpty);
                order.shipments.clear(); order.ended = true;
                return false;
            }
        }
        // Create's convenience method stops after 100 boxes. Drain this bounded request explicitly instead.
        for (var batch : planned.asMap().entrySet()) {
            PackagerBlockEntity packager = batch.getKey();
            List<PackagingRequest> queue = new ArrayList<>(batch.getValue());
            packager.flashLink();
            for (int attempt = 0; !queue.isEmpty() && attempt < TerminalData.MAX_ITEMS; attempt++) {
                long before = queue.stream().mapToLong(PackagingRequest::getCount).sum();
                packager.attemptToSend(queue);
                if (queue.stream().mapToLong(PackagingRequest::getCount).sum() >= before) break;
            }
            packager.triggerStockCheck(); packager.notifyUpdate();
        }
        TerminalStock.clearCache();
        if (order.shipments.isEmpty()) claim(player, order.id, false);
        return true;
    }

    /** Null means an ordinary package. False leaves a recognized package intact. */
    public @Nullable Boolean receive(ServerLevel level, BlockPos target, ItemStack box, boolean simulate) {
        if (!PackageItem.hasOrderData(box)) return null;
        int id = PackageItem.getOrderId(box);
        for (Order order : orders.values()) for (Shipment shipment : order.shipments) {
            if (shipment.id != id || !shipment.station.equals(GlobalPos.of(level.dimension(), target))
                    || !shipment.address.equals(PackageItem.getAddress(box))) continue;
            if (!(level.getBlockEntity(target) instanceof DimensionParcelStationBlockEntity station)
                    || !station.isAllowed(DimensionParcelStationBlockEntity.Channel.ITEM_INPUT)) return false;
            String fragment = PackageItem.getLinkIndex(box) + ":" + PackageItem.getIndex(box);
            if (shipment.received.contains(fragment)) return false;
            List<ItemStack> contents = new ArrayList<>();
            var handler = PackageItem.getContents(box);
            for (int i = 0; i < handler.getSlots(); i++)
                if (!handler.getStackInSlot(i).isEmpty()) contents.add(handler.getStackInSlot(i).copy());
            List<ItemStack> remaining = shipment.remaining.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            for (ItemStack stack : contents) {
                int need = stack.getCount();
                for (ItemStack expected : remaining) if (ItemStack.isSameItemSameComponents(stack, expected)) {
                    int used = Math.min(need, expected.getCount()); expected.shrink(used); need -= used;
                }
                if (need != 0) return false;
            }
            if (simulate) return true;
            shipment.remaining.clear();
            remaining.stream().filter(s -> !s.isEmpty()).forEach(shipment.remaining::add);
            shipment.received.add(fragment);
            for (ItemStack stack : contents) {
                long held = stack.getCount();
                if (order.ended) {
                    DimensionsNet original = DimensionsNet.getNetFromId(order.netId);
                    if (original != null) held = original.getUnifiedStorage().insert(new ItemStackKey(stack), held, false).amount();
                }
                TerminalData.append(order.held, stack, held);
            }
            setDirty();
            return true;
        }
        return null;
    }

    public void claim(ServerPlayer player, UUID id, boolean endWaiting) {
        Order order = orders.get(id);
        if (order == null || !order.owner.equals(player.getUUID())) return;
        if (endWaiting) order.ended = true;
        if (!order.ready()) return;
        pack(order);
        Iterator<ItemStack> iterator = order.packages.iterator();
        while (iterator.hasNext()) {
            ItemStack box = iterator.next();
            player.getInventory().add(box);
            if (box.isEmpty()) iterator.remove();
        }
        player.containerMenu.broadcastChanges();
        setDirty();
    }

    private static void pack(Order order) {
        order.held.removeIf(ItemStack::isEmpty);
        while (!order.held.isEmpty()) {
            ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
            int slot = 0;
            while (slot < PackageItem.SLOTS && !order.held.isEmpty()) {
                ItemStack stack = order.held.getFirst();
                boolean bulky = !stack.getItem().canFitInsideContainerItems();
                if (bulky && slot != 0) break;
                int count = Math.min(stack.getCount(), stack.getMaxStackSize());
                contents.setStackInSlot(slot++, stack.split(count));
                if (stack.isEmpty()) order.held.removeFirst();
                if (bulky) break;
            }
            order.packages.add(PackageItem.containing(contents));
        }
    }

    public ListTag summaries(ServerPlayer player) {
        ListTag result = new ListTag();
        for (Order order : forPlayer(player.getUUID())) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", order.id); tag.putInt("Net", order.netId);
            tag.putBoolean("Ready", order.ready()); tag.putBoolean("Ended", order.ended);
            tag.putInt("Held", order.held.stream().mapToInt(ItemStack::getCount).sum());
            tag.putInt("Packages", order.packages.size());
            Map<ItemStackKey, Long> missing = new LinkedHashMap<>();
            order.shipments.forEach(s -> s.remaining.forEach(stack -> missing.merge(new ItemStackKey(stack), (long) stack.getCount(), Long::sum)));
            ListTag missingTags = new ListTag();
            missing.forEach((key, count) -> missingTags.add(TerminalData.entry(new TerminalStock.Entry(key.copyStackWithCount(1), count, null, true), player.registryAccess())));
            tag.put("Missing", missingTags);
            result.add(tag);
        }
        return result;
    }

    static TerminalOrders load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalOrders data = new TerminalOrders();
        data.nextLogisticsId = tag.contains("NextId") ? tag.getInt("NextId") : Integer.MIN_VALUE;
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id") || !t.hasUUID("Owner")) continue;
            Order order = new Order(t.getUUID("Id"), t.getUUID("Owner"), t.getInt("Net"));
            order.ended = t.getBoolean("Ended");
            order.held.addAll(TerminalData.stacks(t.getList("Held", Tag.TAG_COMPOUND), registries));
            order.packages.addAll(TerminalData.stacks(t.getList("Packages", Tag.TAG_COMPOUND), registries));
            ListTag shipments = t.getList("Shipments", Tag.TAG_COMPOUND);
            for (int j = 0; j < shipments.size(); j++) {
                CompoundTag s = shipments.getCompound(j);
                Shipment shipment = new Shipment();
                shipment.id = s.getInt("Id"); shipment.network = s.getUUID("Network");
                shipment.station = GlobalPos.of(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(s.getString("Dimension"))), BlockPos.of(s.getLong("Pos")));
                shipment.address = s.getString("Address");
                shipment.remaining.addAll(TerminalData.stacks(s.getList("Remaining", Tag.TAG_COMPOUND), registries));
                for (Tag fragment : s.getList("Received", Tag.TAG_STRING)) shipment.received.add(fragment.getAsString());
                order.shipments.add(shipment);
            }
            data.orders.put(order.id, order);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", nextLogisticsId);
        ListTag list = new ListTag();
        for (Order order : orders.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", order.id); t.putUUID("Owner", order.owner); t.putInt("Net", order.netId);
            t.putBoolean("Ended", order.ended);
            t.put("Held", TerminalData.stacks(order.held, registries)); t.put("Packages", TerminalData.stacks(order.packages, registries));
            ListTag shipments = new ListTag();
            for (Shipment shipment : order.shipments) {
                CompoundTag s = new CompoundTag();
                s.putInt("Id", shipment.id); s.putUUID("Network", shipment.network);
                s.putString("Dimension", shipment.station.dimension().location().toString());
                s.putLong("Pos", shipment.station.pos().asLong()); s.putString("Address", shipment.address);
                s.put("Remaining", TerminalData.stacks(shipment.remaining, registries));
                ListTag fragments = new ListTag(); shipment.received.stream().sorted().forEach(f -> fragments.add(StringTag.valueOf(f)));
                s.put("Received", fragments); shipments.add(s);
            }
            t.put("Shipments", shipments); list.add(t);
        }
        tag.put("Orders", list);
        return tag;
    }
}
