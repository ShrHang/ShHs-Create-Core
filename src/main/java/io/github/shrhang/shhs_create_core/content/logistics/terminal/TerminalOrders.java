package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Real items live here after submission, never in a Screen or a menu instance. */
public final class TerminalOrders extends SavedData {
    private static final Factory<TerminalOrders> FACTORY = new Factory<>(TerminalOrders::new, TerminalOrders::load);
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private int nextLogisticsId = Integer.MIN_VALUE;

    public static TerminalOrders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "ShHsTerminalOrders");
    }

    private static final class Order {
        final UUID id;
        final UUID owner;
        final int netId;
        boolean ended;
        final List<ItemStack> held = new ArrayList<>();
        final List<ItemStack> packages = new ArrayList<>();
        private final List<Shipment> shipments = new ArrayList<>();

        Order(UUID id, UUID owner, int netId) {
            this.id = id;
            this.owner = owner;
            this.netId = netId;
        }

        boolean ready() {
            return ended || shipments.stream().allMatch(shipment -> shipment.remaining.isEmpty());
        }

        boolean visible() {
            return !ready() || !held.isEmpty() || !packages.isEmpty();
        }
    }

    private static final class Shipment {
        int id;
        UUID network;
        GlobalPos station;
        String address;
        final List<ItemStack> requested = new ArrayList<>();
        final List<ItemStack> remaining = new ArrayList<>();
        final Set<String> received = new HashSet<>();
    }

    private int allocateId() {
        Set<Integer> used = new HashSet<>();
        orders.values().forEach(order -> order.shipments.forEach(shipment -> used.add(shipment.id)));
        while (nextLogisticsId == -1 || used.contains(nextLogisticsId))
            nextLogisticsId++;
        return nextLogisticsId++;
    }

    private List<Order> forPlayer(UUID player) {
        return orders.values().stream().filter(o -> o.owner.equals(player) && o.visible()).toList();
    }

    public boolean ownsLogisticsId(int id) {
        return orders.values().stream().anyMatch(order -> order.shipments.stream().anyMatch(shipment -> shipment.id == id));
    }

    public boolean submit(ServerPlayer player, DimensionsNet net, UUID submission, List<TerminalStock.Entry> entries) {
        if (orders.containsKey(submission))
            return orders.get(submission).owner.equals(player.getUUID());
        if (entries.isEmpty() || entries.size() > TerminalData.MAX_ORDER_LINES
                || forPlayer(player.getUUID()).size() >= TerminalData.MAX_ACTIVE_ORDERS)
            return false;
        Map<TerminalData.Selection, Long> demand = new LinkedHashMap<>();
        long total = 0;
        for (var entry : entries) {
            if (entry.network() == null || entry.stack().isEmpty()
                    || entry.amount() <= 0 || entry.amount() > TerminalData.MAX_ITEMS)
                return false;
            total += entry.amount();
            if (total > TerminalData.MAX_ITEMS)
                return false;
            demand.merge(new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network()), entry.amount(), Long::sum);
        }
        var routes = TerminalStock.routes(player, net.getId());
        Map<UUID, List<BigItemStack>> external = new LinkedHashMap<>();
        for (var entry : demand.entrySet()) {
            var selection = entry.getKey();
            var route = routes.get(selection.network());
            if (route == null || route.address().isBlank())
                return false;
            external.computeIfAbsent(selection.network(), ignored -> new ArrayList<>())
                    .add(new BigItemStack(selection.key().copyStackWithCount(1), entry.getValue().intValue()));
        }
        Order order = new Order(submission, player.getUUID(), net.getId());
        Multimap<PackagerBlockEntity, PackagingRequest> planned = ArrayListMultimap.create();
        // All networks are planned before any inventory is changed.
        Map<Object, Map<ItemStackKey, Long>> plannedAmounts = new HashMap<>();
        for (var request : external.entrySet()) {
            var route = routes.get(request.getKey());
            var packages = LogisticsManager.findPackagersForRequest(route.network(),
                    PackageOrderWithCrafts.simple(request.getValue()), route.excludedInventory(), route.address());
            Map<ItemStackKey, Long> totals = new HashMap<>();
            for (var entry : packages.entries()) {
                PackagerBlockEntity packager = entry.getKey();
                if (packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.PLAYER))
                    return false;
                var key = new ItemStackKey(entry.getValue().item());
                long amount = entry.getValue().getCount();
                totals.merge(key, amount, Long::sum);
                var inventory = packager.targetInventory.getIdentifiedInventory();
                Object identity = inventory != null && inventory.identifier() != null ? inventory.identifier() : packager;
                long reserved = plannedAmounts.computeIfAbsent(identity, ignored -> new HashMap<>()).merge(key, amount, Long::sum);
                if (reserved > packager.getAvailableItems().getCountOf(key.copyStackWithCount(1)))
                    return false;
            }
            for (var stack : request.getValue())
                if (totals.getOrDefault(new ItemStackKey(stack.stack), 0L) != stack.count)
                    return false;
            Shipment shipment = new Shipment();
            shipment.id = allocateId();
            shipment.network = route.network();
            shipment.station = route.station();
            shipment.address = route.address();
            for (var stack : request.getValue()) {
                TerminalData.append(shipment.requested, stack.stack, stack.count);
                TerminalData.append(shipment.remaining, stack.stack, stack.count);
            }
            order.shipments.add(shipment);
            for (var entry : packages.entries()) {
                PackagingRequest r = entry.getValue();
                planned.put(entry.getKey(), new PackagingRequest(r.item(), r.count(), r.address(), r.linkIndex(),
                        r.finalLink(), r.packageCounter(), shipment.id, r.context()));
            }
        }
        orders.put(order.id, order);
        setDirty();
        // Create's convenience method stops after 100 boxes. Drain this bounded request explicitly instead.
        for (var batch : planned.asMap().entrySet()) {
            PackagerBlockEntity packager = batch.getKey();
            List<PackagingRequest> queue = new ArrayList<>(batch.getValue());
            packager.flashLink();
            for (int attempt = 0; !queue.isEmpty() && attempt < TerminalData.MAX_ITEMS; attempt++) {
                long before = queue.stream().mapToLong(PackagingRequest::getCount).sum();
                packager.attemptToSend(queue);
                if (queue.stream().mapToLong(PackagingRequest::getCount).sum() >= before)
                    break;
            }
            packager.triggerStockCheck();
            packager.notifyUpdate();
        }
        TerminalStock.clearCache();
        return true;
    }

    /** Null means an ordinary package. False leaves a recognized package intact. */
    public @Nullable Boolean receive(ServerLevel level, BlockPos target, ItemStack box, boolean simulate) {
        if (!PackageItem.hasOrderData(box))
            return null;
        int id = PackageItem.getOrderId(box);
        for (Order order : orders.values()) for (Shipment shipment : order.shipments) {
            if (shipment.id != id || !shipment.station.equals(GlobalPos.of(level.dimension(), target))
                    || !shipment.address.equals(PackageItem.getAddress(box)))
                continue;
            if (!(level.getBlockEntity(target) instanceof DimensionParcelStationBlockEntity station)
                    || !station.isAllowed(DimensionParcelStationBlockEntity.Channel.ITEM_INPUT))
                return false;
            String fragment = PackageItem.getLinkIndex(box) + ":" + PackageItem.getIndex(box);
            if (shipment.received.contains(fragment))
                return false;
            List<ItemStack> contents = new ArrayList<>();
            var handler = PackageItem.getContents(box);
            for (int i = 0; i < handler.getSlots(); i++)
                if (!handler.getStackInSlot(i).isEmpty())
                    contents.add(handler.getStackInSlot(i).copy());
            List<ItemStack> remaining = shipment.remaining.stream().map(ItemStack::copy)
                    .collect(Collectors.toCollection(ArrayList::new));
            for (ItemStack stack : contents) {
                int need = stack.getCount();
                for (ItemStack expected : remaining) {
                    if (!ItemStack.isSameItemSameComponents(stack, expected))
                        continue;
                    int used = Math.min(need, expected.getCount());
                    expected.shrink(used);
                    need -= used;
                }
                if (need != 0)
                    return false;
            }
            if (simulate)
                return true;
            shipment.remaining.clear();
            remaining.stream().filter(s -> !s.isEmpty()).forEach(shipment.remaining::add);
            shipment.received.add(fragment);
            for (ItemStack stack : contents) {
                long held = stack.getCount();
                if (order.ended) {
                    DimensionsNet original = DimensionsNet.getNetFromId(order.netId);
                    if (original != null)
                        held = original.getUnifiedStorage().insert(new ItemStackKey(stack), held, false).amount();
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
        if (order == null || !order.owner.equals(player.getUUID()))
            return;
        if (endWaiting)
            order.ended = true;
        if (!order.ready())
            return;
        pack(order);
        Iterator<ItemStack> iterator = order.packages.iterator();
        while (iterator.hasNext()) {
            ItemStack box = iterator.next();
            player.getInventory().add(box);
            if (box.isEmpty())
                iterator.remove();
        }
        player.containerMenu.broadcastChanges();
        setDirty();
    }

    public void claimReady(ServerPlayer player) {
        forPlayer(player.getUUID()).stream().filter(Order::ready).map(order -> order.id).toList()
                .forEach(id -> claim(player, id, false));
    }

    public void endIncomplete(ServerPlayer player) {
        forPlayer(player.getUUID()).stream().filter(order -> !order.ready()).map(order -> order.id).toList()
                .forEach(id -> claim(player, id, true));
    }

    private static void pack(Order order) {
        order.held.removeIf(ItemStack::isEmpty);
        while (!order.held.isEmpty())
            order.packages.add(PackageItem.containing(takePackageContents(order.held)));
    }

    private static ItemStackHandler takePackageContents(List<ItemStack> pending) {
        ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
        int slot = 0;
        while (slot < PackageItem.SLOTS && !pending.isEmpty()) {
            ItemStack stack = pending.getFirst();
            boolean bulky = !stack.canFitInsideContainerItems();
            if (bulky && slot != 0)
                break;
            int count = Math.min(stack.getCount(), stack.getMaxStackSize());
            contents.setStackInSlot(slot++, stack.split(count));
            if (stack.isEmpty())
                pending.removeFirst();
            if (bulky)
                break;
        }
        return contents;
    }

    public List<TerminalOrderSummary> summaries(ServerPlayer player) {
        List<TerminalOrderSummary> result = new ArrayList<>();
        for (Order order : forPlayer(player.getUUID())) {
            Map<TerminalData.Selection, Long> requested = new LinkedHashMap<>();
            Map<TerminalData.Selection, Long> remaining = new HashMap<>();
            for (Shipment shipment : order.shipments) {
                shipment.requested.forEach(stack -> requested.merge(
                        new TerminalData.Selection(new ItemStackKey(stack), shipment.network),
                        (long) stack.getCount(), Long::sum));
                shipment.remaining.forEach(stack -> remaining.merge(
                        new TerminalData.Selection(new ItemStackKey(stack), shipment.network),
                        (long) stack.getCount(), Long::sum));
            }
            List<TerminalOrderSummary.Line> lines = requested.entrySet().stream()
                    .map(entry -> new TerminalOrderSummary.Line(entry.getKey().key().copyStackWithCount(1),
                            entry.getKey().network(), entry.getValue(),
                            remaining.getOrDefault(entry.getKey(), 0L)))
                    .toList();
            result.add(new TerminalOrderSummary(order.id, order.ready(), estimatedPackages(order), lines));
        }
        return List.copyOf(result);
    }

    private static int estimatedPackages(Order order) {
        List<ItemStack> pending = order.held.stream().map(ItemStack::copy)
                .collect(Collectors.toCollection(ArrayList::new));
        int packages = order.packages.size();
        while (!pending.isEmpty()) {
            packages++;
            takePackageContents(pending);
        }
        return packages;
    }

    private static TerminalOrders load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalOrders data = new TerminalOrders();
        data.nextLogisticsId = tag.contains("NextId") ? tag.getInt("NextId") : Integer.MIN_VALUE;
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id") || !t.hasUUID("Owner"))
                continue;
            Order order = new Order(t.getUUID("Id"), t.getUUID("Owner"), t.getInt("Net"));
            order.ended = t.getBoolean("Ended");
            order.held.addAll(TerminalData.stacks(t.getList("Held", Tag.TAG_COMPOUND), registries));
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
