package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.IAxisPipe;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import org.jetbrains.annotations.NotNull;

import static com.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes.SPRAYER;

public class SprayerBlock extends DirectionalAxisKineticBlock
        implements IBE<SprayerBlockEntity>, ProperWaterloggedBlock, IAxisPipe {

    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    public SprayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ENABLED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return AllShapes.FLUID_VALVE.get(getPipeAxis(state));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(ENABLED, WATERLOGGED));
    }

    /**
     * 管道连接判定（仅作为辅助，实际连接由 FluidPipeBlock 决定，此处保留但基本无效）
     */
    @Override
    protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction direction, boolean shaftAxis) {
        if (!shaftAxis) {
            BlockState state = reader.getBlockState(pos);
            if (!(state.getBlock() instanceof SprayerBlock)) return false;
            Direction facing = state.getValue(FACING);
            Axis driveAxis = getDriveAxis(state); // 使用传动轴而非管道轴
            if (direction == facing || direction.getAxis() == driveAxis) {
                return false; // 提示不要连接，但不会强制
            }
            BlockPos offset = pos.relative(direction);
            BlockState neighbour = reader.getBlockState(offset);
            return FluidPipeBlock.canConnectTo(reader, offset, neighbour, direction);
        }
        return super.prefersConnectionTo(reader, pos, direction, shaftAxis);
    }

    /**
     * 管道轴（仅用于模型形状和 IAxisPipe），与应力轴不同。
     */
    public static Axis getPipeAxis(BlockState state) {
        if (!(state.getBlock() instanceof SprayerBlock))
            throw new IllegalStateException("Provided BlockState is not for SprayerBlock.");
        Direction facing = state.getValue(FACING);
        boolean alongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        if (facing.getAxis().isVertical()) {
            return alongFirst ? Axis.X : Axis.Z;
        } else {
            return alongFirst ? facing.getClockWise().getAxis() : facing.getCounterClockWise().getAxis();
        }
    }

    /**
     * 获取真实的应力输入轴（传动杆轴），直接使用父类方法。
     */
    public static Axis getDriveAxis(BlockState state) {
        if (!(state.getBlock() instanceof SprayerBlock block))
            throw new IllegalStateException("Not a SprayerBlock");
        return block.getRotationAxis(state);
    }

    @Override
    public Axis getAxis(BlockState state) {
        return getPipeAxis(state); // IAxisPipe 接口要求，返回管道轴
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide)
            FluidPropagator.propagateChangedPipe(world, pos, state);
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (world.isClientSide)
            return;
        if (state != oldState)
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull Block otherBlock, @NotNull BlockPos neighborPos,
                                boolean isMoving) {
        Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);
        if (d == null)
            return;
        if (!isOpenAt(state, d))
            return;
        world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    public static boolean isOpenAt(BlockState state, Direction d) {
        return d.getAxis() == getPipeAxis(state); // 这里仍然使用管道轴，因为这是管道传播逻辑
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull RandomSource r) {
        FluidPropagator.propagateChangedPipe(world, pos, state);
    }

    @Override
    protected boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(super.getStateForPlacement(context), context);
    }

    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbourState, @NotNull LevelAccessor world,
                                           @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        updateWater(world, state, pos);
        return state;
    }

    @Override
    public @NotNull FluidState getFluidState(@NotNull BlockState state) {
        return fluidState(state);
    }

    // ===== IBE =====
    @Override
    public Class<SprayerBlockEntity> getBlockEntityClass() {
        return SprayerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SprayerBlockEntity> getBlockEntityType() {
        return SPRAYER.get();
    }
}