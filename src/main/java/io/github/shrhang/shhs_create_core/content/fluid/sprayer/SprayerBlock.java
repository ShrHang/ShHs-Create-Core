package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes.SPRAYER_BE;

public class SprayerBlock extends DirectionalAxisKineticBlock
        implements IBE<SprayerBlockEntity>, ProperWaterloggedBlock {

    private static final VoxelShaper SPRAYER_SHAPE = VoxelShaper.forDirectional(Shapes.or(
            Block.box(3, 3, 14, 13, 13, 16),
            Block.box(2, 2, -3, 14, 14, 14)
    ).optimize(), Direction.NORTH);

    public SprayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return getSprayerShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        return getSprayerShape(state);
    }

    private static VoxelShape getSprayerShape(BlockState state) {
        return SPRAYER_SHAPE.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(WATERLOGGED));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(super.getStateForPlacement(context), context);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction,
                                  BlockState neighbourState, LevelAccessor world,
                                  BlockPos pos, BlockPos neighbourPos) {
        updateWater(world, state, pos);
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    public Class<SprayerBlockEntity> getBlockEntityClass() {
        return SprayerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SprayerBlockEntity> getBlockEntityType() {
        return SPRAYER_BE.get();
    }
}