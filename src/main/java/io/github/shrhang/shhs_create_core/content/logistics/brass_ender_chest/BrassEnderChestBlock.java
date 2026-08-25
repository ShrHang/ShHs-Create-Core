package io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Objects;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.titleComponent;
import static io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes.BRASS_ENDER_CHEST_BE;

public class BrassEnderChestBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, IWrenchable, IBE<BrassEnderChestBlockEntity> {
    public static final MapCodec<? extends HorizontalDirectionalBlock> CODEC = simpleCodec(BrassEnderChestBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_HALF = Block.box(1, 0, 1, 14, 14, 14);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BrassEnderChestBlock(Properties p_i48440_1_) {
        super(p_i48440_1_);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    // Block Interaction Core
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof BrassEnderChestBlockEntity brassEnderChestBE))
            return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos blockpos = pos.above();
        if (level.getBlockState(blockpos).isRedstoneConductor(level, blockpos)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if ((brassEnderChestBE).getTargetUUID() == null)
            return InteractionResult.sidedSuccess(level.isClientSide);

        Player targetPlayer = level.getPlayerByUUID((brassEnderChestBE).getTargetUUID());
        if (targetPlayer == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isCrouching()) {
            if (Objects.equals(targetPlayer, player)) {
                brassEnderChestBE.changeLock();
                return InteractionResult.CONSUME;
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (brassEnderChestBE.isLocked() && !Objects.equals(targetPlayer, player))
                return InteractionResult.sidedSuccess(level.isClientSide);

            player.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, pl) -> ChestMenu.threeRows(id, inventory, targetPlayer.getEnderChestInventory()),
                            titleComponent("container.endchest", targetPlayer.getName(), Component.translatable("container.enderchest"))
                    )
            );

            level.playSound(
                    null,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    SoundEvents.ENDER_CHEST_OPEN,
                    SoundSource.BLOCKS,
                    0.5F,
                    level.random.nextFloat() * 0.1F + 0.9F
            );

            player.awardStat(Stats.OPEN_ENDERCHEST);
            PiglinAi.angerNearbyPiglins(player, true);

            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            withBlockEntityDo(level, pos, be -> be.setTargetUUID(player.getUUID()));
        }
    }

    // Horizontal Directional Block
    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    // Water loggable Block
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    // IBE Methods
    @Override
    public Class<BrassEnderChestBlockEntity> getBlockEntityClass() {
        return BrassEnderChestBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrassEnderChestBlockEntity> getBlockEntityType() {
        return BRASS_ENDER_CHEST_BE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Objects.requireNonNull(IBE.super.newBlockEntity(pos, state));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return IBE.super.getTicker(level, state, type);
    }

    // Voxel Shapes
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return SHAPE_HALF;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return SHAPE_HALF;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return SHAPE_HALF;
    }
}