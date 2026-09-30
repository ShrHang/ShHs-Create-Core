package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.ItemUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.fluidlogistics.FluidLogistics;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity.Channel.ITEM_INPUT;
import static io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity.Channel.ITEM_OUTPUT;
import static io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity.Channel.FLUID_INPUT;
import static io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity.Channel.FLUID_OUTPUT;

/** Capability policy and cached storage adapters for one station. */
final class DimensionParcelStationAccess {
    private final DimensionParcelStationBlockEntity station;

    DimensionParcelStationAccess(DimensionParcelStationBlockEntity station) {
        this.station = station;
    }

    private DimensionsNet handlerNet;
    private StationItemHandler itemHandler;
    private StationItemHandler repackagerItemHandler;
    private StationFluidHandler fluidHandler;

    @Nullable
    IItemHandlerModifiable getItemHandler(Direction side) {
        if (side == null)
            return null;
        DimensionsNet net = station.getNet();
        if (net == null)
            return null;
        BlockEntity neighbor = adjacentBlockEntity(side);
        if (isFluidRepackager(neighbor)) {
            if (!canAcceptMixedPackages() && !station.isAllowed(ITEM_OUTPUT))
                return null;
            ensureHandlers(net);
            return repackagerItemHandler;
        }
        if ((!station.isAllowed(ITEM_INPUT) && !station.isAllowed(ITEM_OUTPUT)) || !(neighbor instanceof PackagerBlockEntity)
                || isFluidPackager(neighbor))
            return null;
        ensureHandlers(net);
        return itemHandler;
    }

    @Nullable
    IFluidHandler getFluidHandler(Direction side) {
        if (side == null)
            return null;
        DimensionsNet net = station.getNet();
        if (net == null)
            return null;
        BlockEntity neighbor = adjacentBlockEntity(side);
        if (isFluidRepackager(neighbor)) {
            if (!canAcceptMixedPackages())
                return null;
            ensureHandlers(net);
            return fluidHandler;
        }
        if ((!station.isAllowed(FLUID_INPUT) && !station.isAllowed(FLUID_OUTPUT)) || !isFluidPackager(neighbor))
            return null;
        ensureHandlers(net);
        return fluidHandler;
    }

    @Nullable
    private BlockEntity adjacentBlockEntity(Direction side) {
        return station.getLevel() == null ? null : station.getLevel().getBlockEntity(station.getBlockPos().relative(side));
    }

    boolean isItemPackagerPlacementTarget() {
        return station.getNetId() >= 0 && (station.isAllowed(ITEM_INPUT) || station.isAllowed(ITEM_OUTPUT));
    }

    boolean isFluidPackagerPlacementTarget() {
        return station.getNetId() >= 0 && (station.isAllowed(FLUID_INPUT) || station.isAllowed(FLUID_OUTPUT));
    }

    private static boolean isFluidPackager(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogistics.isFluidPackager(blockEntity)).orElse(false);
    }

    private static boolean isFluidRepackager(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogistics.isFluidRepackager(blockEntity)).orElse(false);
    }

    private boolean canAcceptMixedPackages() {
        return station.isAllowed(ITEM_INPUT) && station.isAllowed(FLUID_INPUT);
    }

    boolean allowsWarehouseOutput(BlockEntity packager) {
        if (isFluidRepackager(packager))
            return false;
        return isFluidPackager(packager) ? station.isAllowed(FLUID_OUTPUT)
                : packager instanceof PackagerBlockEntity && station.isAllowed(ITEM_OUTPUT);
    }

    private void ensureHandlers(DimensionsNet net) {
        if (net == handlerNet)
            return;
        handlerNet = net;
        ItemUnifiedStorageHandler itemDelegate = new ItemUnifiedStorageHandler(net.getUnifiedStorage());
        itemHandler = new StationItemHandler(itemDelegate, false);
        repackagerItemHandler = new StationItemHandler(itemDelegate, true);
        fluidHandler = new StationFluidHandler(new FluidUnifiedStorageHandler(net.getUnifiedStorage()));
    }

    void resetHandlers() {
        handlerNet = null;
        itemHandler = null;
        repackagerItemHandler = null;
        fluidHandler = null;
    }

    private final class StationItemHandler implements IItemHandlerModifiable {
        private final ItemUnifiedStorageHandler delegate;
        private final boolean mixedPackagesOnly;

        private StationItemHandler(ItemUnifiedStorageHandler delegate, boolean mixedPackagesOnly) {
            this.delegate = delegate;
            this.mixedPackagesOnly = mixedPackagesOnly;
        }

        private boolean allowsInput() {
            return station.isAllowed(ITEM_INPUT) && (!mixedPackagesOnly || station.isAllowed(FLUID_INPUT));
        }

        @Override
        public int getSlots() {
            return station.isAllowed(ITEM_OUTPUT) ? delegate.getSlots() : allowsInput() ? 1 : 0;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return station.isAllowed(ITEM_OUTPUT) ? delegate.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (allowsInput())
                delegate.setStackInSlot(slot, stack);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return allowsInput() ? delegate.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return station.isAllowed(ITEM_OUTPUT) ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return allowsInput() ? delegate.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return allowsInput() && delegate.isItemValid(slot, stack);
        }
    }

    private final class StationFluidHandler implements IFluidHandler {
        private final FluidUnifiedStorageHandler delegate;

        private StationFluidHandler(FluidUnifiedStorageHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getTanks() {
            return station.isAllowed(FLUID_OUTPUT) ? delegate.getTanks() : station.isAllowed(FLUID_INPUT) ? 1 : 0;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return station.isAllowed(FLUID_OUTPUT) ? delegate.getFluidInTank(tank) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return station.isAllowed(FLUID_INPUT) ? delegate.getTankCapacity(tank) : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return station.isAllowed(FLUID_INPUT) && delegate.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return station.isAllowed(FLUID_INPUT) ? delegate.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return station.isAllowed(FLUID_OUTPUT) ? delegate.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return station.isAllowed(FLUID_OUTPUT) ? delegate.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
