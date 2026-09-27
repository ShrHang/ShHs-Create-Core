package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;

public enum DimensionParcelStationPermission {
    OWNER,
    MANAGER,
    MEMBER;

    public boolean allows(DimensionsNet net, Player player) {
        return switch (this) {
            case OWNER -> net.isOwner(player);
            case MANAGER -> net.isManager(player);
            case MEMBER -> net.getPlayers().contains(player.getUUID());
        };
    }
}
