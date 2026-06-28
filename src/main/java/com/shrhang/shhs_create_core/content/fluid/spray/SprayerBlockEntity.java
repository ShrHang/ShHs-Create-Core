package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.AllTags;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 喷洒器方块实体，管理流体储罐、角度调节、喷洒条件及管道模式。
 * 管道模式（前后均有管道）相关逻辑未来可能移除。
 */
public class SprayerBlockEntity extends KineticBlockEntity implements IFluidHandler {

    private static final int TANK_CAPACITY = 5;
    private static final int MAX_CONSUMPTION = 4;
    private static final float ANGLE_SPEED_SCALE = 0.3f;
    private static final float ANGLE_EPSILON = 1e-6f;

    private FluidTank tank;
    private float angle = 180f;

    // 管道模式状态（未来可能移除）
    private boolean hasFrontPipe = false;
    private boolean hasBackPipe = false;

    private boolean frontBlocked = false;   // 喷洒方向是否有阻风方块

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

    /**
     * 判断是否允许喷洒：前方无管道、无阻风方块，且角度不为零。
     */
    private boolean shouldSpray() {
        return !hasFrontPipe && !frontBlocked && angle > ANGLE_EPSILON;
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

        // 按模块顺序更新（角度优先，因为后续依赖角度值）
        updatePipeConnections();   // 管道连接检测（未来可移除）
        updateAngle();             // 角度变化（影响 ENABLED 和喷洒）
        updateFrontBlocked();      // 阻风检测
        updateEnabledState();      // 管道模式状态同步（依赖角度）
    }

    // ===== 管道连接模块（未来可能移除） =====

    /**
     * 更新前后方管道连接状态。
     */
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

    /**
     * 检查指定方向是否连接管道。
     */
    private boolean isPipeConnected(Direction direction) {
        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState;
        if (level != null) {
            neighborState = level.getBlockState(neighborPos);
            return FluidPipeBlock.canConnectTo(level, neighborPos, neighborState, direction.getOpposite());
        }
        return false;
    }

    /**
     * 同步管道模式到方块的 ENABLED 属性，并刷新网络。
     * 管道模式激活条件：前后均有管道，且角度大于 0°（即未完全关闭）。
     */
    private void updateEnabledState() {
        boolean isPipeMode = hasFrontPipe && hasBackPipe && (angle > 5f);
        BlockState state = getBlockState();
        boolean currentEnabled = state.getValue(SprayerBlock.ENABLED);
        if (currentEnabled != isPipeMode) {
            if (level != null) {
                level.setBlock(worldPosition, state.setValue(SprayerBlock.ENABLED, isPipeMode), 3);
                refreshNetwork();
            }
        }
    }

    /**
     * 刷新流体网络及能力。
     */
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

    // ===== 阻风检测模块 =====

    /**
     * 更新喷洒方向是否被阻风方块阻挡。
     */
    private void updateFrontBlocked() {
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        BlockState frontState = null;
        if (level != null) {
            frontState = level.getBlockState(frontPos);
        }
        if (frontState != null) {
            frontBlocked = isBlocking(frontState, frontPos);
        }
    }

    /**
     * 判断方块是否阻挡气流（与鼓风机一致）。
     * 空气、流体、无碰撞箱、带 fan_transparent 标签的方块不阻挡。
     */
    private boolean isBlocking(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(AllTags.AllBlockTags.FAN_TRANSPARENT.tag)) return false;
        VoxelShape shape = null;
        if (level != null) {
            shape = state.getCollisionShape(level, pos);
        }
        return shape == null || !shape.isEmpty();
    }

    // ===== 角度更新模块 =====

    /**
     * 根据转速更新喷洒角度。
     */
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

    // ===== 流体能力提供 =====

    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) {
            return null;
        }
        // 管道模式下不暴露能力（未来可移除）
        if (hasFrontPipe && hasBackPipe) {
            return null;
        }
        if (side == facing.getOpposite()) {
            return this; // 后方完全访问
        } else {
            return new RestrictedFluidHandler(getTank()); // 前方只抽取
        }
    }

    // ===== IFluidHandler 实现（后方完全访问） =====

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
            return 0;
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

    // ===== NBT 读写 =====

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