package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DimensionParcelStationBlock extends NetedBlock implements EntityBlock {
    public DimensionParcelStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!(level.isClientSide()) && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof DimensionParcelStationBlockEntity station) {
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null && net.isManager(player))
                bind(station, net, player, true);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof PackagerBlock)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof DimensionParcelStationBlockEntity station))
            return InteractionResult.PASS;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)
                toggleBinding(station, serverPlayer);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(station, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void toggleBinding(DimensionParcelStationBlockEntity station, ServerPlayer player) {
        if (station.getNetId() >= 0) {
            int oldId = station.getNetId();
            DimensionsNet current = station.getNet();
            if (current == null || current.isManager(player)) {
                station.clearNetId();
                player.sendSystemMessage(Component.translatable(
                        "text.shhs_create_core.dimension_parcel_station.unbound", oldId));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "text.shhs_create_core.dimension_parcel_station.no_binding_permission"));
            }
            return;
        }

        DimensionsNet target = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (target == null) {
            player.sendSystemMessage(Component.translatable(
                    "text.shhs_create_core.dimension_parcel_station.no_primary_network"));
            return;
        }
        if (!target.isManager(player)) {
            player.sendSystemMessage(Component.translatable(
                    "text.shhs_create_core.dimension_parcel_station.no_binding_permission"));
            return;
        }
        bind(station, target, player, true);
    }

    private static void bind(DimensionParcelStationBlockEntity station, DimensionsNet net,
                             ServerPlayer player, boolean reportFailure) {
        if (station.trySetNetId(net.getId())) {
            player.sendSystemMessage(Component.translatable(
                    "text.shhs_create_core.dimension_parcel_station.bound", net.getNetworkName()));
        } else if (reportFailure) {
            player.sendSystemMessage(Component.translatable(
                    "text.shhs_create_core.dimension_parcel_station.limit_reached"));
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DimensionParcelStationBlockEntity(pos, state);
    }
}
