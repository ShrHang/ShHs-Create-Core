package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.fluidlogistics.FluidLogistics;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBindingIndex;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Finds loaded station routes without loading chunks. */
public final class DimensionParcelStationRouting {
    private DimensionParcelStationRouting() {}

    public record Route(UUID network, GlobalPos station, String address, IdentifiedInventory excludedInventory) {
    }

    public static Set<UUID> connectedNetworks(DimensionParcelStationBlockEntity station) {
        Set<UUID> result = new TreeSet<>();
        var level = station.getLevel();
        if (level == null)
            return result;
        for (Direction direction : Direction.values()) {
            BlockPos pos = station.getBlockPos().relative(direction);
            if (!level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager))
                continue;
            boolean supported = packager.getClass() == PackagerBlockEntity.class
                    || Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                    FluidLogistics.isFluidPackager(packager)).orElse(false);
            if (!supported || packager.targetInventory == null
                    || !packager.targetInventory.getTarget().getConnectedPos().equals(station.getBlockPos()))
                continue;
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
                    || station.getNetId() != netId)
                continue;
            for (UUID network : connectedNetworks(station)) {
                if (!Create.LOGISTICS.logisticsNetworks.containsKey(network)
                        || !Create.LOGISTICS.mayInteract(network, player))
                    continue;
                String address = station.isAllowed(DimensionParcelStationBlockEntity.Channel.ITEM_INPUT)
                        ? station.getReceiveAddress(network) : "";
                var ignored = new IdentifiedInventory(station.getVirtualInventoryIdentifier(), null);
                Route route = new Route(network, pos, address, ignored);
                Route old = routes.get(network);
                // stations() is placement-ordered; only skip ahead when the older route cannot receive items.
                if (old == null || old.address().isBlank() && !address.isBlank())
                    routes.put(network, route);
            }
        }
        return routes;
    }

}
