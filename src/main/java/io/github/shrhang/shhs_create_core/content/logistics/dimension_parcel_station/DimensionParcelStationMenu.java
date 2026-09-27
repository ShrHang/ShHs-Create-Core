package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import io.github.shrhang.shhs_create_core.content.registries.ShHsMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class DimensionParcelStationMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerData data;

    public DimensionParcelStationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), new SimpleContainerData(4));
    }

    public DimensionParcelStationMenu(int containerId, Inventory inventory,
                                      DimensionParcelStationBlockEntity station) {
        this(containerId, inventory, station.getBlockPos(), serverData(station, inventory.player));
    }

    private DimensionParcelStationMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(ShHsMenuTypes.DIMENSION_PARCEL_STATION.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    private static ContainerData serverData(DimensionParcelStationBlockEntity station, Player player) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> flags(station);
                    case 1 -> station.mayConfigure(player) ? 1 : 0;
                    case 2 -> station.getNetId();
                    case 3 -> station.getStationCount();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
    }

    private static int flags(DimensionParcelStationBlockEntity station) {
        int flags = 0;
        for (DimensionParcelStationBlockEntity.Channel channel : DimensionParcelStationBlockEntity.Channel.values())
            if (station.isAllowed(channel))
                flags |= 1 << channel.ordinal();
        return flags;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isAllowed(DimensionParcelStationBlockEntity.Channel channel) {
        return (data.get(0) & 1 << channel.ordinal()) != 0;
    }

    public boolean mayConfigure() {
        return data.get(1) != 0;
    }

    public int getNetId() {
        return data.get(2);
    }

    public int getStationCount() {
        return data.get(3);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D)
            return false;
        return player.level().getBlockEntity(pos) instanceof DimensionParcelStationBlockEntity;
    }
}
