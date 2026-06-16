package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.Create;
import com.shrhang.shhs_create_core.api.registries.ShHsComponentTypes;
import com.shrhang.shhs_create_core.api.registries.ShHsMenuTypes;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
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
    public PortableStockTickerItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        PortableStockTickerLink link = stack.get(ShHsComponentTypes.PORTABLE_STOCK_TICKER_LINK);
        Level clientLevel = Minecraft.getInstance().level;
        if (link != null && !Screen.hasShiftDown() && clientLevel != null) {
            if (clientLevel.dimension().equals(link.dimension())
                    && !(clientLevel.getBlockEntity(link.sourcePos()) instanceof StockTickerBlockEntity))
                tooltipComponents.add(textComponent("portable_stock_ticker.no_block"
                ).withStyle(ChatFormatting.DARK_RED));
            else
                tooltipComponents.add(textComponent("portable_stock_ticker.tooltip.linked_to",
                        Component.literal(link.dimension().location().toString()).withStyle(ChatFormatting.GREEN),
                        Component.literal(link.sourcePos().toShortString()).withStyle(ChatFormatting.GREEN)
                ).withStyle(ChatFormatting.DARK_GREEN));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            PortableStockTickerLink link = stack.get(ShHsComponentTypes.PORTABLE_STOCK_TICKER_LINK);
            if (!player.isCrouching() && link != null)
                PacketDistributor.sendToServer(new PortableStockRequestPacket(link.networkId()));
            return InteractionResultHolder.pass(stack);
        }

        if (player.isCrouching()) {
            BlockHitResult blockHitResult = (BlockHitResult) player.pick(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 0.0f, false);
            BlockPos blockPos = blockHitResult.getBlockPos();
            InteractionResult result = InteractionResult.PASS;
            if (linkTo(stack, level, blockPos)) {
                player.sendSystemMessage(textComponent("portable_stock_ticker.link_success").withStyle(ChatFormatting.GREEN));
                result = InteractionResult.SUCCESS;
            }
            return new InteractionResultHolder<>(result, stack);
        }

        InteractionResult result = tryToOpenMenu(level, player, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return new InteractionResultHolder<>(result, stack);

    }

    public static boolean linkTo(ItemStack stack, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StockTickerBlockEntity stockTicker) {
            PortableStockTickerLink newLink = new PortableStockTickerLink(stockTicker.behaviour.freqId, level.dimension(), pos);
            stack.set(ShHsComponentTypes.PORTABLE_STOCK_TICKER_LINK, newLink);
            return true;
        }
        return false;
    }

    public static boolean tryToOpenMenu(Level level, Player player, ItemStack stack) {
        PortableStockTickerLink link = stack.get(ShHsComponentTypes.PORTABLE_STOCK_TICKER_LINK);
        if (link == null) {
            player.sendSystemMessage(textComponent("portable_stock_ticker.no_data").withStyle(ChatFormatting.DARK_GRAY));
            return false;
        }

        if (!validateLinkedNetwork(player, link, true))
            return false;

        player.openMenu(new PortableStockTickerMenuProvider(link.networkId()), buf -> buf.writeUUID(link.networkId()));
        return true;
    }

    public static boolean validateLinkedNetwork(Player player, PortableStockTickerLink link, boolean reportStatus) {
        UUID networkId = link.networkId();
        LogisticsNetwork network = Create.LOGISTICS.logisticsNetworks.get(networkId);
        if (network == null) {
            player.sendSystemMessage(textComponent("portable_stock_ticker.no_network").withStyle(ChatFormatting.DARK_RED));
            return false;
        }

        if (!Create.LOGISTICS.mayInteract(networkId, player)) {
            player.sendSystemMessage(textComponent("portable_stock_ticker.network_locked").withStyle(ChatFormatting.DARK_RED));
            return false;
        }

        InventorySummary summary = LogisticsManager.getSummaryOfNetwork(networkId, false);
        if (reportStatus) {
            player.sendSystemMessage(textComponent("portable_stock_ticker.network_status",
                    summary.getTotalCount(),
                    summary.contributingLinks,
                    network.loadedLinks.size(),
                    Create.LOGISTICS.getUnloadedLinkCount(networkId)
            ).withStyle(ChatFormatting.DARK_GRAY));
        }

        return true;
    }

    public record RemoteStockKeeperRequestMenuProvider(StockTickerBlockEntity stockTickerBE) implements MenuProvider {

        public AbstractContainerMenu createMenu(int pContainerId, @NotNull Inventory pPlayerInventory, @NotNull Player pPlayer) {
            return new RemoteStockKeeperRequestMenu(ShHsMenuTypes.REMOTE_STOCK_KEEPER_REQUEST.get(), pContainerId, pPlayerInventory, stockTickerBE);
        }

        @Override
        public @NotNull Component getDisplayName() {
            return Component.empty();
        }
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
}
