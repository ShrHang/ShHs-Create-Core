package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationRouting;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationRouting.Route;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.fluidlogistics.FluidLogistics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side discovery. Never loads chunks or includes our own dimension storage as an external source. */
public final class TerminalStock {
    public record Entry(ItemStack stack, long amount, UUID network, boolean requestable) {
    }

    public record Snapshot(List<Entry> entries, Map<UUID, String> addresses) {
    }

    private record CacheKey(int dimensionNet, UUID logisticsNet) {
    }

    private record Cached(long tick, InventorySummary summary) {
    }

    private static final Map<CacheKey, Cached> CACHE = new HashMap<>();

    private TerminalStock() {
    }

    static void clearCache() {
        CACHE.clear();
    }

    private static InventorySummary externalSummary(int netId, Route route, long tick) {
        CacheKey key = new CacheKey(netId, route.network());
        Cached cached = CACHE.get(key);
        if (cached != null && tick - cached.tick() < 20)
            return cached.summary();
        InventorySummary result = new InventorySummary();
        Set<InventoryIdentifier> seen = new HashSet<>();
        for (var link : LogisticallyLinkedBehaviour.getAllPresent(route.network(), false)) {
            if (!(link.blockEntity instanceof PackagerLinkBlockEntity block)) continue;
            PackagerBlockEntity packager = block.getPackager();
            if (packager == null || packager.targetInventory == null
                    || packager.isTargetingSameInventory(route.excludedInventory()))
                continue;
            IdentifiedInventory inventory = packager.targetInventory.getIdentifiedInventory();
            if (inventory != null && inventory.identifier() != null && !seen.add(inventory.identifier()))
                continue;
            result.add(link.getSummary(route.excludedInventory()));
        }
        CACHE.put(key, new Cached(tick, result));
        return result;
    }

    static Snapshot snapshot(ServerPlayer player, DimensionsNet net) {
        if (net == null)
            return new Snapshot(List.of(), Map.of());
        Map<UUID, Route> routes = DimensionParcelStationRouting.routes(player, net.getId());
        List<Entry> result = new ArrayList<>();
        for (var value : net.getUnifiedStorage().getStorage()) {
            if (value.key() instanceof ItemStackKey key && value.amount() > 0) {
                result.add(new Entry(key.copyStackWithCount(1), value.amount(), null, true));
            } else if (value.key() instanceof FluidStackKey key && value.amount() > 0) {
                ItemStack display = Mods.FLUIDLOGISTICS.runIfInstalled(
                        () -> () -> FluidLogistics.fluidDisplayKey(key)).orElse(ItemStack.EMPTY);
                if (!display.isEmpty()) result.add(new Entry(display, value.amount(), null, false));
            }
        }
        for (Route route : routes.values()) {
            for (var value : externalSummary(net.getId(), route,
                    player.server.overworld().getGameTime()).getStacks()) {
                if (value.count > 0)
                    result.add(new Entry(value.stack.copyWithCount(1), value.count, route.network(), !route.address().isBlank()));
            }
        }
        result.sort(Comparator.comparing((Entry e) -> e.network() != null)
                .thenComparing(e -> e.stack().getDescriptionId()).thenComparing(e -> String.valueOf(e.network())));
        Map<UUID, String> addresses = new LinkedHashMap<>();
        routes.values().forEach(route -> addresses.put(route.network(), route.address()));
        return new Snapshot(List.copyOf(result), Map.copyOf(addresses));
    }
}
