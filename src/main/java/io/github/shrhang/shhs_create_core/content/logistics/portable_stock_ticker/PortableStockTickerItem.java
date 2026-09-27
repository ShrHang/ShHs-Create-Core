package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import io.github.shrhang.shhs_create_core.content.registries.ShHsComponentTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;
import java.util.UUID;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;
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
        if (player.isCrouching()) {
            if (!isLookingAtAir(level, player)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide()) {
                int cleared = stack.getOrDefault(ShHsComponentTypes.PORTABLE_STOCK_TICKER_ADDRESSES,
                        PortableStockTickerAddresses.EMPTY).addresses().size();
                stack.remove(ShHsComponentTypes.PORTABLE_STOCK_TICKER_ADDRESSES);
                player.displayClientMessage(textComponent("portable_stock_ticker.addresses_cleared", cleared), true);
            }
            return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }

        InteractionResult result = tryToOpenMenu(player, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return new InteractionResultHolder<>(result, stack);
    }

    private static boolean isLookingAtAir(Level level, Player player) {
        double blockRange = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        HitResult blockHit = player.pick(blockRange, 0, false);
        if (blockHit.getType() != HitResult.Type.MISS) {
            return false;
        }

        double entityRange = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        Vec3 end = start.add(look.scale(entityRange));
        AABB bounds = player.getBoundingBox().expandTowards(look.scale(entityRange)).inflate(1);
        return ProjectileUtil.getEntityHitResult(level, player, start, end, bounds,
                entity -> entity != player && !entity.isSpectator() && entity.isPickable()) == null;
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isCrouching()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        if (blockEntity instanceof ClipboardBlockEntity clipboard) {
            if (!level.isClientSide()) {
                saveAddresses(player, context.getItemInHand(), clipboard);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!(blockEntity instanceof StockCheckingBlockEntity)
                && !(blockEntity instanceof PackagerLinkBlockEntity)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide() && linkTo(player, context.getItemInHand(), level, context.getClickedPos())) {
            player.displayClientMessage(CreateLang.translate("logistically_linked.tuned").component(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void saveAddresses(Player player, ItemStack stack, ClipboardBlockEntity clipboard) {
        PortableStockTickerAddresses found = PortableStockTickerAddresses.fromClipboard(clipboard.components());
        if (found.addresses().isEmpty()) {
            player.displayClientMessage(textComponent("portable_stock_ticker.no_clipboard_addresses"), true);
            return;
        }

        PortableStockTickerAddresses existing = stack.getOrDefault(
                ShHsComponentTypes.PORTABLE_STOCK_TICKER_ADDRESSES, PortableStockTickerAddresses.EMPTY);
        PortableStockTickerAddresses merged = existing.merge(found);
        int added = merged.addresses().size() - existing.addresses().size();
        stack.set(ShHsComponentTypes.PORTABLE_STOCK_TICKER_ADDRESSES, merged);
        player.displayClientMessage(textComponent("portable_stock_ticker.addresses_saved", added,
                merged.addresses().size()), true);
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

        PortableStockTickerAddresses addresses = stack.getOrDefault(
                ShHsComponentTypes.PORTABLE_STOCK_TICKER_ADDRESSES, PortableStockTickerAddresses.EMPTY);
        player.openMenu(new PortableStockTickerMenuProvider(networkId, addresses), buf -> {
            buf.writeUUID(networkId);
            PortableStockTickerAddresses.STREAM_CODEC.encode(buf, addresses);
        });
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

    public record PortableStockTickerMenuProvider(UUID networkId,
                                                  PortableStockTickerAddresses addresses) implements MenuProvider {

        @Override
        public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
            return PortableStockTickerMenu.create(containerId, playerInventory, networkId, addresses);
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
