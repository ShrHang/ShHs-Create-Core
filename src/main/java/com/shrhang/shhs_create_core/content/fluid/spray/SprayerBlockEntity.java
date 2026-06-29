package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
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

import java.util.*;
import java.util.function.Predicate;

/**
 * 喷洒器方块实体，管理流体储罐、角度调节、喷洒条件及管道模式。
 * <p>
 * 管道模式下，喷口方向由角度动态决定，喷口集合变化时自动刷新管道网络。
 * 喷口更新仅在角度停止变化或环境条件变化时触发，避免角度连续变化时重复计算。
 * <p>
 * 储罐容量已提升至 32 mB，以适应最高 32 mB/tick 的喷洒消耗。
 * <p>
 * 注：管道与滴灌相关能力目前混合在此实体中，未来可能被拆分为独立的模块。
 */
public class SprayerBlockEntity extends KineticBlockEntity implements IFluidHandler {

    private static final int TANK_CAPACITY = 32;          // 提升至 32 mB
    private static final int MAX_CONSUMPTION = 32;        // 最高消耗 32 mB/tick
    private static final float ANGLE_SPEED_SCALE = 0.3f;
    private static final float ANGLE_EPSILON = 1e-6f;

    // 喷口参数
    private static final float SPOUT_MIN_ANGLE = 45.0f;
    private static final int MAX_SPOUTS = 4; // 180/45

    private FluidTank tank;
    private float angle = 180f;

    private boolean hasFrontPipe = false;
    private boolean hasBackPipe = false;
    private boolean frontBlocked = false;

    private Set<Direction> openSpouts = Collections.emptySet();
    private SprayerFluidTransportBehaviour transportBehaviour;

    // 角度变化挂起标志
    private boolean angleChangedPending = false;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        // 初始加载时强制计算一次喷口
        onConditionsChanged();
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

        Predicate<Direction> spoutPredicate = this::isSpoutOpen;
        transportBehaviour = new SprayerFluidTransportBehaviour(this, spoutPredicate);
        behaviours.add(transportBehaviour);
    }

    /**
     * 判断常规喷洒是否允许：前方无管道、无阻风方块，且角度不为零。
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
        if (level != null && !level.isClientSide) {
            updatePipeConnections();
            updateFrontBlocked();
            updateAngle();
            updateEnabledState();
        }
        super.tick();
    }

    // ===== 管道连接模块 =====

    /**
     * 更新前后方管道连接状态，若变化则触发喷口重算。
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
            onConditionsChanged();
        }
    }

    /**
     * 检查指定方向是否连接管道。
     */
    private boolean isPipeConnected(Direction direction) {
        if (level == null) return false;
        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        return FluidPipeBlock.canConnectTo(level, neighborPos, neighborState, direction.getOpposite());
    }

    /**
     * 同步管道模式状态到方块的 ENABLED 属性。
     * ENABLED 仅影响 canPullFluidFrom，不影响接口，故不刷新网络。
     */
    private void updateEnabledState() {
        boolean isPipeMode = hasFrontPipe && hasBackPipe && (angle > 5f);
        BlockState state = getBlockState();
        boolean currentEnabled = state.getValue(SprayerBlock.ENABLED);
        if (currentEnabled != isPipeMode) {
            if (level != null) {
                level.setBlock(worldPosition, state.setValue(SprayerBlock.ENABLED, isPipeMode), 3);
            }
        }
    }

    // ===== 阻风检测模块 =====

    /**
     * 更新喷洒方向是否被阻风方块阻挡，若变化则触发喷口重算。
     */
    private void updateFrontBlocked() {
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        BlockState frontState = level != null ? level.getBlockState(frontPos) : null;
        boolean newBlocked = frontState != null && isBlocking(frontState, frontPos);
        if (newBlocked != frontBlocked) {
            frontBlocked = newBlocked;
            setChanged();
            sendData();
            onConditionsChanged();
        }
    }

    /**
     * 判断方块是否阻挡气流（空气、透明方块等不阻挡）。
     */
    private boolean isBlocking(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(AllTags.AllBlockTags.FAN_TRANSPARENT.tag)) return false;
        if (level == null) return true;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }

    // ===== 角度更新与事件触发 =====

    /**
     * 根据转速更新喷洒角度（范围 0~180°）。
     * 角度变化时设置挂起标志，稳定后触发喷口更新。
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
                angleChangedPending = true;
            }
        }
        // 角度未变化且挂起标志为真 -> 角度已稳定，触发更新
        if (angleChangedPending) {
            onConditionsChanged();
            angleChangedPending = false;
        }
    }

    /**
     * 环境条件变化时的回调，重算喷口集合并刷新网络。
     */
    private void onConditionsChanged() {
        Set<Direction> newOpen = computeOpenSpouts();
        if (!newOpen.equals(openSpouts)) {
            openSpouts = newOpen;
            refreshNetwork();
        }
    }

    /**
     * 判断某方向是否为当前打开的喷口。
     */
    private boolean isSpoutOpen(Direction direction) {
        return openSpouts.contains(direction);
    }

    /**
     * 计算当前应打开的喷口方向集合。
     * 仅在管道模式且角度≥45°时有效，否则空集。
     * <p>
     * 优先级顺序：
     * 1. 空置面（非 facing，非传动轴）：竖直下→上，水平东→南→西→北
     * 2. 传动面（传动轴方向）：首选与 facing 逆时针旋转一致的方向，其次另一个
     * 每个方向需未被管道或阻风方块阻挡。
     */
    private Set<Direction> computeOpenSpouts() {
        if (!isPipeModeActive()) return Collections.emptySet();

        int count = getSpoutCount(angle);
        if (count <= 0) return Collections.emptySet();

        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        Direction.Axis shaftAxis = KineticBlockEntityRenderer.getRotationAxisOf(this);
        Set<Direction> blocked = getBlockedDirections();

        List<Direction> priorityList = new ArrayList<>();

        // 空置面：竖直
        for (Direction dir : new Direction[]{Direction.DOWN, Direction.UP}) {
            if (dir != facing && !blocked.contains(dir) && dir.getAxis() != shaftAxis) {
                priorityList.add(dir);
            }
        }
        // 空置面：水平
        for (Direction dir : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}) {
            if (dir != facing && !blocked.contains(dir) && dir.getAxis() != shaftAxis) {
                priorityList.add(dir);
            }
        }

        // 传动面
        List<Direction> shaftDirs = getShaftDirections(shaftAxis);
        Direction primary = getPrimaryShaftDirection(facing, shaftAxis);
        Direction secondary = shaftDirs.get(0).equals(primary) ? shaftDirs.get(1) : shaftDirs.get(0);
        for (Direction dir : new Direction[]{primary, secondary}) {
            if (!blocked.contains(dir)) {
                priorityList.add(dir);
            }
        }

        Set<Direction> result = new HashSet<>();
        int added = 0;
        for (Direction dir : priorityList) {
            result.add(dir);
            added++;
            if (added >= count) break;
        }
        return result;
    }

    /**
     * 获取所有被管道或阻风方块阻挡的方向。
     */
    private Set<Direction> getBlockedDirections() {
        Set<Direction> blocked = new HashSet<>();
        if (level == null) return blocked;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (isPipeConnected(dir) || isBlocking(neighborState, neighborPos)) {
                blocked.add(dir);
            }
        }
        return blocked;
    }

    /**
     * 获取指定轴上的两个方向。
     */
    private List<Direction> getShaftDirections(Direction.Axis axis) {
        if (axis == Direction.Axis.X) return List.of(Direction.EAST, Direction.WEST);
        if (axis == Direction.Axis.Y) return List.of(Direction.UP, Direction.DOWN);
        return List.of(Direction.NORTH, Direction.SOUTH);
    }

    /**
     * 确定传动面中的首选方向（与 facing 逆时针旋转方向一致）。
     */
    private Direction getPrimaryShaftDirection(Direction facing, Direction.Axis shaftAxis) {
        if (shaftAxis == Direction.Axis.Y) {
            if (facing == Direction.UP) return Direction.EAST;
            if (facing == Direction.DOWN) return Direction.WEST;
            return Direction.UP;
        } else if (shaftAxis == Direction.Axis.X) {
            return Direction.EAST;
        } else {
            return Direction.SOUTH;
        }
    }

    /**
     * 根据角度计算喷口数量（0~4），45° 起每增加 45° 增一个。
     */
    private int getSpoutCount(float angle) {
        if (angle < SPOUT_MIN_ANGLE) return 0;
        int count = 1 + (int) ((angle - SPOUT_MIN_ANGLE) / 45.0f);
        return Math.min(count, MAX_SPOUTS);
    }

    /**
     * 判断是否处于管道模式（前后均有管道且角度>5°）。
     */
    public boolean isPipeModeActive() {
        return hasFrontPipe && hasBackPipe && (angle > 5f);
    }

    /**
     * 刷新流体网络及能力，使当前喷口方向建立管道连接。
     * 使用 Create 标准流程：失效能力 → 传播管道变化 → 重置压力。
     */
    private void refreshNetwork() {
        if (level == null || transportBehaviour == null) return;
        level.invalidateCapabilities(worldPosition);
        FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1, TickPriority.HIGH);
        transportBehaviour.wipePressure();
    }

    // ===== 流体能力提供 =====

    /**
     * 获取指定方向的流体处理器。
     * 管道模式下不暴露任何方向的能力，以防干扰管道网络。
     * 非管道模式下，后方完全访问，前方只抽取。
     */
    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) return null;

        if (hasFrontPipe && hasBackPipe) return null;

        if (side == facing.getOpposite()) {
            return this;
        } else {
            return new RestrictedFluidHandler(getTank());
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