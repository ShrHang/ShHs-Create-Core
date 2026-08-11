package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import static io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes.SPRAYER;

/**
 * 喷洒器方块，仅负责定向喷洒，不再包含管道功能。
 */
public class SprayerBlock extends DirectionalAxisKineticBlock
        implements IBE<SprayerBlockEntity>, ProperWaterloggedBlock {

    public SprayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WATERLOGGED, false));
    }

    // 此方法仅用于形状计算
    private static Axis getPipeAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean alongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        if (facing.getAxis().isVertical()) {
            return alongFirst ? Axis.X : Axis.Z;
        } else {
            return alongFirst ? facing.getClockWise().getAxis() : facing.getCounterClockWise().getAxis();
        }
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return AllShapes.FLUID_VALVE.get(getPipeAxis(state));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(WATERLOGGED));
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
    public Class<SprayerBlockEntity> getBlockEntityClass() {
        return SprayerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SprayerBlockEntity> getBlockEntityType() {
        return SPRAYER.get();
    }
}
