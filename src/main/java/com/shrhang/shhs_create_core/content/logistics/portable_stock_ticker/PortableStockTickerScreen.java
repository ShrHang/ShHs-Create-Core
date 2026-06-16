package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PortableStockTickerScreen extends AbstractContainerScreen<PortableStockTickerMenu> {
    private static final AllGuiTextures HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final AllGuiTextures BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final AllGuiTextures FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;
    private static final int WIDTH = 226;
    private static final int HEIGHT = 196;
    private static final int ITEMS_PER_PAGE = 7;

    private EditBox searchBox;
    private EditBox addressBox;
    private final List<BigItemStack> orders = new ArrayList<>();
    private int scroll;

    public PortableStockTickerScreen(PortableStockTickerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        PacketDistributor.sendToServer(new PortableStockRequestPacket(menu.networkId));

        searchBox = new EditBox(font, leftPos + 71, topPos + 22, 100, 9, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search"));
        searchBox.setBordered(false);
        searchBox.setTextColor(0x4A2D31);
        addRenderableWidget(searchBox);

        addressBox = new EditBox(font, leftPos + 27, topPos + imageHeight - 36, 92, 10, Component.literal("Address"));
        addressBox.setHint(Component.literal("Package address"));
        addressBox.setBordered(false);
        addressBox.setTextColor(0x714A40);
        addRenderableWidget(addressBox);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot != null)
            snapshot.tick();
        if (snapshot == null || snapshot.ticksSinceLastUpdate() > 20)
            PacketDistributor.sendToServer(new PortableStockRequestPacket(menu.networkId));
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        HEADER.render(graphics, x - 15, y);
        y += HEADER.getHeight();
        int bodyCount = (imageHeight - HEADER.getHeight() - FOOTER.getHeight()) / BODY.getHeight();
        for (int i = 0; i < bodyCount; i++) {
            BODY.render(graphics, x - 15, y);
            y += BODY.getHeight();
        }
        FOOTER.render(graphics, x - 15, y);

        if (isSendButton(mouseX, mouseY))
            AllGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER.render(graphics, leftPos + 126, topPos + imageHeight - 27);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderItems(graphics);
        renderOrders(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.literal("Portable Stock Ticker").withStyle(ChatFormatting.DARK_GRAY), 18, 7, 0x4A2D31, false);
        graphics.drawString(font, Component.literal("Order").withStyle(ChatFormatting.DARK_GRAY), 160, 22, 0x4A2D31, false);
        graphics.drawString(font, Component.literal("Send").withStyle(ChatFormatting.WHITE), 155, imageHeight - 22, 0xffffff, false);
        if (addressBox != null && addressBox.getValue().isBlank() && !addressBox.isFocused())
            graphics.drawString(font, Component.literal("Package address").withStyle(ChatFormatting.ITALIC), 27, imageHeight - 36, 0xCDBCA8, false);
    }

    private void renderItems(GuiGraphics graphics) {
        List<BigItemStack> items = filteredItems();
        int start = Math.min(scroll, Math.max(0, items.size()));
        int end = Math.min(items.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            BigItemStack entry = items.get(i);
            int row = i - start;
            int x = leftPos + 27;
            int y = topPos + 42 + row * 18;
            AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, x - 1, y - 1);
            graphics.renderItem(entry.stack, x, y);
            graphics.drawString(font, entry.stack.getHoverName(), x + 22, y + 1, 0x4A2D31, false);
            graphics.drawString(font, "x" + entry.count, x + 22, y + 10, 0x714A40, false);
        }
    }

    private void renderOrders(GuiGraphics graphics) {
        for (int i = 0; i < Math.min(orders.size(), 7); i++) {
            BigItemStack entry = orders.get(i);
            int x = leftPos + 160;
            int y = topPos + 42 + i * 18;
            AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, x - 1, y - 1);
            graphics.renderItem(entry.stack, x, y);
            graphics.drawString(font, "x" + entry.count, x + 20, y + 5, 0x4A2D31, false);
        }
    }

    private List<BigItemStack> filteredItems() {
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot == null)
            return List.of();
        String filter = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        return snapshot.summary().getStacksByCount().stream()
                .filter(stack -> filter.isBlank()
                        || stack.stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing(stack -> stack.stack.getHoverName().getString()))
                .toList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isSendButton(mouseX, mouseY)) {
            sendOrder();
            return true;
        }

        int itemIndex = hoveredItemIndex(mouseX, mouseY);
        if (itemIndex != -1) {
            List<BigItemStack> items = filteredItems();
            if (itemIndex < items.size())
                addOrder(items.get(itemIndex), hasShiftDown() ? 10 : 1);
            return true;
        }

        int orderIndex = hoveredOrderIndex(mouseX, mouseY);
        if (orderIndex != -1 && orderIndex < orders.size()) {
            removeOrder(orderIndex, hasShiftDown() ? 10 : 1);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<BigItemStack> items = filteredItems();
        int maxScroll = Math.max(0, items.size() - ITEMS_PER_PAGE);
        scroll = Math.max(0, Math.min(maxScroll, scroll + (scrollY < 0 ? 1 : -1)));
        return true;
    }

    private int hoveredItemIndex(double mouseX, double mouseY) {
        if (mouseX < leftPos + 24 || mouseX > leftPos + 150 || mouseY < topPos + 40 || mouseY > topPos + 170)
            return -1;
        int row = ((int) mouseY - topPos - 42) / 18;
        return row < 0 || row >= ITEMS_PER_PAGE ? -1 : scroll + row;
    }

    private int hoveredOrderIndex(double mouseX, double mouseY) {
        if (mouseX < leftPos + 156 || mouseX > leftPos + 220 || mouseY < topPos + 40 || mouseY > topPos + 170)
            return -1;
        int row = ((int) mouseY - topPos - 42) / 18;
        return row < 0 || row >= 7 ? -1 : row;
    }

    private boolean isSendButton(double mouseX, double mouseY) {
        return mouseX >= leftPos + 126 && mouseX <= leftPos + 206 && mouseY >= topPos + imageHeight - 27 && mouseY <= topPos + imageHeight - 7;
    }

    private void addOrder(BigItemStack source, int amount) {
        for (BigItemStack order : orders) {
            if (ItemStack.isSameItemSameComponents(order.stack, source.stack)) {
                order.count = Math.min(source.count, order.count + amount);
                return;
            }
        }
        if (orders.size() >= 7)
            return;
        orders.add(new BigItemStack(source.stack.copyWithCount(1), Math.min(source.count, amount)));
    }

    private void removeOrder(int index, int amount) {
        BigItemStack order = orders.get(index);
        order.count -= amount;
        if (order.count <= 0)
            orders.remove(index);
    }

    private void sendOrder() {
        if (orders.isEmpty())
            return;
        PacketDistributor.sendToServer(new PortablePackageOrderRequestPacket(
                menu.networkId,
                PackageOrderWithCrafts.simple(BigItemStack.duplicateWrappers(orders)),
                addressBox.getValue()
        ));
        orders.clear();
        PacketDistributor.sendToServer(new PortableStockRequestPacket(menu.networkId));
    }
}
