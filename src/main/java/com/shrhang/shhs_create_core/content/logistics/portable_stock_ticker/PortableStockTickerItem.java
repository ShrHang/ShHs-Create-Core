package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.shrhang.shhs_create_core.content.registries.ShHsComponentTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

import static com.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;

public class PortableStockTickerItem extends Item {
    private static final int STATUS_REFRESH_INTERVAL = 20;

    public PortableStockTickerItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        LogisticsNetworkLink link = stack.get(ShHsComponentTypes.LOGISTICS_NETWORK_LINK);
        if (link != null && !Screen.hasShiftDown()) {
            UUID networkId = link.networkId();
            requestStatusIfNeeded(networkId);
            PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(networkId);
            PortableStockTickerClientData.NetworkStatus status =
                    snapshot == null ? PortableStockTickerClientData.NetworkStatus.UNKNOWN : snapshot.status();
            if (status == PortableStockTickerClientData.NetworkStatus.NO_NETWORK) {
                tooltipComponents.add(textComponent("portable_stock_ticker.no_network").withStyle(ChatFormatting.DARK_RED));
            } else if (status == PortableStockTickerClientData.NetworkStatus.UNLOADED) {
                tooltipComponents.add(textComponent("portable_stock_ticker.unloaded").withStyle(ChatFormatting.DARK_RED));
            } else if (Screen.hasAltDown()) {
                tooltipComponents.add(Component.literal(String.valueOf(networkId)).withStyle(ChatFormatting.GREEN));
            } else {
                tooltipComponents.add(textComponent("portable_stock_ticker.tooltip.linked").withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.isCrouching()) {
            BlockHitResult blockHitResult = (BlockHitResult) player.pick(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 0.0f, false);
            if (linkTo(player, stack, level, blockHitResult.getBlockPos())) {
                player.displayClientMessage(CreateLang.translate("logistically_linked.tuned").component(), true);
                return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
            }
            return new InteractionResultHolder<>(InteractionResult.PASS, stack);
        }

        InteractionResult result = tryToOpenMenu(player, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return new InteractionResultHolder<>(result, stack);

    }

    public static boolean linkTo(Player player, ItemStack stack, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        UUID networkId;
        if (be instanceof StockCheckingBlockEntity stockCheckingBE) {
            networkId = stockCheckingBE.behaviour.freqId;
        } else if (be instanceof PackagerLinkBlockEntity packagerLinkBE) {
            networkId = packagerLinkBE.behaviour.freqId;
        } else {
            return false;
        }

        if (!Create.LOGISTICS.mayInteract(networkId, player)) {
            player.displayClientMessage(CreateLang.translate("logistically_linked.protected").style(ChatFormatting.DARK_RED).component(), true);
            return false;
        }

        stack.set(ShHsComponentTypes.LOGISTICS_NETWORK_LINK, new LogisticsNetworkLink(networkId));
        return true;
    }

    public static boolean tryToOpenMenu(Player player, ItemStack stack) {
        LogisticsNetworkLink link = stack.get(ShHsComponentTypes.LOGISTICS_NETWORK_LINK);
        if (link == null) {
            player.displayClientMessage(textComponent("portable_stock_ticker.no_data").withStyle(ChatFormatting.DARK_GRAY), true);
            return false;
        }

        UUID networkId = link.networkId();
        if (!checkLink(player, networkId)) return false;

        player.openMenu(new PortableStockTickerMenuProvider(networkId), buf -> buf.writeUUID(networkId));
        return true;
    }

    public static boolean checkLink(Player player, UUID networkId) {
        PortableStockTickerClientData.NetworkStatus status = getNetworkStatus(player, networkId);
        if (status == PortableStockTickerClientData.NetworkStatus.NO_NETWORK) {
            player.displayClientMessage(textComponent("portable_stock_ticker.no_network").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (status == PortableStockTickerClientData.NetworkStatus.UNLOADED) {
            player.displayClientMessage(textComponent("portable_stock_ticker.unloaded").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (status != PortableStockTickerClientData.NetworkStatus.AVAILABLE) {
            player.displayClientMessage(CreateLang.translate("logistically_linked.protected").style(ChatFormatting.DARK_RED).component(), true);
            return false;
        }

        return true;
    }

    public static PortableStockTickerClientData.NetworkStatus getNetworkStatus(@NotNull Player player, UUID networkId) {
        LogisticsNetwork network = Create.LOGISTICS.logisticsNetworks.get(networkId);
        if (network == null) {
            return PortableStockTickerClientData.NetworkStatus.NO_NETWORK;
        }
        if (!Create.LOGISTICS.mayInteract(networkId, player)) {
            return PortableStockTickerClientData.NetworkStatus.UNKNOWN;
        }
        if (LogisticallyLinkedBehaviour.getAllPresent(networkId, false).isEmpty()) {
            return PortableStockTickerClientData.NetworkStatus.UNLOADED;
        }
        return PortableStockTickerClientData.NetworkStatus.AVAILABLE;
    }

    public record PortableStockTickerMenuProvider(UUID networkId) implements MenuProvider {

        @Override
        public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
            return PortableStockTickerMenu.create(containerId, playerInventory, networkId);
        }

        @Override
        public @NotNull Component getDisplayName() {
            return Component.empty();
        }
    }

    private static void requestStatusIfNeeded(UUID networkId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.getOrCreate(networkId);
        if (!snapshot.shouldRequestStatus(gameTime, STATUS_REFRESH_INTERVAL)) {
            return;
        }

        snapshot.markStatusRequest(gameTime);
        PacketDistributor.sendToServer(new StockStatusPacket.StockStatusRequestPacket(networkId));
    }
}
