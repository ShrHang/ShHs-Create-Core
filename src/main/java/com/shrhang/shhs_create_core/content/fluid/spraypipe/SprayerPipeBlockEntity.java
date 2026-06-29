package com.shrhang.shhs_create_core.content.fluid.spraypipe;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
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

import java.util.*;

/**
 * 管道方块实体，实现转角控制多开口机制与阀门锁紧机制。
 * 开口选择不再受管道连接阻挡，仅考虑物理方块阻挡。
 * <p>
 * 实现路径与原 SprayerBlockEntity 完全一致（继承 KineticBlockEntity，直接持有 transportBehaviour 引用）。
 */
public class SprayerPipeBlockEntity extends KineticBlockEntity {

    private static final float ANGLE_SPEED_SCALE = 0.3f;
    private static final float SPOUT_MIN_ANGLE = 45.0f;
    private static final int MAX_SPOUTS = 4;

    private float angle = 180f;
    private Set<Direction> openSpouts = Collections.emptySet();
    private boolean angleChangedPending = false;

    // 直接持有行为引用，与喷洒器一致
    private SprayerFluidTransportBehaviour transportBehaviour;

    public SprayerPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 创建并保存传输行为引用
        transportBehaviour = new SprayerFluidTransportBehaviour(this, this::isSpoutOpen);
        behaviours.add(transportBehaviour);
        super.addBehaviours(behaviours);
    }

    @Override
    public void initialize() {
        super.initialize();
        // 初始加载时计算一次开口
        onConditionsChanged();
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            updateAngle();
            updateEnabledState();
        }
        super.tick();
    }

    // ===== 角度更新 =====
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
        // 角度稳定后触发开口更新
        if (angleChangedPending) {
            onConditionsChanged();
            angleChangedPending = false;
        }
    }

    // ===== 阀门锁紧：根据角度更新 ENABLED 状态 =====
    private void updateEnabledState() {
        boolean shouldEnable = angle > 5f && !openSpouts.isEmpty();
        BlockState state = getBlockState();
        boolean currentEnabled = state.getValue(SprayerPipeBlock.ENABLED);
        if (currentEnabled != shouldEnable) {
            if (level != null) {
                level.setBlock(worldPosition, state.setValue(SprayerPipeBlock.ENABLED, shouldEnable), 3);
            }
        }
    }

    // ===== 开口计算 =====
    private void onConditionsChanged() {
        Set<Direction> newOpen = computeOpenSpouts();
        if (!newOpen.equals(openSpouts)) {
            openSpouts = newOpen;
            refreshNetwork();
        }
    }

    private boolean isSpoutOpen(Direction direction) {
        return openSpouts.contains(direction);
    }

    private Set<Direction> computeOpenSpouts() {
        int count = getSpoutCount(angle);
        if (count <= 0) return Collections.emptySet();

        Direction facing = getBlockState().getValue(SprayerPipeBlock.FACING);
        Direction.Axis shaftAxis = KineticBlockEntityRenderer.getRotationAxisOf(this);
        Set<Direction> blocked = getBlockedDirections(); // 仅物理阻挡

        List<Direction> priorityList = new ArrayList<>();

        // 空置面（非 facing，非传动轴）
        for (Direction dir : Direction.values()) {
            if (dir != facing && dir.getAxis() != shaftAxis && !blocked.contains(dir)) {
                priorityList.add(dir);
            }
        }
        // 传动面（按优先级：首选与 facing 逆时针一致，次选另一个）
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

    private Set<Direction> getBlockedDirections() {
        Set<Direction> blocked = new HashSet<>();
        if (level == null) return blocked;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            // 只检查物理阻挡，不检查管道连接
            if (isBlocking(neighborState, neighborPos)) {
                blocked.add(dir);
            }
        }
        return blocked;
    }

    private boolean isBlocking(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(AllTags.AllBlockTags.FAN_TRANSPARENT.tag)) return false;
        if (level == null) return true;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }

    private List<Direction> getShaftDirections(Direction.Axis axis) {
        if (axis == Direction.Axis.X) return List.of(Direction.EAST, Direction.WEST);
        if (axis == Direction.Axis.Y) return List.of(Direction.UP, Direction.DOWN);
        return List.of(Direction.NORTH, Direction.SOUTH);
    }

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

    private int getSpoutCount(float angle) {
        if (angle < SPOUT_MIN_ANGLE) return 0;
        int count = 1 + (int) ((angle - SPOUT_MIN_ANGLE) / 45.0f);
        return Math.min(count, MAX_SPOUTS);
    }

    // ===== 刷新网络（与原喷洒器实现完全一致） =====
    private void refreshNetwork() {
        if (level == null) return;
        level.invalidateCapabilities(worldPosition);
        FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1, TickPriority.HIGH);
        // 直接通过保存的引用重置压力，避免调用不存在的 getBehaviours()
        if (transportBehaviour != null) {
            transportBehaviour.wipePressure();
        }
    }

    // ===== NBT =====
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        angle = compound.getFloat("Angle");
        // 客户端不触发网络刷新
        if (!clientPacket) {
            openSpouts = computeOpenSpouts();
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Angle", angle);
    }

    // ===== 内部传输行为（与原 SprayerFluidTransportBehaviour 逻辑一致） =====
    public static class SprayerFluidTransportBehaviour extends FluidTransportBehaviour {
        private final java.util.function.Predicate<Direction> spoutPredicate;

        public SprayerFluidTransportBehaviour(SmartBlockEntity be, java.util.function.Predicate<Direction> spoutPredicate) {
            super(be);
            this.spoutPredicate = spoutPredicate != null ? spoutPredicate : d -> false;
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            Direction facing = state.getValue(SprayerPipeBlock.FACING);
            if (direction.getAxis() == facing.getAxis()) {
                return true;
            }
            return spoutPredicate.test(direction);
        }

        @Override
        public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
            if (state.hasProperty(SprayerPipeBlock.ENABLED) && !state.getValue(SprayerPipeBlock.ENABLED)) {
                return false;
            }
            return super.canPullFluidFrom(fluid, state, direction);
        }
    }
}