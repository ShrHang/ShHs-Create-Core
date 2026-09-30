package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.fluidlogistics.terminal.FluidLogisticsTerminalClientCompat;
import io.github.shrhang.shhs_create_core.content.data.ShHsGuiTextures;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DimensionLogisticsTerminalScreen extends AbstractContainerScreen<DimensionLogisticsTerminalMenu> {
    private static final AllGuiTextures STOCK_HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final AllGuiTextures STOCK_BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final AllGuiTextures STOCK_FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;
    private static final AllGuiTextures STOCK_SLOT = AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT;
    private static final AllGuiTextures STOCK_SEARCH = AllGuiTextures.STOCK_KEEPER_REQUEST_SEARCH;
    private static final AllGuiTextures SEND_HOVER = AllGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER;
    private static final AllGuiTextures CATEGORY_SHOWN = AllGuiTextures.STOCK_KEEPER_CATEGORY_SHOWN;
    private static final AllGuiTextures CATEGORY_HIDDEN = AllGuiTextures.STOCK_KEEPER_CATEGORY_HIDDEN;
    private static final AllGuiTextures NUMBERS = AllGuiTextures.NUMBERS;

    private static final class Layout {
        static final int COLUMNS = 9, CELL = 20, STOCK_X = 24, CATEGORY_Y = 33, STOCK_WIDTH = 180, BASKET_SIZE = 9;
        static final int SEARCH_X = 71, SEARCH_Y = 22, SEARCH_WIDTH = 100, SEARCH_HEIGHT = 9;
        static final int DEPOSIT_X = 21, DEPOSIT_Y = 38, DEPOSIT_WIDTH = 194;
        static final int LEFT_MOUSE_X = 16, LEFT_MOUSE_WIDTH = 199, BASKET_MOUSE_HEIGHT = 29;
        static final int FOOTER_HEIGHT = 80, BASKET_FROM_BOTTOM = 72, SCROLL_TOP = 15, SCROLL_TRIM = 92;
        static final int SEND_X = 143, SEND_FROM_BOTTOM = 39, SEND_WIDTH = 78, SEND_HEIGHT = 18;
        static final int SEND_TEXTURE_X = 145, SEND_TEXTURE_FROM_BOTTOM = 41;
        static final int SEND_LABEL_CENTER_X = 184, SEND_LABEL_FROM_BOTTOM = 35;
        static final int TEXT_COLOR = 0x4a2d31, SWITCH_HEIGHT = 16;
        static final int INFO_X = 22, INFO_Y = 17, INFO_WIDTH = 94;
        static final int CANCEL_X = 118, CANCEL_Y = 17, CANCEL_SIZE = 9;
        static final int PREVIOUS_X = 131, PREVIOUS_Y = 13, NEXT_Y = 22, NAV_SIZE = 8;
        static final int READY_X = 144, INCOMPLETE_X = 160, BULK_Y = 15, BULK_WIDTH = 14, BULK_HEIGHT = 15;
        static final int ORDER_ITEM_X = 16, ORDER_ITEM_Y = 34, ORDER_ITEM_STEP = 18;
    }

    private final TerminalBasket basket;
    private final Set<UUID> collapsedCategories = new HashSet<>();
    private final LerpedFloat itemScroll = LerpedFloat.linear().startWithValue(0);
    private List<StockCategory> displayedCategories = List.of();
    private EditBox searchBox;
    private int basketOffset, orderIndex;
    private final int windowHeight;
    private final boolean compactLayout;
    private boolean rightPanelTab, scrollHandleActive;
    private boolean orderVisible, craftVisible;
    private UUID selectedOrder;
    private long seenRevision = -1;
    private String lastSearch = "";

    public DimensionLogisticsTerminalScreen(DimensionLogisticsTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        basket = new TerminalBasket(() -> menu.snapshots.current().stock());
        compactLayout = menu.compactLayout;
        windowHeight = menu.windowHeight;
        orderVisible = menu.orderCanShow;
        craftVisible = menu.modulesCanShare;
        imageWidth = compactLayout ? DimensionLogisticsTerminalLayout.LEFT_PANEL_WIDTH : DimensionLogisticsTerminalLayout.WINDOW_WIDTH;
        imageHeight = windowHeight;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(new NoShadowFontWrapper(font), leftPos + Layout.SEARCH_X, topPos + Layout.SEARCH_Y,
                Layout.SEARCH_WIDTH, Layout.SEARCH_HEIGHT, TerminalData.text("search"));
        searchBox.setBordered(false);
        searchBox.setTextColor(Layout.TEXT_COLOR);
        searchBox.setMaxLength(80);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(value -> {
            lastSearch = value;
            itemScroll.startWithValue(0);
            refreshSearchResults();
        });
        addWidget(searchBox);
        refreshSearchResults();
        refreshState();
    }

    private boolean leftPanelVisible() { return !compactLayout || !rightPanelTab; }
    private boolean rightPanelVisible() { return !compactLayout || rightPanelTab; }

    private void refreshSearchResults() {
        String query = lastSearch.toLowerCase(Locale.ROOT).strip();
        Map<UUID, List<TerminalStock.Entry>> grouped = new HashMap<>();
        for (TerminalStock.Entry entry : menu.snapshots.current().stock()) {
            if (!query.isEmpty()
                    && !entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    && !BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString().contains(query)) continue;
            grouped.computeIfAbsent(entry.network(), ignored -> new ArrayList<>()).add(entry);
        }
        List<UUID> sources = new ArrayList<>(grouped.keySet());
        sources.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        List<StockCategory> categories = new ArrayList<>(sources.size());
        for (UUID source : sources) {
            String address = source == null ? "" : menu.snapshots.current().addresses().getOrDefault(source, "");
            Component name = source == null ? TerminalData.text("dimension_category", menu.netId)
                    : address.isBlank() ? TerminalData.text("storage_category", shortNetwork(source))
                    : TerminalData.text("storage_category_address", shortNetwork(source), address);
            categories.add(new StockCategory(source, name, address, List.copyOf(grouped.get(source))));
        }
        displayedCategories = List.copyOf(categories);
        layoutCategories();
        clampItemScroll();
    }

    private void layoutCategories() {
        int y = 0;
        for (StockCategory category : displayedCategories) {
            category.y = y;
            y += Layout.CELL;
            if (!collapsedCategories.contains(category.network))
                y += Mth.ceil(category.entries.size() / (float) Layout.COLUMNS) * Layout.CELL;
        }
    }

    private int getMaxScroll() {
        int visibleHeight = windowHeight - 84;
        int totalRows = 2;
        for (StockCategory category : displayedCategories) {
            totalRows++;
            if (!collapsedCategories.contains(category.network))
                totalRows += Mth.ceil(category.entries.size() / (float) Layout.COLUMNS);
        }
        return Math.max(0, (int) ((totalRows * Layout.CELL - visibleHeight + 50) / (float) Layout.CELL));
    }

    private void clampItemScroll() {
        float value = Mth.clamp(itemScroll.getChaseTarget(), 0, getMaxScroll());
        if (value != itemScroll.getChaseTarget()) itemScroll.startWithValue(value);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.snapshots.current().revision() != seenRevision) {
            seenRevision = menu.snapshots.current().revision();
            basket.acknowledge(menu.snapshots.current().acceptedSubmission());
            refreshSearchResults();
        }
        itemScroll.tickChaser();
        if (Math.abs(itemScroll.getValue() - itemScroll.getChaseTarget()) < 1 / 16f)
            itemScroll.setValue(itemScroll.getChaseTarget());
        refreshState();
    }

    private TerminalData.OrderSummary currentOrder() {
        if (menu.snapshots.current().orders().isEmpty()) {
            selectedOrder = null;
            orderIndex = 0;
            return null;
        }
        if (selectedOrder != null)
            for (int i = 0; i < menu.snapshots.current().orders().size(); i++)
                if (menu.snapshots.current().orders().get(i).id().equals(selectedOrder)) { orderIndex = i; break; }
        orderIndex = Math.clamp(orderIndex, 0, menu.snapshots.current().orders().size() - 1);
        TerminalData.OrderSummary order = menu.snapshots.current().orders().get(orderIndex);
        selectedOrder = order.id();
        return order;
    }

    private void refreshState() {
        currentOrder();
        searchBox.visible = leftPanelVisible();
        menu.setClientSlotActivity(rightPanelVisible(), rightPanelVisible() && craftVisible);
    }

    private void requestOrderAction(int operation, boolean needsCurrent) {
        TerminalData.OrderSummary order = needsCurrent ? currentOrder() : null;
        if (needsCurrent && order == null) return;
        TerminalPackets.requestOrder(menu, operation, order == null ? null : order.id());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (rightPanelVisible()) renderRightPanel(graphics);
        if (leftPanelVisible()) {
            renderStockPanelBackground(graphics);
            renderStock(graphics, partialTick, mouseX, mouseY);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private void renderStockPanelBackground(GuiGraphics graphics) {
        int x = leftPos - 15, y = topPos;
        STOCK_HEADER.render(graphics, x, y);
        y += STOCK_HEADER.getHeight();
        int slices = Math.max(0, (windowHeight - STOCK_HEADER.getHeight() - STOCK_FOOTER.getHeight()) / STOCK_BODY.getHeight());
        for (int i = 0; i < slices; i++) {
            STOCK_BODY.render(graphics, x, y);
            y += STOCK_BODY.getHeight();
        }
        STOCK_FOOTER.render(graphics, x, y);
    }

    private void renderStock(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        float scroll = itemScroll.getValue(partialTick);
        renderStockEntries(graphics, scroll, partialTick, mouseX, mouseY);
        renderBasket(graphics);
        renderStockScrollbar(graphics, scroll);
        renderCollectButton(graphics, mouseX, mouseY);
    }

    private void renderStockEntries(GuiGraphics graphics, float scroll, float partialTick, int mouseX, int mouseY) {
        graphics.enableScissor(leftPos + 16, topPos + 17, leftPos + 215, topPos + windowHeight - Layout.FOOTER_HEIGHT);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll * Layout.CELL, 0);
        for (int sliceY = -2; sliceY < getMaxScroll() * Layout.CELL + windowHeight - 72;
             sliceY += AllGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight()) {
            float shownY = sliceY - scroll * Layout.CELL;
            if (shownY >= -Layout.CELL && shownY <= windowHeight - 72)
                AllGuiTextures.STOCK_KEEPER_REQUEST_BG.render(graphics, leftPos + 22, topPos + sliceY + 18);
        }
        STOCK_SEARCH.render(graphics, leftPos + 42, searchBox.getY() - 5);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        if (searchBox.getValue().isBlank() && !searchBox.isFocused()) {
            Component message = searchBox.getMessage();
            graphics.drawString(font, message, leftPos + DimensionLogisticsTerminalLayout.LEFT_PANEL_WIDTH / 2
                    - font.width(message) / 2, searchBox.getY(), Layout.TEXT_COLOR, false);
        }
        for (StockCategory category : displayedCategories) {
            int categoryY = Layout.CATEGORY_Y + category.y;
            AllGuiTextures categoryTexture = collapsedCategories.contains(category.network) ? CATEGORY_HIDDEN : CATEGORY_SHOWN;
            categoryTexture.render(graphics, leftPos + Layout.STOCK_X, topPos + categoryY + 6);
            drawCategoryName(graphics, category.name, leftPos + Layout.STOCK_X + 9, topPos + categoryY + 7,
                    Layout.STOCK_WIDTH - 9);
            if (collapsedCategories.contains(category.network)) continue;
            for (int index = 0; index < category.entries.size(); index++) {
                int itemY = categoryY + Layout.CELL + index / Layout.COLUMNS * Layout.CELL;
                float shownY = itemY - scroll * Layout.CELL;
                if (shownY < 0) continue;
                if (shownY > windowHeight - 72) break;
                TerminalStock.Entry entry = category.entries.get(index);
                renderEntry(graphics, entry.stack(), entry.amount(),
                        Layout.STOCK_X + index % Layout.COLUMNS * Layout.CELL,
                        itemY, entry.network() != null, entry.network() != null && !entry.requestable(), true);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private void drawCategoryName(GuiGraphics graphics, Component name, int x, int y, int width) {
        Component fitted = name;
        if (font.width(name) > width) {
            String ellipsis = "…";
            fitted = Component.literal(font.plainSubstrByWidth(name.getString(),
                    Math.max(0, width - font.width(ellipsis))) + ellipsis);
        }
        graphics.drawString(font, fitted, x + 1, y + 1, 0x4a2d31, false);
        graphics.drawString(font, fitted, x, y, 0xf8f8ec, false);
    }

    private String shortNetwork(UUID network) {
        return network.toString().substring(0, 8);
    }

    private List<Map.Entry<TerminalData.Selection, Integer>> basketEntries() { return basket.entries(); }
    private int basketY() { return topPos + windowHeight - Layout.BASKET_FROM_BOTTOM; }

    private void renderBasket(GuiGraphics graphics) {
        List<Map.Entry<TerminalData.Selection, Integer>> entries = basketEntries();
        basketOffset = Math.clamp(basketOffset, 0, Math.max(0, entries.size() - Layout.BASKET_SIZE));
        for (int slot = 0; slot < Layout.BASKET_SIZE && slot + basketOffset < entries.size(); slot++) {
            var value = entries.get(slot + basketOffset);
            TerminalStock.Entry stock = basket.lookup(value.getKey());
            renderEntry(graphics, value.getKey().key().copyStackWithCount(1), value.getValue(),
                    Layout.STOCK_X + slot * Layout.CELL, basketY() - topPos, true,
                    stock == null || !stock.requestable() || stock.amount() < value.getValue(), false);
        }
    }

    private void renderStockScrollbar(GuiGraphics graphics, float scroll) {
        int maxScroll = getMaxScroll();
        if (maxScroll == 0) return;
        int visibleHeight = windowHeight - Layout.SCROLL_TRIM;
        int totalHeight = maxScroll * Layout.CELL + visibleHeight;
        int barSize = Math.max(5, Mth.floor((float) visibleHeight / totalHeight * (visibleHeight - 2)));
        int barX = leftPos + Layout.STOCK_X + Layout.STOCK_WIDTH;
        int barY = topPos + Layout.SCROLL_TOP + (int) ((scroll * Layout.CELL) / totalHeight * (visibleHeight - 2));
        AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
        graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize, pad.getStartX(), pad.getStartY(),
                pad.getWidth(), pad.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
        if (barSize > 16) AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
    }

    private boolean isCollectHovered(double mouseX, double mouseY) {
        return insideLeft(mouseX, mouseY, Layout.SEND_X, windowHeight - Layout.SEND_FROM_BOTTOM,
                Layout.SEND_WIDTH, Layout.SEND_HEIGHT);
    }

    private void renderCollectButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean active = basket.valid();
        if (active && isCollectHovered(mouseX, mouseY)) SEND_HOVER.render(graphics,
                leftPos + Layout.SEND_TEXTURE_X, topPos + windowHeight - Layout.SEND_TEXTURE_FROM_BOTTOM);
        Component label = TerminalData.text("collect");
        graphics.drawString(font, label, leftPos + Layout.SEND_LABEL_CENTER_X - font.width(label) / 2,
                topPos + windowHeight - Layout.SEND_LABEL_FROM_BOTTOM, active ? 0x252525 : 0x8a7d73, false);
    }

    private void renderRightPanel(GuiGraphics graphics) {
        int x = leftPos + menu.rightPanelX;
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_HEADER.render(graphics, x, topPos);
        ShHsGuiTextures background = ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BACKGROUND;
        for (int y = DimensionLogisticsTerminalLayout.HEADER_TEXTURE_HEIGHT;
             y < menu.bottomPanelY; y += background.getHeight()) {
            int h = Math.min(background.getHeight(), menu.bottomPanelY - y);
            graphics.blit(background.location, x, topPos + y, background.getWidth(), h,
                    background.getStartX(), background.getStartY(), background.getWidth(), h, background.getTextureWidth(), background.getTextureHeight());
        }
        if (orderVisible) ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_ORDER.render(graphics, x, topPos + menu.orderPanelY);
        if (craftVisible) ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_CRAFT.render(graphics, x, topPos + menu.craftPanelY);
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_PLAYER.render(graphics, x, topPos + menu.playerPanelY);
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BOTTOM.render(graphics, x, topPos + menu.bottomPanelY);
        if (orderVisible) renderOrder(graphics, x, topPos + menu.orderPanelY);
    }

    private void renderOrder(GuiGraphics graphics, int x, int y) {
        TerminalData.OrderSummary order = currentOrder();
        if (order == null) {
            drawFitted(graphics, TerminalData.text("no_orders"), x + Layout.INFO_X, y + Layout.INFO_Y, Layout.INFO_WIDTH);
            return;
        }
        Component status = TerminalData.text("order_status", orderIndex + 1, menu.snapshots.current().orders().size(),
                TerminalData.text(order.ready() ? "ready_short" : "waiting_short"), order.packages());
        drawFitted(graphics, status, x + Layout.INFO_X, y + Layout.INFO_Y, Layout.INFO_WIDTH);
        for (int i = 0; i < Math.min(TerminalData.MAX_ORDER_LINES, order.lines().size()); i++) {
            TerminalData.OrderSummary.Line line = order.lines().get(i);
            int itemX = x + Layout.ORDER_ITEM_X + i * Layout.ORDER_ITEM_STEP;
            int itemY = y + Layout.ORDER_ITEM_Y;
            graphics.renderItem(line.stack(), itemX + 1, itemY + 1);
            renderProgressCount(graphics, line.delivered(), line.requested(), itemX, itemY);
        }
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width) {
        float scale = Math.min(1, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 100);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, Layout.TEXT_COLOR, false);
        graphics.pose().popPose();
    }

    private String compactCount(long count) {
        if (count >= 1_000_000) return count / 1_000_000 + "m";
        if (count >= 10_000) return count / 1_000 + "k";
        if (count >= 1_000) {
            long tenths = count / 100;
            return tenths % 10 == 0 ? tenths / 10 + "k" : tenths / 10 + "." + tenths % 10 + "k";
        }
        return Long.toString(count);
    }

    private int numberWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray())
            width += c == ' ' ? 4 : c == '.' ? 2 : c == 'm' ? 6 : c == '+' ? 8 : 4;
        return width;
    }

    private String itemCountText(long count) {
        if (count >= 1_000_000) return count / 1_000_000 + "m";
        if (count >= 10_000) return count / 1_000 + "k";
        if (count >= 1_000) return count / 1_000 + "." + count % 1_000 / 100 + "k";
        return count >= 100 ? Long.toString(count) : " " + count;
    }

    private void renderProgressCount(GuiGraphics graphics, long delivered, long requested, int x, int y) {
        String left = compactCount(delivered), right = compactCount(requested);
        int slash = font.width("/"), width = numberWidth(left) + slash + numberWidth(right);
        float scale = Math.min(1, 17f / width);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 18 - width * scale, y + 10, 220);
        graphics.pose().scale(scale, scale, 1);
        int cursor = drawNumberText(graphics, left, 0);
        graphics.drawString(font, "/", cursor, 0, 0xffffff, true);
        drawNumberText(graphics, right, cursor + slash);
        graphics.pose().popPose();
    }

    private int drawNumberText(GuiGraphics graphics, String text, int x) {
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                x += 4;
                continue;
            }
            int offset = (c - '0') * 6, width = NUMBERS.getWidth();
            if (c == '.') { width = 3; offset = 60; }
            else if (c == 'k') offset = 64;
            else if (c == 'm') { width = 7; offset = 70; }
            else if (c == '+') { width = 9; offset = 84; }
            RenderSystem.enableBlend();
            graphics.blit(NUMBERS.location, x, 0, 0, NUMBERS.getStartX() + offset, NUMBERS.getStartY(),
                    width, NUMBERS.getHeight(), 256, 256);
            x += width - 1;
        }
        return x;
    }

    private void renderItemCount(GuiGraphics graphics, ItemStack stack, long amount, boolean external, int x, int y) {
        if (amount <= 1) return;
        boolean infinite = external && amount >= BigItemStack.INF;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 200);
        boolean custom = Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogisticsTerminalClientCompat.renderAmount(graphics, stack, amount, infinite)).orElse(false);
        graphics.pose().popPose();
        if (custom) return;
        String text = infinite ? "+" : itemCountText(amount);
        int offset = (int) Math.floor(-text.length() * 2.5);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 14 + offset, y + 10, 200);
        drawNumberText(graphics, text, 0);
        graphics.pose().popPose();
    }

    private void renderEntry(GuiGraphics graphics, ItemStack stack, long amount, int x, int y,
                             boolean external, boolean unavailable, boolean renderSlot) {
        x += leftPos; y += topPos;
        if (renderSlot) STOCK_SLOT.render(graphics, x, y);
        graphics.renderItem(stack, x + 1, y + 1);
        if (external) graphics.fill(x, y, x + 4, y + 3, 0xff52bbd0);
        if (unavailable) graphics.fill(x, y, x + 18, y + 18, 0x88ad3030);
        renderItemCount(graphics, stack, amount, external, x, y);
    }

    private HoveredEntry hoveredStock(int mouseX, int mouseY) {
        if (!leftPanelVisible()) return null;
        int x = mouseX - leftPos - Layout.STOCK_X, y = mouseY - topPos;
        if (x < 0 || x >= Layout.STOCK_WIDTH) return null;
        if (y >= 16 && y <= windowHeight - Layout.FOOTER_HEIGHT && itemScroll.settled()) {
            float contentY = y - Layout.CATEGORY_Y + itemScroll.getChaseTarget() * Layout.CELL;
            for (StockCategory category : displayedCategories) {
                if (collapsedCategories.contains(category.network)) continue;
                int rows = Mth.ceil(category.entries.size() / (float) Layout.COLUMNS);
                float itemsY = category.y + Layout.CELL;
                if (contentY < itemsY || contentY >= itemsY + rows * Layout.CELL) continue;
                int row = Mth.floor((contentY - itemsY) / Layout.CELL);
                int index = row * Layout.COLUMNS + x / Layout.CELL;
                if (index < category.entries.size())
                    return new HoveredEntry(category.entries.get(index), HoveredArea.STOCK);
            }
        }
        int selectedY = basketY() - topPos;
        if (y >= selectedY && y < selectedY + Layout.CELL) {
            List<Map.Entry<TerminalData.Selection, Integer>> list = basketEntries();
            int index = basketOffset + x / Layout.CELL;
            if (index < list.size()) {
                var value = list.get(index);
                TerminalStock.Entry stock = basket.lookup(value.getKey());
                return new HoveredEntry(new TerminalStock.Entry(value.getKey().key().copyStackWithCount(1),
                        value.getValue(), value.getKey().network(), stock != null && stock.requestable()), HoveredArea.BASKET);
            }
        }
        return null;
    }

    private StockCategory hoveredCategory(double mouseX, double mouseY) {
        if (!leftPanelVisible() || !itemScroll.settled()
                || !insideLeft(mouseX, mouseY, Layout.STOCK_X, 16,
                Layout.STOCK_WIDTH, windowHeight - Layout.FOOTER_HEIGHT - 16)) return null;
        float contentY = (float) mouseY - topPos - Layout.CATEGORY_Y
                + itemScroll.getChaseTarget() * Layout.CELL;
        for (StockCategory category : displayedCategories)
            if (contentY >= category.y && contentY < category.y + Layout.CELL) return category;
        return null;
    }

    private TerminalData.OrderSummary.Line hoveredOrderLine(double mouseX, double mouseY) {
        if (!rightPanelVisible() || !orderVisible) return null;
        TerminalData.OrderSummary order = currentOrder();
        if (order == null) return null;
        int x = (int) mouseX - leftPos - menu.rightPanelX - Layout.ORDER_ITEM_X;
        int y = (int) mouseY - topPos - menu.orderPanelY - Layout.ORDER_ITEM_Y;
        if (x < 0 || y < 0 || y >= 18) return null;
        int index = x / Layout.ORDER_ITEM_STEP;
        return index < Math.min(TerminalData.MAX_ORDER_LINES, order.lines().size())
                && x % Layout.ORDER_ITEM_STEP < 18 ? order.lines().get(index) : null;
    }

    public Optional<Map.Entry<ItemStack, Rect2i>> hoveredIngredient(double mouseX, double mouseY) {
        HoveredEntry stock = hoveredStock((int) mouseX, (int) mouseY);
        if (stock != null) return Optional.of(Map.entry(stock.entry().stack(), new Rect2i((int) mouseX - 8, (int) mouseY - 8, 16, 16)));
        TerminalData.OrderSummary.Line line = hoveredOrderLine(mouseX, mouseY);
        return line == null ? Optional.empty() : Optional.of(Map.entry(line.stack(), new Rect2i((int) mouseX - 8, (int) mouseY - 8, 16, 16)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderCustomTooltip(graphics, mouseX, mouseY);
        if (compactLayout) {
            Component label = TerminalData.text("switch");
            graphics.drawString(font, label, leftPos + (imageWidth - font.width(label)) / 2, topPos + 4, 0x603d39, false);
        }
    }

    private void renderCustomTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) return;
        HoveredEntry hovered = hoveredStock(mouseX, mouseY);
        if (hovered != null) {
            TerminalStock.Entry entry = hovered.entry();
            List<Component> tooltip = stockTooltip(entry);
            tooltip.add(entry.network() == null ? TerminalData.text("local") : TerminalData.text("external", entry.network().toString().substring(0, 8)));
            if (entry.network() != null && !entry.requestable()) tooltip.add(TerminalData.text("unavailable"));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        StockCategory category = hoveredCategory(mouseX, mouseY);
        if (category != null && category.network != null && hasShiftDown()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(TerminalData.text("external", category.network.toString()));
            if (!category.address.isBlank()) tooltip.add(TerminalData.text("address_value", category.address));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        TerminalData.OrderSummary.Line line = hoveredOrderLine(mouseX, mouseY);
        if (line != null) {
            List<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, line.stack()));
            tooltip.add(TerminalData.text("delivery_progress", line.delivered(), line.requested()));
            tooltip.add(TerminalData.text("external", line.network().toString().substring(0, 8)));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        Component action = hoveredActionTooltip(mouseX, mouseY);
        if (action != null) graphics.renderTooltip(font, action, mouseX, mouseY);
    }

    private List<Component> stockTooltip(TerminalStock.Entry entry) {
        List<Component> resource = Mods.FLUIDLOGISTICS.runIfInstalled(() -> () ->
                FluidLogisticsTerminalClientCompat.inventoryTooltip(entry.stack(), entry.amount(),
                        entry.network() != null && entry.amount() >= BigItemStack.INF,
                        minecraft.options.advancedItemTooltips).orElse(List.of())).orElse(List.of());
        return resource.isEmpty() ? new ArrayList<>(getTooltipFromItem(minecraft, entry.stack()))
                : new ArrayList<>(resource);
    }

    private Component hoveredActionTooltip(double mouseX, double mouseY) {
        if (!rightPanelVisible()) return null;
        int toggleY = menu.bottomPanelY + DimensionLogisticsTerminalLayout.MODULE_TOGGLE_Y;
        if (insideRight(mouseX, mouseY, DimensionLogisticsTerminalLayout.ORDER_TOGGLE_X, toggleY, 16, 16))
            return TerminalData.text(orderVisible ? "hide_orders" : "show_orders");
        if (insideRight(mouseX, mouseY, DimensionLogisticsTerminalLayout.CRAFT_TOGGLE_X, toggleY, 16, 16))
            return TerminalData.text(craftVisible ? "hide_crafting" : "show_crafting");
        if (!orderVisible) return null;
        int y = menu.orderPanelY;
        if (insideRight(mouseX, mouseY, Layout.CANCEL_X, y + Layout.CANCEL_Y, 9, 9)) return TerminalData.text("cancel_current");
        if (insideRight(mouseX, mouseY, Layout.PREVIOUS_X, y + Layout.PREVIOUS_Y, 8, 8)) return TerminalData.text("previous_order");
        if (insideRight(mouseX, mouseY, Layout.PREVIOUS_X, y + Layout.NEXT_Y, 8, 8)) return TerminalData.text("next_order");
        if (insideRight(mouseX, mouseY, Layout.READY_X, y + Layout.BULK_Y, 14, 15)) return TerminalData.text("claim_ready");
        if (insideRight(mouseX, mouseY, Layout.INCOMPLETE_X, y + Layout.BULK_Y, 14, 15)) return TerminalData.text("cancel_incomplete");
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int switchWidth = font.width(TerminalData.text("switch"));
        int switchX = leftPos + (imageWidth - switchWidth) / 2;
        if (compactLayout && button == 0 && mouseX >= switchX - 4 && mouseX < switchX + switchWidth + 4
                && mouseY >= topPos && mouseY < topPos + Layout.SWITCH_HEIGHT) {
            rightPanelTab = !rightPanelTab;
            refreshState();
            return true;
        }
        if (leftPanelVisible() && button == 1 && searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setValue(""); searchBox.setFocused(true); itemScroll.startWithValue(0); return true;
        }
        if (button == 0 && handleModuleToggle(mouseX, mouseY)) return true;
        if (button == 0 && handleOrderAction(mouseX, mouseY)) return true;
        if (leftPanelVisible() && button == 0 && isCollectHovered(mouseX, mouseY) && basket.valid()) { basket.submit(menu); return true; }
        if (leftPanelVisible() && button == 0 && beginScrollbarDrag(mouseX, mouseY)) return true;
        if (button == 0 && handleCategoryToggle(mouseX, mouseY)) return true;
        HoveredEntry hovered = hoveredStock((int) mouseX, (int) mouseY);
        if (hovered != null && (button == 0 || button == 1)) {
            TerminalStock.Entry entry = hovered.entry();
            if (hovered.area() == HoveredArea.STOCK && entry.network() == null && !entry.requestable()) return true;
            TerminalData.Selection selection = new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network());
            if (hovered.area() == HoveredArea.STOCK && entry.network() == null) {
                TerminalPackets.requestLocalClick(menu, entry.stack(), button, hasShiftDown());
            } else if (entry.network() != null) {
                int delta = hasShiftDown() ? entry.stack().getMaxStackSize() : 1;
                if (hovered.area() == HoveredArea.BASKET || button == 1) delta = -delta;
                basket.change(selection, delta);
            }
            return true;
        }
        if (handleDeposit(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleCategoryToggle(double mouseX, double mouseY) {
        StockCategory category = hoveredCategory(mouseX, mouseY);
        if (category == null) return false;
        if (!collapsedCategories.add(category.network)) collapsedCategories.remove(category.network);
        layoutCategories();
        clampItemScroll();
        return true;
    }

    private boolean handleModuleToggle(double mouseX, double mouseY) {
        if (!rightPanelVisible()) return false;
        int y = menu.bottomPanelY + DimensionLogisticsTerminalLayout.MODULE_TOGGLE_Y;
        if (insideRight(mouseX, mouseY, DimensionLogisticsTerminalLayout.ORDER_TOGGLE_X, y, 16, 16)) {
            if (menu.orderCanShow) { orderVisible = !orderVisible; if (orderVisible && !menu.modulesCanShare) craftVisible = false; refreshState(); }
            return true;
        }
        if (insideRight(mouseX, mouseY, DimensionLogisticsTerminalLayout.CRAFT_TOGGLE_X, y, 16, 16)) {
            if (menu.craftCanShow) { craftVisible = !craftVisible; if (craftVisible && !menu.modulesCanShare) orderVisible = false; refreshState(); }
            return true;
        }
        return false;
    }

    private boolean handleOrderAction(double mouseX, double mouseY) {
        if (!rightPanelVisible() || !orderVisible) return false;
        int y = menu.orderPanelY;
        if (insideRight(mouseX, mouseY, Layout.CANCEL_X, y + Layout.CANCEL_Y, 9, 9)) { requestOrderAction(TerminalPackets.END, true); return true; }
        if (insideRight(mouseX, mouseY, Layout.PREVIOUS_X, y + Layout.PREVIOUS_Y, 8, 8)) { navigateOrder(-1); return true; }
        if (insideRight(mouseX, mouseY, Layout.PREVIOUS_X, y + Layout.NEXT_Y, 8, 8)) { navigateOrder(1); return true; }
        if (insideRight(mouseX, mouseY, Layout.READY_X, y + Layout.BULK_Y, 14, 15)) { requestOrderAction(TerminalPackets.CLAIM_READY, false); return true; }
        if (insideRight(mouseX, mouseY, Layout.INCOMPLETE_X, y + Layout.BULK_Y, 14, 15)) { requestOrderAction(TerminalPackets.END_INCOMPLETE, false); return true; }
        return false;
    }

    private void navigateOrder(int direction) {
        if (menu.snapshots.current().orders().isEmpty()) return;
        orderIndex = Math.floorMod(orderIndex + direction, menu.snapshots.current().orders().size());
        selectedOrder = menu.snapshots.current().orders().get(orderIndex).id();
    }

    private boolean handleDeposit(double mouseX, double mouseY) {
        if (!leftPanelVisible() || menu.getCarried().isEmpty() || !insideLeft(mouseX, mouseY, Layout.DEPOSIT_X,
                Layout.DEPOSIT_Y, Layout.DEPOSIT_WIDTH, windowHeight - Layout.DEPOSIT_Y - 93)) return false;
        TerminalPackets.requestDeposit(menu);
        return true;
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        if (getMaxScroll() == 0) return false;
        int x = leftPos + Layout.STOCK_X + Layout.STOCK_WIDTH;
        if (mouseX <= x || mouseX > x + 8 || mouseY <= topPos + Layout.SCROLL_TOP || mouseY >= topPos + windowHeight - 82) return false;
        scrollHandleActive = true; updateScrollFromMouse(mouseY); return true;
    }

    private void updateScrollFromMouse(double mouseY) {
        int visibleHeight = windowHeight - Layout.SCROLL_TRIM;
        int totalHeight = getMaxScroll() * Layout.CELL + visibleHeight;
        int barSize = Math.max(5, Mth.floor((float) visibleHeight / totalHeight * (visibleHeight - 2)));
        double target = (mouseY - topPos - Layout.SCROLL_TOP - barSize / 2.0) * totalHeight
                / (visibleHeight - 2) / Layout.CELL;
        itemScroll.chase(Mth.clamp(target, 0, getMaxScroll()), 0.8, Chaser.EXP);
    }

    private boolean insideLeft(double mx, double my, int x, int y, int width, int height) {
        return mx >= leftPos + x && mx < leftPos + x + width && my >= topPos + y && my < topPos + y + height;
    }

    private boolean insideRight(double mx, double my, int x, int y, int width, int height) {
        return mx >= leftPos + menu.rightPanelX + x && mx < leftPos + menu.rightPanelX + x + width
                && my >= topPos + y && my < topPos + y + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (leftPanelVisible() && mouseX >= leftPos + Layout.LEFT_MOUSE_X
                && mouseX <= leftPos + Layout.LEFT_MOUSE_X + Layout.LEFT_MOUSE_WIDTH) {
            int direction = (int) Math.signum(dy);
            if (mouseY >= basketY() - 4 && mouseY <= basketY() - 4 + Layout.BASKET_MOUSE_HEIGHT) {
                HoveredEntry hovered = hoveredStock((int) mouseX, (int) mouseY);
                if (hasControlDown() && hovered != null && hovered.entry().network() != null) {
                    TerminalStock.Entry entry = hovered.entry();
                    basket.change(new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network()), direction);
                } else basketOffset = Math.clamp(basketOffset - direction, 0, Math.max(0, basket.size() - Layout.BASKET_SIZE));
                return true;
            }
            float target = Mth.clamp(itemScroll.getChaseTarget() + (float) Math.ceil(Math.abs(dy))
                    * (float) -Math.signum(dy), 0, getMaxScroll());
            itemScroll.chase(target, 0.5, Chaser.EXP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollHandleActive) { scrollHandleActive = false; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollHandleActive) { updateScrollFromMouse(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 && hasShiftDown() && leftPanelVisible() && basket.valid()) { basket.submit(menu); return true; }
        if (searchBox.isFocused() && key != 256) return searchBox.keyPressed(key, scan, modifiers) || searchBox.canConsumeInput();
        return super.keyPressed(key, scan, modifiers);
    }

    private enum HoveredArea { STOCK, BASKET }
    private record HoveredEntry(TerminalStock.Entry entry, HoveredArea area) {}
    private static final class StockCategory {
        private final UUID network;
        private final Component name;
        private final String address;
        private final List<TerminalStock.Entry> entries;
        private int y;

        private StockCategory(UUID network, Component name, String address, List<TerminalStock.Entry> entries) {
            this.network = network;
            this.name = name;
            this.address = address;
            this.entries = entries;
        }
    }
}
