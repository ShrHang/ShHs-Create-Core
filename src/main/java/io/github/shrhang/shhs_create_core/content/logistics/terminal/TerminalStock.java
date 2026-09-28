package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.simibubi.create.Create;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Server-side discovery. Never loads chunks or includes our own dimension storage as an external source. */
public final class TerminalStock {
    public record Entry(ItemStack stack, long amount, UUID network, boolean requestable) {}
    public record Route(UUID network, GlobalPos station, String address, IdentifiedInventory ignored) {}

    private record CacheKey(int dimensionNet, UUID logisticsNet) {}
    private record Cached(long tick, InventorySummary summary) {}
    private static final Map<CacheKey, Cached> CACHE = new HashMap<>();

    private TerminalStock() {}

    public static void clearCache() { CACHE.clear(); }

    public static Set<UUID> connectedNetworks(DimensionParcelStationBlockEntity station) {
        Set<UUID> result = new TreeSet<>();
        var level = station.getLevel();
        if (level == null) return result;
        for (Direction direction : Direction.values()) {
            BlockPos pos = station.getBlockPos().relative(direction);
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager)
                    || packager.getClass() != PackagerBlockEntity.class || packager.targetInventory == null
                    || !packager.targetInventory.getTarget().getConnectedPos().equals(station.getBlockPos())) continue;
            for (Direction side : Direction.values()) {
                BlockPos linkPos = pos.relative(side);
                if (level.isLoaded(linkPos) && level.getBlockEntity(linkPos) instanceof PackagerLinkBlockEntity link
                        && link.getPackager() == packager && link.behaviour != null)
                    result.add(link.behaviour.freqId);
            }
        }
        return result;
    }

    public static Map<UUID, Route> routes(ServerPlayer player, int netId) {
        Map<UUID, Route> routes = new LinkedHashMap<>();
        for (GlobalPos pos : DimensionParcelStationBindingIndex.get(player.server).stations(netId)) {
            var level = player.server.getLevel(pos.dimension());
            if (level == null || !level.isLoaded(pos.pos())
                    || !(level.getBlockEntity(pos.pos()) instanceof DimensionParcelStationBlockEntity station)
                    || station.getNetId() != netId) continue;
            for (UUID network : connectedNetworks(station)) {
                if (!Create.LOGISTICS.logisticsNetworks.containsKey(network)
                        || !Create.LOGISTICS.mayInteract(network, player)) continue;
                String address = station.isAllowed(DimensionParcelStationBlockEntity.Channel.ITEM_INPUT)
                        ? station.getReceiveAddress(network) : "";
                var ignored = new IdentifiedInventory(station.getVirtualInventoryIdentifier(), null);
                Route route = new Route(network, pos, address, ignored);
                Route old = routes.get(network);
                if (old == null || old.address().isBlank() && !address.isBlank()) routes.put(network, route);
            }
        }
        return routes;
    }

    public static InventorySummary externalSummary(int netId, Route route, long tick, boolean fresh) {
        CacheKey key = new CacheKey(netId, route.network());
        Cached cached = CACHE.get(key);
        if (!fresh && cached != null && tick - cached.tick() < 20) return cached.summary();
        InventorySummary result = new InventorySummary();
        Set<InventoryIdentifier> seen = new HashSet<>();
        for (var link : LogisticallyLinkedBehaviour.getAllPresent(route.network(), false)) {
            if (!(link.blockEntity instanceof PackagerLinkBlockEntity block)) continue;
            PackagerBlockEntity packager = block.getPackager();
            if (packager == null || packager.targetInventory == null
                    || packager.isTargetingSameInventory(route.ignored())) continue;
            IdentifiedInventory inventory = packager.targetInventory.getIdentifiedInventory();
            if (inventory != null && inventory.identifier() != null && !seen.add(inventory.identifier())) continue;
            result.add(link.getSummary(route.ignored()));
        }
        CACHE.put(key, new Cached(tick, result));
        return result;
    }

    public static List<Entry> snapshot(ServerPlayer player, DimensionsNet net) {
        if (net == null) return List.of();
        List<Entry> result = new ArrayList<>();
        Set<ItemStackKey> local = new HashSet<>();
        for (var value : net.getUnifiedStorage().getStorage()) {
            if (value.key() instanceof ItemStackKey key && value.amount() > 0) {
                local.add(key);
                result.add(new Entry(key.copyStackWithCount(1), value.amount(), null, true));
            }
        }
        for (Route route : routes(player, net.getId()).values()) {
            for (var value : externalSummary(net.getId(), route, player.server.overworld().getGameTime(), false).getStacks()) {
                if (value.count > 0 && !local.contains(new ItemStackKey(value.stack)))
                    result.add(new Entry(value.stack.copyWithCount(1), value.count, route.network(), !route.address().isBlank()));
            }
        }
        result.sort(Comparator.comparing((Entry e) -> e.network() != null)
                .thenComparing(e -> e.stack().getDescriptionId()).thenComparing(e -> String.valueOf(e.network())));
        return result;
    }
}
