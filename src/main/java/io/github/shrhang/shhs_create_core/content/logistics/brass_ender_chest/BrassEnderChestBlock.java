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
import net.minecraft.world.Container;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.*;
import static io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes.BRASS_ENDER_CHEST_BE;

public class BrassEnderChestBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, IWrenchable, IBE<BrassEnderChestBlockEntity> {
    public static final MapCodec<? extends HorizontalDirectionalBlock> CODEC = simpleCodec(BrassEnderChestBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_HALF = Block.box(1, 0, 1, 14, 14, 14);

    public BrassEnderChestBlock(Properties properties) {
        super(properties);
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof BrassEnderChestBlockEntity brassEnderChestBE))
            return InteractionResult.sidedSuccess(level.isClientSide);
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (brassEnderChestBE.getTargetUUID() == null)
            return feedback(player, "no_owner");
        if (player.isCrouching()) {
            if (!brassEnderChestBE.isOwner(player))
                return feedback(player, "owner_only");
            brassEnderChestBE.changeLock();
            return feedback(player, brassEnderChestBE.isLocked() ? "locked" : "unlocked");
        }
        BlockPos above = pos.above();
        if (level.getBlockState(above).isRedstoneConductor(level, above))
            return feedback(player, "blocked");
        if (!brassEnderChestBE.canAccess(player))
            return feedback(player, "access_denied");

        Container targetInventory = brassEnderChestBE.getMenuInventory(player);
        if (targetInventory == null)
            return feedback(player, brassEnderChestBE.hasInventoryLoadFailed() ? "load_failed" : "loading");
        if (player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, pl) -> ChestMenu.threeRows(id, inventory, targetInventory),
                        titleComponent("container.endchest", Component.literal(brassEnderChestBE.getTargetName()),
                                Component.translatable("container.enderchest"))
                )
        ).isEmpty())
            return InteractionResult.CONSUME;

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

    private static InteractionResult feedback(Player player, String key) {
        player.displayClientMessage(msgComponent("brass_ender_chest." + key), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player)
            withBlockEntityDo(level, pos, be -> be.setOwner(player));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

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

    @Override
    public Class<BrassEnderChestBlockEntity> getBlockEntityClass() {
        return BrassEnderChestBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrassEnderChestBlockEntity> getBlockEntityType() {
        return BRASS_ENDER_CHEST_BE.get();
    }

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
