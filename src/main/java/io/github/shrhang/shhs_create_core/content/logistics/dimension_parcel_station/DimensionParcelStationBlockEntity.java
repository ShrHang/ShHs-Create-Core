package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.ItemUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import io.github.shrhang.shhs_create_core.ShHsConfig;
import io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DimensionParcelStationBlockEntity extends NetedBlockEntity implements MenuProvider {
    private static final String FLUID_PACKAGER_CLASS =
            "com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity";

    private boolean allowItemInput = true;
    private boolean allowItemOutput = true;
    private boolean allowFluidInput = true;
    private boolean allowFluidOutput = true;

    private DimensionsNet handlerNet;
    private StationItemHandler itemHandler;
    private StationFluidHandler fluidHandler;

    public DimensionParcelStationBlockEntity(BlockPos pos, BlockState state) {
        this(ShHsBlockEntityTypes.DIMENSION_PARCEL_STATION_BE.get(), pos, state);
    }

    public DimensionParcelStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean trySetNetId(int netId) {
        if (!(level instanceof ServerLevel serverLevel) || netId < 0)
            return false;
        GlobalPos pos = GlobalPos.of(serverLevel.dimension(), worldPosition);
        if (!DimensionParcelStationBindingIndex.get(serverLevel.getServer())
                .tryReserve(serverLevel.getServer(), pos, netId))
            return false;
        super.setNetId(netId);
        resetHandlers();
        level.invalidateCapabilities(worldPosition);
        sendBlockUpdated();
        return getNetId() == netId;
    }

    @Override
    public void setNetId(int id) {
        if (id < 0) {
            if (level instanceof ServerLevel serverLevel)
                DimensionParcelStationBindingIndex.get(serverLevel.getServer())
                        .release(GlobalPos.of(serverLevel.dimension(), worldPosition));
            super.setNetId(id);
            resetHandlers();
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
                sendBlockUpdated();
            }
            return;
        }
        trySetNetId(id);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel)
            DimensionParcelStationBindingIndex.get(serverLevel.getServer()).onStationLoaded(this);
    }

    @Nullable
    public IItemHandlerModifiable getItemHandler(Direction side) {
        if (side == null || (!allowItemInput && !allowItemOutput) || getNet() == null)
            return null;
        BlockEntity neighbor = level == null ? null : level.getBlockEntity(worldPosition.relative(side));
        if (!(neighbor instanceof PackagerBlockEntity) || isFluidPackager(neighbor))
            return null;
        ensureHandlers();
        return itemHandler;
    }

    @Nullable
    public IFluidHandler getFluidHandler(Direction side) {
        if (side == null || (!allowFluidInput && !allowFluidOutput) || getNet() == null)
            return null;
        BlockEntity neighbor = level == null ? null : level.getBlockEntity(worldPosition.relative(side));
        if (!isFluidPackager(neighbor))
            return null;
        ensureHandlers();
        return fluidHandler;
    }

    private static boolean isFluidPackager(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && FLUID_PACKAGER_CLASS.equals(blockEntity.getClass().getName());
    }

    public boolean allowsOutputFor(BlockEntity packager) {
        return isFluidPackager(packager) ? allowFluidOutput
                : packager instanceof PackagerBlockEntity && allowItemOutput;
    }

    private void ensureHandlers() {
        DimensionsNet net = getNet();
        if (net == handlerNet)
            return;
        handlerNet = net;
        if (net == null) {
            itemHandler = null;
            fluidHandler = null;
        } else {
            itemHandler = new StationItemHandler(new ItemUnifiedStorageHandler(net.getUnifiedStorage()));
            fluidHandler = new StationFluidHandler(new FluidUnifiedStorageHandler(net.getUnifiedStorage()));
        }
    }

    private void resetHandlers() {
        handlerNet = null;
        itemHandler = null;
        fluidHandler = null;
    }

    public boolean mayConfigure(Player player) {
        DimensionsNet net = getNet();
        return net != null && ShHsConfig.SERVER.dimensionParcelStationConfigurePermission.get().allows(net, player);
    }

    public void toggle(Channel channel, Player player) {
        if (!mayConfigure(player))
            return;
        switch (channel) {
            case ITEM_INPUT -> allowItemInput = !allowItemInput;
            case ITEM_OUTPUT -> allowItemOutput = !allowItemOutput;
            case FLUID_INPUT -> allowFluidInput = !allowFluidInput;
            case FLUID_OUTPUT -> allowFluidOutput = !allowFluidOutput;
        }
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            sendBlockUpdated();
        }
    }

    private void sendBlockUpdated() {
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean isAllowed(Channel channel) {
        return switch (channel) {
            case ITEM_INPUT -> allowItemInput;
            case ITEM_OUTPUT -> allowItemOutput;
            case FLUID_INPUT -> allowFluidInput;
            case FLUID_OUTPUT -> allowFluidOutput;
        };
    }

    public int getStationCount() {
        if (!(level instanceof ServerLevel serverLevel) || getNetId() < 0)
            return 0;
        return DimensionParcelStationBindingIndex.get(serverLevel.getServer()).activeCount(getNetId());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        allowItemInput = !tag.contains("AllowItemInput") || tag.getBoolean("AllowItemInput");
        allowItemOutput = !tag.contains("AllowItemOutput") || tag.getBoolean("AllowItemOutput");
        allowFluidInput = !tag.contains("AllowFluidInput") || tag.getBoolean("AllowFluidInput");
        allowFluidOutput = !tag.contains("AllowFluidOutput") || tag.getBoolean("AllowFluidOutput");
        resetHandlers();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("AllowItemInput", allowItemInput);
        tag.putBoolean("AllowItemOutput", allowItemOutput);
        tag.putBoolean("AllowFluidInput", allowFluidInput);
        tag.putBoolean("AllowFluidOutput", allowFluidOutput);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.shhs_create_core.dimension_parcel_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DimensionParcelStationMenu(containerId, inventory, this);
    }

    public enum Channel {
        ITEM_INPUT,
        ITEM_OUTPUT,
        FLUID_INPUT,
        FLUID_OUTPUT
    }

    private final class StationItemHandler implements IItemHandlerModifiable {
        private final ItemUnifiedStorageHandler delegate;

        private StationItemHandler(ItemUnifiedStorageHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return allowItemOutput ? delegate.getSlots() : allowItemInput ? 1 : 0;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return allowItemOutput ? delegate.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (allowItemInput)
                delegate.setStackInSlot(slot, stack);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return allowItemInput ? delegate.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return allowItemOutput ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return allowItemInput ? delegate.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return allowItemInput && delegate.isItemValid(slot, stack);
        }
    }

    private final class StationFluidHandler implements IFluidHandler {
        private final FluidUnifiedStorageHandler delegate;

        private StationFluidHandler(FluidUnifiedStorageHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getTanks() {
            return allowFluidOutput ? delegate.getTanks() : allowFluidInput ? 1 : 0;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return allowFluidOutput ? delegate.getFluidInTank(tank) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return allowFluidInput ? delegate.getTankCapacity(tank) : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return allowFluidInput && delegate.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return allowFluidInput ? delegate.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return allowFluidOutput ? delegate.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return allowFluidOutput ? delegate.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
