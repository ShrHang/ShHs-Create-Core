package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.content.data.ShHsGuiTextures;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class DimensionLogisticsTerminalScreen extends AbstractContainerScreen<DimensionLogisticsTerminalMenu> {
    private static final AllGuiTextures STOCK_HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final AllGuiTextures STOCK_BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final AllGuiTextures STOCK_FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;
    private static final AllGuiTextures STOCK_SLOT = AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT;
    private static final AllGuiTextures STOCK_SEARCH = AllGuiTextures.STOCK_KEEPER_REQUEST_SEARCH;
    private static final AllGuiTextures SEND_HOVER = AllGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER;

    private static final class Layout {
        private static final int STOCK_COLUMNS = 9;
        private static final int CELL_SIZE = 20;
        private static final int STOCK_X = 24;
        private static final int STOCK_Y = 37;
        private static final int STOCK_WIDTH = STOCK_COLUMNS * CELL_SIZE;
        private static final int BASKET_SIZE = 9;

        private static final int SEARCH_X = 71;
        private static final int SEARCH_Y = 22;
        private static final int SEARCH_WIDTH = 100;
        private static final int SEARCH_HEIGHT = 9;
        private static final int MODE_X = 30;
        private static final int ACTION_WIDTH = 99;
        private static final int ACTION_HEIGHT = 20;
        private static final int CLAIM_X = 32;
        private static final int CLAIM_WIDTH = 74;
        private static final int END_X = 110;
        private static final int END_WIDTH = 122;

        private static final int ORDER_NAV_X = 32;
        private static final int ORDER_NAV_Y = 38;
        private static final int ORDER_NAV_WIDTH = 198;
        private static final int ORDER_NAV_HEIGHT = 20;
        private static final int ORDER_NAV_MIDDLE_X = 126;
        private static final int DEPOSIT_X = 21;
        private static final int DEPOSIT_Y = 38;
        private static final int DEPOSIT_WIDTH = 194;

        private static final int LEFT_MOUSE_X = 16;
        private static final int LEFT_MOUSE_WIDTH = 199;
        private static final int BASKET_MOUSE_HEIGHT = 29;

        private static final int FOOTER_HEIGHT = 80;
        private static final int BASKET_FROM_BOTTOM = 72;
        private static final int ACTION_FROM_BOTTOM = 36;
        private static final int ORDER_ACTION_FROM_BOTTOM = 64;
        private static final int SCROLL_TOP = 15;
        private static final int SCROLL_WINDOW_TRIM = 92;
        private static final int SEND_X = 143;
        private static final int SEND_FROM_BOTTOM = 39;
        private static final int SEND_WIDTH = 78;
        private static final int SEND_HEIGHT = 18;
        private static final int SEND_TEXTURE_X = 145;
        private static final int SEND_TEXTURE_FROM_BOTTOM = 41;
        private static final int SEND_LABEL_CENTER_X = 184;
        private static final int SEND_LABEL_FROM_BOTTOM = 35;

        private static final int ORDER_TITLE_X = 70;
        private static final int ORDER_TITLE_Y = 21;
        private static final int ORDER_PAGE_X = 76;
        private static final int ORDER_PAGE_Y = 44;
        private static final int ORDER_TEXT_X = 34;
        private static final int ORDER_STATUS_Y = 63;
        private static final int ORDER_PROGRESS_Y = 79;
        private static final int ORDER_MISSING_LABEL_Y = 97;
        private static final int ORDER_MISSING_Y = 113;
        private static final int ORDER_MISSING_ROWS = 3;
        private static final int TEXT_COLOR = 0x4A2D31;
    }

    private final LinkedHashMap<TerminalData.Selection, Integer> basket = new LinkedHashMap<>();
    private final LerpedFloat itemScroll = LerpedFloat.linear().startWithValue(0);
    private List<TerminalStock.Entry> displayedItems = List.of();
    private EditBox searchBox;
    private Button viewButton;
    private Button claimButton;
    private Button endButton;
    private int basketOffset, orderIndex, missingRow;
    private int windowHeight;
    private float uiScale = 1;
    private boolean showingOrders;
    private boolean scrollHandleActive;
    private UUID submission;
    private UUID selectedOrder;
    private long seenRevision = -1;
    private String lastSearch = "";

    public DimensionLogisticsTerminalScreen(DimensionLogisticsTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = DimensionLogisticsTerminalLayout.WINDOW_WIDTH;
        imageHeight = DimensionLogisticsTerminalLayout.MIN_WINDOW_HEIGHT;
    }

    @Override
    protected void init() {
        initializeScale();
        super.init();
        initWidgets();
        refreshSearchResults();
        refreshWidgetState();
    }

    private void initializeScale() {
        // Slot hit-testing, widgets and rendering share this logical coordinate system.
        uiScale = Math.min(1f, Math.min((width - 12f) / imageWidth,
                (height - 12f) / DimensionLogisticsTerminalLayout.MIN_WINDOW_HEIGHT));
        width = (int) (width / uiScale);
        height = (int) (height / uiScale);
        int appropriateHeight = height - 10;
        appropriateHeight -= Mth.positiveModulo(
                appropriateHeight - STOCK_HEADER.getHeight() - STOCK_FOOTER.getHeight(),
                STOCK_BODY.getHeight());
        int maximumHeight = STOCK_HEADER.getHeight() + STOCK_FOOTER.getHeight()
                + STOCK_BODY.getHeight() * 17;
        windowHeight = Math.clamp(appropriateHeight,
                DimensionLogisticsTerminalLayout.MIN_WINDOW_HEIGHT, maximumHeight);
        imageHeight = windowHeight;
    }

    private void initWidgets() {
        searchBox = new EditBox(new NoShadowFontWrapper(font),
                leftPos + Layout.SEARCH_X, topPos + Layout.SEARCH_Y,
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
        viewButton = addRenderableWidget(Button.builder(TerminalData.text("orders"), ignored -> {
            showingOrders = !showingOrders;
            refreshWidgetState();
        }).bounds(leftPos + Layout.MODE_X, topPos + windowHeight - Layout.ACTION_FROM_BOTTOM,
                Layout.ACTION_WIDTH, Layout.ACTION_HEIGHT).build());
        claimButton = addRenderableWidget(Button.builder(TerminalData.text("claim"), ignored -> orderAction(TerminalPackets.CLAIM))
                .bounds(leftPos + Layout.CLAIM_X, topPos + windowHeight - Layout.ORDER_ACTION_FROM_BOTTOM,
                        Layout.CLAIM_WIDTH, Layout.ACTION_HEIGHT).build());
        endButton = addRenderableWidget(Button.builder(TerminalData.text("end"), ignored -> orderAction(TerminalPackets.END))
                .bounds(leftPos + Layout.END_X, topPos + windowHeight - Layout.ORDER_ACTION_FROM_BOTTOM,
                        Layout.END_WIDTH, Layout.ACTION_HEIGHT).build());
    }

    private void refreshSearchResults() {
        String query = lastSearch.toLowerCase(Locale.ROOT).strip();
        displayedItems = menu.clientStock.stream().filter(entry -> query.isEmpty()
                || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString().contains(query)).toList();
        clampItemScroll();
    }

    private int getMaxScroll() {
        int visibleHeight = windowHeight - 84;
        int totalRows = 2 + Mth.ceil(displayedItems.size() / (float) Layout.STOCK_COLUMNS);
        return Math.max(0, (int) ((totalRows * Layout.CELL_SIZE - visibleHeight + 50)
                / (float) Layout.CELL_SIZE));
    }

    private void clampItemScroll() {
        float clamped = Mth.clamp(itemScroll.getChaseTarget(), 0, getMaxScroll());
        if (clamped != itemScroll.getChaseTarget()) itemScroll.startWithValue(clamped);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.clientRevision != seenRevision) {
            seenRevision = menu.clientRevision;
            if (submission != null && submission.equals(menu.acceptedSubmission)) {
                basket.clear(); submission = null;
            }
            refreshSearchResults();
        }
        itemScroll.tickChaser();
        if (Math.abs(itemScroll.getValue() - itemScroll.getChaseTarget()) < 1 / 16f)
            itemScroll.setValue(itemScroll.getChaseTarget());
        refreshWidgetState();
    }

    private CompoundTag currentOrder() {
        if (menu.clientOrders.isEmpty()) {
            selectedOrder = null;
            return new CompoundTag();
        }
        if (selectedOrder != null) {
            for (int i = 0; i < menu.clientOrders.size(); i++) {
                if (!menu.clientOrders.getCompound(i).getUUID("Id").equals(selectedOrder)) continue;
                orderIndex = i;
                break;
            }
        }
        orderIndex = Math.clamp(orderIndex, 0, menu.clientOrders.size() - 1);
        CompoundTag tag = menu.clientOrders.getCompound(orderIndex);
        selectedOrder = tag.getUUID("Id");
        return tag;
    }

    private void refreshWidgetState() {
        CompoundTag order = currentOrder();
        viewButton.setMessage(TerminalData.text(showingOrders ? "stock" : "orders"));
        searchBox.visible = !showingOrders;
        claimButton.visible = showingOrders;
        endButton.visible = showingOrders;
        claimButton.active = selectedOrder != null && order.getBoolean("Ready");
        endButton.active = selectedOrder != null && !order.getBoolean("Ready");
    }

    private boolean validBasket() {
        for (var selection : basket.entrySet()) {
            var entry = lookup(selection.getKey());
            if (entry == null || !entry.requestable() || selection.getValue() > entry.amount()) return false;
        }
        return true;
    }

    private TerminalStock.Entry lookup(TerminalData.Selection selection) {
        for (var entry : menu.clientStock)
            if (Objects.equals(entry.network(), selection.network()) && selection.key().equals(new ItemStackKey(entry.stack()))) return entry;
        return null;
    }

    private void submitSelection() {
        if (!validBasket()) return;
        if (submission == null) submission = UUID.randomUUID();
        CompoundTag data = new CompoundTag(); data.putUUID("Submission", submission);
        ListTag list = new ListTag();
        basket.forEach((key, amount) -> list.add(TerminalData.entry(new TerminalStock.Entry(
                key.key().copyStackWithCount(1), amount, key.network(), true), menu.player.registryAccess())));
        data.put("Items", list);
        TerminalPackets.request(menu, TerminalPackets.SUBMIT, data);
    }

    private void orderAction(int action) {
        if (selectedOrder == null) return;
        CompoundTag data = new CompoundTag(); data.putUUID("Order", selectedOrder);
        TerminalPackets.request(menu, action, data);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderStockPanelBackground(graphics);
        renderRightPanel(graphics);
        if (showingOrders) renderOrders(graphics);
        else renderStock(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Titles are part of the modular GUI and are rendered explicitly.
    }

    private void renderStockPanelBackground(GuiGraphics graphics) {
        int x = leftPos - 15;
        int y = topPos;
        STOCK_HEADER.render(graphics, x, y);
        y += STOCK_HEADER.getHeight();
        int bodySlices = (windowHeight - STOCK_HEADER.getHeight() - STOCK_FOOTER.getHeight())
                / STOCK_BODY.getHeight();
        for (int i = 0; i < bodySlices; i++) {
            STOCK_BODY.render(graphics, x, y);
            y += STOCK_BODY.getHeight();
        }
        STOCK_FOOTER.render(graphics, x, y);
    }

    private void renderStock(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        float currentScroll = itemScroll.getValue(partialTick);
        renderStockEntries(graphics, currentScroll, partialTick, mouseX, mouseY);
        renderBasket(graphics);
        renderStockScrollbar(graphics, currentScroll);
        renderCollectButton(graphics, mouseX, mouseY);
    }

    private void renderStockEntries(GuiGraphics graphics, float currentScroll,
                                    float partialTick, int mouseX, int mouseY) {
        int windowX = leftPos + 16;
        int windowBottom = topPos + windowHeight - Layout.FOOTER_HEIGHT;
        graphics.enableScissor(windowX, topPos + 17, leftPos + 215, windowBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -currentScroll * Layout.CELL_SIZE, 0);

        for (int sliceY = -2; sliceY < getMaxScroll() * Layout.CELL_SIZE + windowHeight - 72;
             sliceY += AllGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight()) {
            float renderedY = sliceY - currentScroll * Layout.CELL_SIZE;
            if (renderedY < -Layout.CELL_SIZE || renderedY > windowHeight - 72) continue;
            AllGuiTextures.STOCK_KEEPER_REQUEST_BG.render(graphics,
                    leftPos + 22, topPos + sliceY + 18);
        }

        STOCK_SEARCH.render(graphics, leftPos + 42, searchBox.getY() - 5);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        if (searchBox.getValue().isBlank() && !searchBox.isFocused()) {
            Component message = searchBox.getMessage();
            graphics.drawString(font, message,
                    leftPos + DimensionLogisticsTerminalLayout.LEFT_PANEL_WIDTH / 2 - font.width(message) / 2,
                    searchBox.getY(), Layout.TEXT_COLOR, false);
        }

        for (int index = 0; index < displayedItems.size(); index++) {
            int itemY = Layout.STOCK_Y + index / Layout.STOCK_COLUMNS * Layout.CELL_SIZE;
            float renderedY = itemY - currentScroll * Layout.CELL_SIZE;
            if (renderedY < 0) continue;
            if (renderedY > windowHeight - 72) break;
            TerminalStock.Entry entry = displayedItems.get(index);
            int x = Layout.STOCK_X + index % Layout.STOCK_COLUMNS * Layout.CELL_SIZE;
            renderEntry(graphics, entry.stack(), entry.amount(), x, itemY,
                    entry.network() != null, !entry.requestable(), true);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private void renderBasket(GuiGraphics graphics) {
        List<Map.Entry<TerminalData.Selection, Integer>> entries = basketEntries();
        basketOffset = Math.clamp(basketOffset, 0, Math.max(0, entries.size() - Layout.BASKET_SIZE));
        for (int slot = 0; slot < Layout.BASKET_SIZE && slot + basketOffset < entries.size(); slot++) {
            Map.Entry<TerminalData.Selection, Integer> value = entries.get(slot + basketOffset);
            TerminalStock.Entry stock = lookup(value.getKey());
            renderEntry(graphics, value.getKey().key().copyStackWithCount(1), value.getValue(),
                    Layout.STOCK_X + slot * Layout.CELL_SIZE, basketY() - topPos,
                    value.getKey().network() != null,
                    stock == null || !stock.requestable() || stock.amount() < value.getValue(), false);
        }
    }

    private void renderStockScrollbar(GuiGraphics graphics, float currentScroll) {
        int maxScroll = getMaxScroll();
        if (maxScroll == 0) return;
        int visibleHeight = windowHeight - Layout.SCROLL_WINDOW_TRIM;
        int totalHeight = maxScroll * Layout.CELL_SIZE + visibleHeight;
        int barSize = Math.max(5, Mth.floor((float) visibleHeight / totalHeight * (visibleHeight - 2)));
        int barX = leftPos + Layout.STOCK_X + Layout.STOCK_WIDTH;
        int barY = topPos + Layout.SCROLL_TOP
                + (int) ((currentScroll * Layout.CELL_SIZE) / totalHeight * (visibleHeight - 2));
        AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
        graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize,
                pad.getStartX(), pad.getStartY(), pad.getWidth(), pad.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
        if (barSize > 16)
            AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
    }

    private int basketY() {
        return topPos + windowHeight - Layout.BASKET_FROM_BOTTOM;
    }

    private void renderCollectButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean active = !basket.isEmpty() && validBasket();
        if (active && isCollectHovered(mouseX, mouseY))
            SEND_HOVER.render(graphics, leftPos + Layout.SEND_TEXTURE_X,
                    topPos + windowHeight - Layout.SEND_TEXTURE_FROM_BOTTOM);
        Component label = TerminalData.text("collect");
        int color = active ? 0x252525 : 0x8a7d73;
        graphics.drawString(font, label,
                leftPos + Layout.SEND_LABEL_CENTER_X - font.width(label) / 2,
                topPos + windowHeight - Layout.SEND_LABEL_FROM_BOTTOM, color, false);
    }

    private boolean isCollectHovered(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, Layout.SEND_X, windowHeight - Layout.SEND_FROM_BOTTOM,
                Layout.SEND_WIDTH, Layout.SEND_HEIGHT);
    }

    private List<Map.Entry<TerminalData.Selection, Integer>> basketEntries() {
        return new ArrayList<>(basket.entrySet());
    }

    private void renderRightPanel(GuiGraphics graphics) {
        int x = leftPos + DimensionLogisticsTerminalLayout.RIGHT_PANEL_X;
        int y = topPos;
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_HEADER.render(graphics, x, y);
        y += ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_HEADER.getHeight();
        y = renderRightSection(graphics, x, y, ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_CRAFT);
        y = renderRightSection(graphics, x, y, ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_PLAYER);
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BOTTOM.render(graphics, x, y);
    }

    private int renderRightSection(GuiGraphics graphics, int x, int y, ShHsGuiTextures foreground) {
        ShHsGuiTextures background = ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BACKGROUND;
        int sliceHeight = background.getHeight();
        int sectionHeight = (foreground.getHeight() + sliceHeight - 1) / sliceHeight * sliceHeight;
        for (int offset = 0; offset < sectionHeight; offset += sliceHeight)
            background.render(graphics, x, y + offset);
        foreground.render(graphics, x, y);
        return y + sectionHeight;
    }

    private void renderOrders(GuiGraphics graphics) {
        CompoundTag order = currentOrder();
        graphics.drawString(font, TerminalData.text("orders"), leftPos + Layout.ORDER_TITLE_X,
                topPos + Layout.ORDER_TITLE_Y, Layout.TEXT_COLOR, false);
        if (selectedOrder == null) {
            graphics.drawString(font, TerminalData.text("no_orders"), leftPos + Layout.ORDER_TEXT_X,
                    topPos + Layout.ORDER_STATUS_Y, Layout.TEXT_COLOR, false);
            return;
        }
        graphics.drawString(font, "◀ " + (orderIndex + 1) + " / " + menu.clientOrders.size() + " ▶",
                leftPos + Layout.ORDER_PAGE_X, topPos + Layout.ORDER_PAGE_Y, Layout.TEXT_COLOR, false);
        graphics.drawString(font, TerminalData.text(order.getBoolean("Ready") ? "ready" : "waiting"),
                leftPos + Layout.ORDER_TEXT_X, topPos + Layout.ORDER_STATUS_Y, Layout.TEXT_COLOR, false);
        graphics.drawString(font, TerminalData.text("progress", order.getInt("Held"), order.getInt("Packages")),
                leftPos + Layout.ORDER_TEXT_X, topPos + Layout.ORDER_PROGRESS_Y, Layout.TEXT_COLOR, false);
        graphics.drawString(font, TerminalData.text("missing"), leftPos + Layout.ORDER_TEXT_X,
                topPos + Layout.ORDER_MISSING_LABEL_Y, Layout.TEXT_COLOR, false);

        List<TerminalStock.Entry> missing = decodeMissing(order);
        missingRow = Math.clamp(missingRow, 0, Math.max(0,
                (missing.size() + Layout.STOCK_COLUMNS - 1) / Layout.STOCK_COLUMNS - Layout.ORDER_MISSING_ROWS));
        int first = missingRow * Layout.STOCK_COLUMNS;
        int capacity = Layout.STOCK_COLUMNS * Layout.ORDER_MISSING_ROWS;
        for (int slot = 0; slot < capacity && first + slot < missing.size(); slot++) {
            TerminalStock.Entry entry = missing.get(first + slot);
            int x = Layout.STOCK_X + slot % Layout.STOCK_COLUMNS * Layout.CELL_SIZE;
            int y = Layout.ORDER_MISSING_Y + slot / Layout.STOCK_COLUMNS * Layout.CELL_SIZE;
            renderEntry(graphics, entry.stack(), entry.amount(), x, y, true, false, true);
        }
    }

    private List<TerminalStock.Entry> decodeMissing(CompoundTag order) {
        List<TerminalStock.Entry> missing = new ArrayList<>();
        ListTag missingTags = order.getList("Missing", Tag.TAG_COMPOUND);
        for (int i = 0; i < missingTags.size(); i++)
            missing.add(TerminalData.entry(missingTags.getCompound(i), menu.player.registryAccess()));
        return missing;
    }

    private void renderEntry(GuiGraphics graphics, ItemStack stack, long amount, int x, int y,
                             boolean external, boolean unavailable, boolean renderSlotBackground) {
        x += leftPos;
        y += topPos;
        if (renderSlotBackground) STOCK_SLOT.render(graphics, x, y);
        graphics.renderItem(stack, x + 1, y + 1);
        if (external) graphics.fill(x, y, x + 4, y + 3, 0xff52bbd0);
        if (unavailable) graphics.fill(x, y, x + 18, y + 18, 0x88ad3030);
        String count = amount >= 1000000 ? String.format(Locale.ROOT, "%.1fm", amount / 1000000d)
                : amount >= 10000 ? (amount / 1000) + "k" : Long.toString(amount);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 18, y + 12, 200);
        graphics.pose().scale(.65f, .65f, 1);
        graphics.drawString(font, count, -font.width(count), 0, 0xffffff, true);
        graphics.pose().popPose();
    }

    private HoveredEntry hovered(int mouseX, int mouseY) {
        if (showingOrders) return null;
        int x = mouseX - leftPos - Layout.STOCK_X;
        int y = mouseY - topPos;
        if (x < 0 || x >= Layout.STOCK_WIDTH) return null;
        if (y >= 16 && y <= windowHeight - Layout.FOOTER_HEIGHT && itemScroll.settled()) {
            int row = Mth.floor((y - Layout.STOCK_Y) / (float) Layout.CELL_SIZE
                    + itemScroll.getChaseTarget());
            int index = row * Layout.STOCK_COLUMNS + x / Layout.CELL_SIZE;
            return index >= 0 && index < displayedItems.size()
                    ? new HoveredEntry(displayedItems.get(index), HoveredArea.STOCK)
                    : null;
        }
        int basketY = basketY() - topPos;
        if (y >= basketY && y < basketY + Layout.CELL_SIZE) {
            List<Map.Entry<TerminalData.Selection, Integer>> list = basketEntries();
            int index = basketOffset + x / Layout.CELL_SIZE;
            if (index < list.size()) {
                Map.Entry<TerminalData.Selection, Integer> value = list.get(index);
                TerminalStock.Entry entry = new TerminalStock.Entry(value.getKey().key().copyStackWithCount(1),
                        value.getValue(), value.getKey().network(), lookup(value.getKey()) != null);
                return new HoveredEntry(entry, HoveredArea.BASKET);
            }
        }
        return null;
    }

    public Optional<Map.Entry<ItemStack, Rect2i>> hoveredIngredient(double mouseX, double mouseY) {
        HoveredEntry hovered = hovered(logical(mouseX), logical(mouseY));
        return hovered == null ? Optional.empty() : Optional.of(Map.entry(hovered.entry().stack(),
                new Rect2i((int) mouseX - 8, (int) mouseY - 8, 16, 16)));
    }

    public Rect2i physicalBounds() {
        return new Rect2i((int) (leftPos * uiScale), (int) (topPos * uiScale), (int) (imageWidth * uiScale), (int) (imageHeight * uiScale));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = logical(mouseX);
        int y = logical(mouseY);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        super.render(graphics, x, y, partialTick);
        renderTooltip(graphics, x, y);
        renderEntryTooltip(graphics, x, y);
        graphics.pose().popPose();
    }

    private void renderEntryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        HoveredEntry hovered = hovered(mouseX, mouseY);
        if (hovered == null || !menu.getCarried().isEmpty()) return;
        TerminalStock.Entry entry = hovered.entry();
        List<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, entry.stack()));
        tooltip.add(TerminalData.text("amount", entry.amount()));
        tooltip.add(entry.network() == null ? TerminalData.text("local")
                : TerminalData.text("external", entry.network().toString().substring(0, 8)));
        if (!entry.requestable()) tooltip.add(TerminalData.text("unavailable"));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = logical(mouseX);
        double y = logical(mouseY);
        if (!showingOrders && button == 1 && searchBox.isMouseOver(x, y)) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            itemScroll.startWithValue(0);
            return true;
        }
        if (handleOrderNavigation(x, y)) return true;
        if (handleDeposit(x, y)) return true;
        if (!showingOrders && button == 0 && isCollectHovered(x, y)
                && !basket.isEmpty() && validBasket()) {
            submitSelection();
            return true;
        }
        if (!showingOrders && button == 0 && beginScrollbarDrag(x, y)) return true;

        HoveredEntry hovered = hovered((int) x, (int) y);
        if (hovered != null && (button == 0 || button == 1)) {
            TerminalStock.Entry entry = hovered.entry();
            TerminalData.Selection key = new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network());
            int delta = hasShiftDown() ? entry.stack().getMaxStackSize() : 1;
            if (hovered.area() == HoveredArea.BASKET || button == 1) delta = -delta;
            change(key, delta);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private boolean handleOrderNavigation(double mouseX, double mouseY) {
        if (!showingOrders || menu.clientOrders.isEmpty()
                || !inside(mouseX, mouseY, Layout.ORDER_NAV_X, Layout.ORDER_NAV_Y,
                Layout.ORDER_NAV_WIDTH, Layout.ORDER_NAV_HEIGHT)) return false;
        int direction = mouseX < leftPos + Layout.ORDER_NAV_MIDDLE_X ? -1 : 1;
        orderIndex = Math.floorMod(orderIndex + direction, menu.clientOrders.size());
        selectedOrder = null;
        missingRow = 0;
        refreshWidgetState();
        return true;
    }

    private boolean handleDeposit(double mouseX, double mouseY) {
        if (showingOrders || menu.getCarried().isEmpty()
                || !inside(mouseX, mouseY, Layout.DEPOSIT_X, Layout.DEPOSIT_Y,
                Layout.DEPOSIT_WIDTH, windowHeight - Layout.DEPOSIT_Y - 93)) return false;
        TerminalPackets.request(menu, TerminalPackets.DEPOSIT, new CompoundTag());
        return true;
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        if (getMaxScroll() == 0) return false;
        int barX = leftPos + Layout.STOCK_X + Layout.STOCK_WIDTH;
        if (mouseX <= barX || mouseX > barX + 8
                || mouseY <= topPos + Layout.SCROLL_TOP
                || mouseY >= topPos + windowHeight - 82) return false;
        scrollHandleActive = true;
        updateScrollFromMouse(mouseY);
        return true;
    }

    private void updateScrollFromMouse(double mouseY) {
        int visibleHeight = windowHeight - Layout.SCROLL_WINDOW_TRIM;
        int totalHeight = getMaxScroll() * Layout.CELL_SIZE + visibleHeight;
        int barSize = Math.max(5, Mth.floor((float) visibleHeight / totalHeight * (visibleHeight - 2)));
        double target = (mouseY - topPos - Layout.SCROLL_TOP - barSize / 2.0)
                * totalHeight / (visibleHeight - 2) / Layout.CELL_SIZE;
        itemScroll.chase(Mth.clamp(target, 0, getMaxScroll()), 0.8, Chaser.EXP);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    private void change(TerminalData.Selection key, int delta) {
        TerminalStock.Entry stock = lookup(key);
        int old = basket.getOrDefault(key, 0);
        if (delta > 0 && (stock == null || !stock.requestable() || basket.size() >= TerminalData.MAX_LINES && old == 0)) return;
        long max = stock == null ? old : stock.amount();
        int total = basket.values().stream().mapToInt(Integer::intValue).sum();
        long upperBound = Math.clamp((long) TerminalData.MAX_ITEMS - total + old, 0L, max);
        int amount = (int) Math.clamp((long) old + delta, 0L, upperBound);
        if (amount == 0) basket.remove(key); else basket.put(key, amount);
        submission = null;
        refreshWidgetState();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        double x = logical(mouseX);
        double y = logical(mouseY);
        if (x >= leftPos + Layout.LEFT_MOUSE_X && x <= leftPos + Layout.LEFT_MOUSE_X + Layout.LEFT_MOUSE_WIDTH) {
            int direction = (int) Math.signum(dy);
            if (showingOrders) {
                missingRow = Math.max(0, missingRow - direction);
                return true;
            }
            if (y >= basketY() - 4 && y <= basketY() - 4 + Layout.BASKET_MOUSE_HEIGHT) {
                HoveredEntry hovered = hovered((int) x, (int) y);
                if (hasControlDown() && hovered != null) {
                    TerminalStock.Entry entry = hovered.entry();
                    change(new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network()), direction);
                } else {
                    basketOffset = Math.clamp(basketOffset - direction,
                            0, Math.max(0, basket.size() - Layout.BASKET_SIZE));
                }
                return true;
            }
            float target = Mth.clamp(itemScroll.getChaseTarget()
                    + (float) Math.ceil(Math.abs(dy)) * (float) -Math.signum(dy), 0, getMaxScroll());
            itemScroll.chase(target, 0.5, Chaser.EXP);
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    private int logical(double coordinate) {
        return (int) (coordinate / uiScale);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollHandleActive) {
            scrollHandleActive = false;
            return true;
        }
        return super.mouseReleased(logical(x), logical(y), button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollHandleActive) {
            updateScrollFromMouse(logical(y));
            return true;
        }
        return super.mouseDragged(logical(x), logical(y), button, dx / uiScale, dy / uiScale);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 && hasShiftDown() && !showingOrders && !basket.isEmpty() && validBasket()) {
            submitSelection();
            return true;
        }
        if (searchBox.isFocused() && key != 256)
            return searchBox.keyPressed(key, scan, modifiers) || searchBox.canConsumeInput();
        return super.keyPressed(key, scan, modifiers);
    }

    private enum HoveredArea {
        STOCK,
        BASKET
    }

    private record HoveredEntry(TerminalStock.Entry entry, HoveredArea area) {}
}
