package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.shrhang.shhs_create_core.content.registries.ShHsComponentTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
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
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;
import java.util.UUID;

import static com.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;
import static net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND;

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
            LogisticsNetworkStatus status =
                    snapshot == null ? LogisticsNetworkStatus.INACCESSIBLE : snapshot.status();
            if (status == LogisticsNetworkStatus.NO_NETWORK) {
                tooltipComponents.add(textComponent("portable_stock_ticker.no_network").withStyle(ChatFormatting.DARK_RED));
            } else if (status == LogisticsNetworkStatus.UNLOADED) {
                tooltipComponents.add(textComponent("portable_stock_ticker.unloaded").withStyle(ChatFormatting.DARK_RED));
            } else if (Screen.hasAltDown()) {
                tooltipComponents.add(Component.literal(NbtUtils.createUUID(networkId).toString()).withStyle(ChatFormatting.GREEN));
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

    public static void tryToOpenFromInventory(Player player) {
        ItemStack stack = findCuriosTicker(player, true);
        if (stack.isEmpty()) stack = findInventoryTicker(player, true);
        if (stack.isEmpty()) stack = findCuriosTicker(player, false);
        if (stack.isEmpty()) stack = findInventoryTicker(player, false);
        if (!stack.isEmpty()) tryToOpenMenu(player, stack);
    }

    private static ItemStack findInventoryTicker(Player player, boolean requireLinked) {
        Inventory inventory = player.getInventory();
        int mainHandSlot = inventory.selected;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) { // 优先选择非主副手
            if (slot == mainHandSlot || slot == SLOT_OFFHAND) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (isOpenableTicker(stack, requireLinked)) {
                return stack;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (isOpenableTicker(offhand, requireLinked)) { // 其次选择副手
            return offhand;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (isOpenableTicker(mainHand, requireLinked)) { // 最后才是主手
            return mainHand;
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack findCuriosTicker(Player player, boolean requireLinked) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findCurios(stack -> isOpenableTicker(stack, requireLinked))
                        .stream()
                        .findFirst()
                        .map(SlotResult::stack))
                .orElse(ItemStack.EMPTY);
    }

    private static boolean isOpenableTicker(ItemStack stack, boolean requireLinked) {
        return stack.getItem() instanceof PortableStockTickerItem
                && (!requireLinked || stack.has(ShHsComponentTypes.LOGISTICS_NETWORK_LINK));
    }

    public static boolean checkLink(Player player, UUID networkId) {
        LogisticsNetworkStatus status = LogisticsNetworkStatus.resolve(player, networkId);
        if (status == LogisticsNetworkStatus.NO_NETWORK) {
            player.displayClientMessage(textComponent("portable_stock_ticker.no_network").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (status == LogisticsNetworkStatus.UNLOADED) {
            player.displayClientMessage(textComponent("portable_stock_ticker.unloaded").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (status != LogisticsNetworkStatus.AVAILABLE) {
            player.displayClientMessage(CreateLang.translate("logistically_linked.protected").style(ChatFormatting.DARK_RED).component(), true);
            return false;
        }

        return true;
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
