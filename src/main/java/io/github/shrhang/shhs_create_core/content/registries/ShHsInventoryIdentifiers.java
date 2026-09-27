package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlockEntity;
import com.simibubi.create.api.packager.InventoryIdentifier;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationInventoryIdentifier;

public class ShHsInventoryIdentifiers {
    public static void register() {
        InventoryIdentifier.REGISTRY.register(ShHsBlocks.BRASS_ENDER_CHEST.get(), (level, state, face) ->
                level.getBlockEntity(face.getPos()) instanceof BrassEnderChestBlockEntity be ? be.getInvId() : null);
        InventoryIdentifier.REGISTRY.register(ShHsBlocks.DIMENSION_PARCEL_STATION.get(), (level, state, face) ->
                level.getBlockEntity(face.getPos()) instanceof DimensionParcelStationBlockEntity station
                        && station.getNetId() >= 0
                        ? new DimensionParcelStationInventoryIdentifier(station.getNetId())
                        : null);
    }
}
