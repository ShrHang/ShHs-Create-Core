package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalOrderStore.Order;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalOrderStore.Shipment;
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
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.fluidlogistics.terminal.FluidLogisticsTerminalOrderCompat;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationRouting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Order submission, delivery and collection over persistent server state. */
public final class TerminalOrders {
    private final TerminalOrderStore store;

    TerminalOrders(TerminalOrderStore store) {
        this.store = store;
    }

    public static TerminalOrders get(MinecraftServer server) {
        return TerminalOrderStore.get(server).service;
    }

    public boolean ownsLogisticsId(int id) {
        return store.orders.values().stream().anyMatch(order -> order.shipments.stream().anyMatch(shipment -> shipment.id == id));
    }

    public boolean submit(ServerPlayer player, DimensionsNet net, UUID submission, List<TerminalStock.Entry> entries) {
        if (store.orders.containsKey(submission))
            return store.orders.get(submission).owner.equals(player.getUUID());
        if (entries.isEmpty() || entries.size() > TerminalData.MAX_ORDER_LINES
                || store.forPlayer(player.getUUID()).size() >= TerminalData.MAX_ACTIVE_ORDERS)
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
        var routes = DimensionParcelStationRouting.routes(player, net.getId());
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
            shipment.id = store.allocateId();
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
        store.orders.put(order.id, order);
        store.setDirty();
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
        for (Order order : store.orders.values()) for (Shipment shipment : order.shipments) {
            if (shipment.id != id || !shipment.address.equals(PackageItem.getAddress(box)))
                continue;
            if (!(level.getBlockEntity(target) instanceof NetedBlockEntity targetBlock)
                    || targetBlock.getNetId() != order.netId || targetBlock.getNet() == null)
                return false;
            List<TerminalData.PackageEntry> contents = packageContents(box);
            if (contents.isEmpty())
                return false;
            if (targetBlock instanceof DimensionParcelStationBlockEntity station) {
                boolean hasItems = contents.stream().anyMatch(entry -> !entry.isResource());
                boolean hasResources = contents.stream().anyMatch(TerminalData.PackageEntry::isResource);
                if (hasItems && !station.isAllowed(DimensionParcelStationBlockEntity.Channel.ITEM_INPUT)
                        || hasResources && !station.isAllowed(DimensionParcelStationBlockEntity.Channel.FLUID_INPUT))
                    return false;
            }
            String fragment = PackageItem.getLinkIndex(box) + ":" + PackageItem.getIndex(box);
            if (shipment.received.contains(fragment))
                return false;
            List<ItemStack> remaining = shipment.remaining.stream().map(ItemStack::copy)
                    .collect(Collectors.toCollection(ArrayList::new));
            for (TerminalData.PackageEntry entry : contents) {
                int need = entry.amount();
                for (ItemStack expected : remaining) {
                    if (!matches(entry, expected))
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
            for (TerminalData.PackageEntry entry : contents) {
                long held = entry.amount();
                if (order.ended) {
                    DimensionsNet original = DimensionsNet.getNetFromId(order.netId);
                    if (original != null)
                        held = insert(original, entry);
                }
                if (entry.isResource())
                    appendResource(order.heldResources, entry, held);
                else
                    TerminalData.append(order.held, entry.key(), held);
            }
            store.setDirty();
            return true;
        }
        return null;
    }

    private static List<TerminalData.PackageEntry> packageContents(ItemStack box) {
        var resources = Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogisticsTerminalOrderCompat.inspect(box));
        if (resources.isPresent())
            return resources.get();
        List<TerminalData.PackageEntry> contents = new ArrayList<>();
        var handler = PackageItem.getContents(box);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty())
                contents.add(new TerminalData.PackageEntry(stack, stack.getCount(), null));
        }
        return List.copyOf(contents);
    }

    private static boolean matches(TerminalData.PackageEntry entry, ItemStack expected) {
        if (!entry.isResource())
            return ItemStack.isSameItemSameComponents(entry.key(), expected);
        return Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogisticsTerminalOrderCompat.matches(entry, expected)).orElse(false);
    }

    private static long insert(DimensionsNet net, TerminalData.PackageEntry entry) {
        if (!entry.isResource())
            return net.getUnifiedStorage().insert(new ItemStackKey(entry.key()), entry.amount(), false).amount();
        return Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogisticsTerminalOrderCompat.insert(net, entry)).orElse((long) entry.amount());
    }

    private static void appendResource(List<TerminalData.PackageEntry> resources,
                                       TerminalData.PackageEntry entry, long amount) {
        if (amount <= 0)
            return;
        int added = Math.toIntExact(amount);
        for (int i = 0; i < resources.size(); i++) {
            TerminalData.PackageEntry existing = resources.get(i);
            if (!existing.resourceType().equals(entry.resourceType())
                    || !ItemStack.isSameItemSameComponents(existing.key(), entry.key()))
                continue;
            resources.set(i, new TerminalData.PackageEntry(existing.key(),
                    Math.addExact(existing.amount(), added), existing.resourceType()));
            return;
        }
        resources.add(new TerminalData.PackageEntry(entry.key(), added, entry.resourceType()));
    }

    public void claim(ServerPlayer player, UUID id, boolean endWaiting) {
        Order order = store.orders.get(id);
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
        store.setDirty();
    }

    public void claimReady(ServerPlayer player) {
        store.forPlayer(player.getUUID()).stream().filter(Order::ready).map(order -> order.id).toList()
                .forEach(id -> claim(player, id, false));
    }

    public void endIncomplete(ServerPlayer player) {
        store.forPlayer(player.getUUID()).stream().filter(order -> !order.ready()).map(order -> order.id).toList()
                .forEach(id -> claim(player, id, true));
    }

    private static void pack(Order order) {
        order.held.removeIf(ItemStack::isEmpty);
        while (!order.held.isEmpty())
            order.packages.add(PackageItem.containing(takePackageContents(order.held)));
        Iterator<TerminalData.PackageEntry> resources = order.heldResources.iterator();
        while (resources.hasNext()) {
            TerminalData.PackageEntry resource = resources.next();
            List<ItemStack> packages = Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                    FluidLogisticsTerminalOrderCompat.createPackages(resource)).orElse(List.of());
            if (packages.isEmpty())
                continue;
            order.packages.addAll(packages);
            resources.remove();
        }
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

    public List<TerminalData.OrderSummary> summaries(ServerPlayer player) {
        List<TerminalData.OrderSummary> result = new ArrayList<>();
        for (Order order : store.forPlayer(player.getUUID())) {
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
            List<TerminalData.OrderSummary.Line> lines = requested.entrySet().stream()
                    .map(entry -> new TerminalData.OrderSummary.Line(entry.getKey().key().copyStackWithCount(1),
                            entry.getKey().network(), entry.getValue(),
                            remaining.getOrDefault(entry.getKey(), 0L)))
                    .toList();
            result.add(new TerminalData.OrderSummary(order.id, order.ready(), estimatedPackages(order), lines));
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
        for (TerminalData.PackageEntry resource : order.heldResources)
            packages += Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                    FluidLogisticsTerminalOrderCompat.createPackages(resource).size()).orElse(0);
        return packages;
    }

}
