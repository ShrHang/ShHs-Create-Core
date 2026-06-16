package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.shrhang.shhs_create_core.api.registries.ShHsComponentTypes;
import com.shrhang.shhs_create_core.api.registries.ShHsMenuTypes;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;

public class PortableStockTickerItem extends Item {
    public PortableStockTickerItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        LvPosRecord record = stack.getOrDefault(ShHsComponentTypes.LV_POS, LvPosRecord.EMPTY);
        if (record != LvPosRecord.EMPTY && !Screen.hasShiftDown() && Minecraft.getInstance().level != null) {
            if (Minecraft.getInstance().level.dimension() != record.dimension())
                tooltipComponents.add(textComponent("portable_stock_ticker.different_dimension"
                ).withStyle(ChatFormatting.DARK_RED));
            else if (!(Minecraft.getInstance().level.getBlockEntity(record.pos()) instanceof StockTickerBlockEntity))
                tooltipComponents.add(textComponent("portable_stock_ticker.no_block"
                ).withStyle(ChatFormatting.DARK_RED));
            else
                tooltipComponents.add(textComponent("portable_stock_ticker.tooltip.linked_to",
                        Component.literal(record.dimension().location().toString()).withStyle(ChatFormatting.GREEN),
                        Component.literal(record.pos().toShortString()).withStyle(ChatFormatting.GREEN)
                ).withStyle(ChatFormatting.DARK_GREEN));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide())
            return InteractionResultHolder.pass(stack);

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
        if (be instanceof StockTickerBlockEntity) {
            LvPosRecord newRecord = new LvPosRecord(level.dimension(), pos);
            stack.set(ShHsComponentTypes.LV_POS, newRecord);
            return true;
        }
        return false;
    }

    public static boolean tryToOpenMenu(Level level, Player player, ItemStack stack) {
        LvPosRecord record = stack.get(ShHsComponentTypes.LV_POS);
        if (record == LvPosRecord.EMPTY || record == null) {
            player.sendSystemMessage(textComponent("portable_stock_ticker.no_data").withStyle(ChatFormatting.DARK_GRAY));
            return false;
        }

        ResourceKey<Level> targetDimension = record.dimension();
        BlockPos pos = record.pos();

        if (level.dimension() != targetDimension) {
            player.sendSystemMessage(textComponent("portable_stock_ticker.different_dimension").withStyle(ChatFormatting.DARK_RED));
            return false;
        }

        if (Minecraft.getInstance().level == null)
            return false;

        BlockEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
        if (be instanceof StockTickerBlockEntity) {
            player.openMenu(new RemoteStockKeeperRequestMenuProvider((StockTickerBlockEntity) be), buf ->
                    buf.writeBoolean(true).writeBoolean(false).writeBlockPos(pos));
            return true;
        }

        player.sendSystemMessage(textComponent("portable_stock_ticker.no_block").withStyle(ChatFormatting.DARK_RED));
        return false;
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
}
