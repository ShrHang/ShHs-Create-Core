package com.shrhang.shhs_create_core.content.fluid.spraypipe;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.fluids.FluidPropagator;
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

/**
 * 独立的管道方块，拥有与喷洒器相似的外观和动力，但无流体储存与喷洒能力。
 * 仅负责流体管道连接与传输。
 * <p>
 * 此类位于独立的 spraypipe 包中，与喷洒器完全解耦。
 */
public class SprayerPipeBlock extends DirectionalAxisKineticBlock
        implements IBE<SprayerPipeBlockEntity>, ProperWaterloggedBlock, IAxisPipe {

    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    public SprayerPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ENABLED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return AllShapes.FLUID_VALVE.get(getPipeAxis(state));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(ENABLED, WATERLOGGED));
    }

    @NotNull
    public static Axis getPipeAxis(BlockState state) {
        if (!(state.getBlock() instanceof SprayerPipeBlock))
            throw new IllegalStateException("Provided BlockState is not for SprayerPipeBlock.");
        Direction facing = state.getValue(FACING);
        boolean alongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        if (facing.getAxis().isVertical()) {
            return alongFirst ? Axis.X : Axis.Z;
        } else {
            return alongFirst ? facing.getClockWise().getAxis() : facing.getCounterClockWise().getAxis();
        }
    }

    @Override
    @NotNull
    public Axis getAxis(@NotNull BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // ----- 管道传播逻辑 -----
    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide)
            FluidPropagator.propagateChangedPipe(world, pos, state);
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos,
                        @NotNull BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (world.isClientSide) return;
        if (state != oldState)
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos,
                                @NotNull Block otherBlock, @NotNull BlockPos neighborPos, boolean isMoving) {
        Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);
        if (d == null) return;
        if (!isOpenAt(state, d)) return;
        world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    public static boolean isOpenAt(BlockState state, Direction d) {
        return d.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull RandomSource r) {
        FluidPropagator.propagateChangedPipe(world, pos, state);
    }

    // ----- 其他 -----
    @Override
    protected boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(super.getStateForPlacement(context), context);
    }

    @Override
    @NotNull
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
                                  @NotNull BlockState neighbourState, @NotNull LevelAccessor world,
                                  @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        updateWater(world, state, pos);
        return state;
    }

    @Override
    @NotNull
    public FluidState getFluidState(@NotNull BlockState state) {
        return fluidState(state);
    }

    @Override
    public Class<SprayerPipeBlockEntity> getBlockEntityClass() {
        return SprayerPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SprayerPipeBlockEntity> getBlockEntityType() {
        return null; // 未注册，纯占位
    }
}
