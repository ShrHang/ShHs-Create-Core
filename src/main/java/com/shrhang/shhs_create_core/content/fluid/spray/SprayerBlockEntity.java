package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SprayerBlockEntity extends KineticBlockEntity implements IFluidHandler {

    private static final int TANK_CAPACITY = 5;
    private static final int MAX_CONSUMPTION = 4;
    private static final float ANGLE_SPEED_SCALE = 0.3f;

    private FluidTank tank;
    private float angle = 180f;

    private boolean hasFrontPipe = false;
    private boolean hasBackPipe = false;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        behaviours.add(new SprayBehaviour(this, tank, () -> {
            float factor = Mth.clamp(angle / 180f, 0f, 1f);
            return (int) (MAX_CONSUMPTION * factor);
        }, this::shouldSpray));

        behaviours.add(new SprayerFluidTransportBehaviour(this));
    }

    private boolean shouldSpray() {
        return !hasFrontPipe && angle > 0.001f;
    }


    public FluidTank getTank() {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        return tank;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        updatePipeConnections();
        updateEnabledState();
        updateAngle();
    }

    private void updatePipeConnections() {
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        boolean front = isPipeConnected(facing);
        boolean back = isPipeConnected(facing.getOpposite());

        if (front != hasFrontPipe || back != hasBackPipe) {
            hasFrontPipe = front;
            hasBackPipe = back;
            setChanged();
            sendData();
            refreshNetwork();
        }
    }

    private void updateEnabledState() {
        boolean isPipeMode = hasFrontPipe && hasBackPipe;
        BlockState state = getBlockState();
        boolean currentEnabled = state.getValue(SprayerBlock.ENABLED);
        if (currentEnabled != isPipeMode) {
            if (level != null) {
                level.setBlock(worldPosition, state.setValue(SprayerBlock.ENABLED, isPipeMode), 3);
                refreshNetwork();
            }
        }
    }

    private void refreshNetwork() {
        if (level == null) return;
        level.invalidateCapabilities(worldPosition);
        FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1, TickPriority.HIGH);
        SprayerFluidTransportBehaviour behaviour = BlockEntityBehaviour.get(level, worldPosition, SprayerFluidTransportBehaviour.TYPE);
        if (behaviour != null) {
            behaviour.wipePressure();
        }
    }

    private void updateAngle() {
        float speed = getTheoreticalSpeed();
        if (speed != 0) {
            float delta = speed * ANGLE_SPEED_SCALE;
            float newAngle = Mth.clamp(angle + delta, 0f, 180f);
            if (newAngle != angle) {
                angle = newAngle;
                setChanged();
                sendData();
            }
        }
    }

    private boolean isPipeConnected(Direction direction) {
        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState;
        if (level != null) {
            neighborState = level.getBlockState(neighborPos);
            return FluidPipeBlock.canConnectTo(level, neighborPos, neighborState, direction.getOpposite());
        }
        return false;
    }

    // ===== 流体能力提供（供注册表调用） =====

    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;

        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) {
            return null; // 侧面不接入
        }

        // 管道模式：不暴露能力（返回 null 使 hasFluidCapability 为 false）
        if (hasFrontPipe && hasBackPipe) {
            return null;
        }

        // 喷射模式：后方完全访问，前方只允许抽取
        if (side == facing.getOpposite()) {
            return this; // 后方：可填充和抽取
        } else {
            return new RestrictedFluidHandler(getTank()); // 前方：只允许抽取
        }
    }

    // ===== IFluidHandler 实现（用于后方完全访问） =====

    @Override
    public int getTanks() {
        return getTank().getTanks();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tankIndex) {
        return getTank().getFluidInTank(tankIndex);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return getTank().getTankCapacity(tankIndex);
    }

    @Override
    public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
        return getTank().isFluidValid(tankIndex, stack);
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return getTank().fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return getTank().drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        return getTank().drain(maxDrain, action);
    }

    // ===== 限制处理器（前方只抽取） =====

    private static class RestrictedFluidHandler implements IFluidHandler {
        private final FluidTank tank;

        public RestrictedFluidHandler(FluidTank tank) {
            this.tank = tank;
        }

        @Override
        public int getTanks() {
            return tank.getTanks();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            return tank.getFluidInTank(tankIndex);
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tank.getTankCapacity(tankIndex);
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return tank.isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
            return 0; // 禁止填充
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    }

    // ===== NBT =====

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        angle = compound.getFloat("Angle");
        hasFrontPipe = compound.getBoolean("HasFrontPipe");
        hasBackPipe = compound.getBoolean("HasBackPipe");
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        tank.readFromNBT(registries, compound.getCompound("Tank"));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Angle", angle);
        compound.putBoolean("HasFrontPipe", hasFrontPipe);
        compound.putBoolean("HasBackPipe", hasBackPipe);
        if (tank != null) {
            compound.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        }
    }
}