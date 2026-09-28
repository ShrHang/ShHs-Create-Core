package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.content.data.ShHsGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class DimensionLogisticsTerminalScreen extends AbstractContainerScreen<DimensionLogisticsTerminalMenu> {
    private final LinkedHashMap<TerminalData.Selection, Integer> basket = new LinkedHashMap<>();
    private List<TerminalStock.Entry> displayed = List.of();
    private EditBox search;
    private Button submit, mode, claim, end;
    private int stockRow, basketOffset, orderIndex, missingRow;
    private float uiScale = 1;
    private boolean orders;
    private UUID submission;
    private UUID selectedOrder;
    private long seenRevision = -1;
    private String lastSearch = "";

    public DimensionLogisticsTerminalScreen(DimensionLogisticsTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 464; imageHeight = 256;
        inventoryLabelY = -10000; titleLabelY = -10000;
    }

    @Override
    protected void init() {
        // Everything, including slot hit-testing and widgets, uses the same logical coordinate system.
        uiScale = Math.min(1f, Math.min((width - 12f) / imageWidth, (height - 12f) / imageHeight));
        width = (int) (width / uiScale); height = (int) (height / uiScale);
        super.init();
        search = new EditBox(font, leftPos + 71, topPos + 21, 131, 12, TerminalData.text("search"));
        search.setBordered(false); search.setTextColor(0x4A2D31); search.setMaxLength(80); search.setValue(lastSearch);
        search.setResponder(value -> { lastSearch = value; stockRow = 0; filter(); });
        addRenderableWidget(search);
        submit = addRenderableWidget(Button.builder(TerminalData.text("collect"), ignored -> submit())
                .bounds(leftPos + 133, topPos + 220, 99, 20).build());
        mode = addRenderableWidget(Button.builder(TerminalData.text("orders"), ignored -> {
            orders = !orders; updateButtons();
        }).bounds(leftPos + 30, topPos + 220, 99, 20).build());
        claim = addRenderableWidget(Button.builder(TerminalData.text("claim"), ignored -> orderAction(TerminalPackets.CLAIM))
                .bounds(leftPos + 32, topPos + 192, 74, 20).build());
        end = addRenderableWidget(Button.builder(TerminalData.text("end"), ignored -> orderAction(TerminalPackets.END))
                .bounds(leftPos + 110, topPos + 192, 122, 20).build());
        addRenderableWidget(Button.builder(TerminalData.text("clear_craft"), ignored ->
                TerminalPackets.request(menu, TerminalPackets.CLEAR_CRAFT, new CompoundTag()))
                .bounds(leftPos + 271, topPos + 234, 164, 18).build());
        filter(); updateButtons();
    }

    private void filter() {
        String query = lastSearch.toLowerCase(Locale.ROOT).strip();
        displayed = menu.clientStock.stream().filter(entry -> query.isEmpty()
                || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString().contains(query)).toList();
        stockRow = Math.clamp(stockRow, 0, Math.max(0, (displayed.size() + 8) / 9 - 6));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.clientRevision != seenRevision) {
            seenRevision = menu.clientRevision;
            if (submission != null && submission.equals(menu.acceptedSubmission)) {
                basket.clear(); submission = null;
            }
            filter();
        }
        updateButtons();
    }

    private CompoundTag currentOrder() {
        if (menu.clientOrders.isEmpty()) { selectedOrder = null; return new CompoundTag(); }
        if (selectedOrder != null) for (int i = 0; i < menu.clientOrders.size(); i++)
            if (menu.clientOrders.getCompound(i).getUUID("Id").equals(selectedOrder)) { orderIndex = i; break; }
        orderIndex = Math.clamp(orderIndex, 0, menu.clientOrders.size() - 1);
        CompoundTag tag = menu.clientOrders.getCompound(orderIndex);
        selectedOrder = tag.getUUID("Id");
        return tag;
    }

    private void updateButtons() {
        CompoundTag order = currentOrder();
        mode.setMessage(TerminalData.text(orders ? "stock" : "orders"));
        search.visible = !orders; submit.visible = !orders;
        submit.active = !basket.isEmpty() && validBasket();
        claim.visible = end.visible = orders;
        claim.active = selectedOrder != null && order.getBoolean("Ready");
        end.active = selectedOrder != null && !order.getBoolean("Ready");
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

    private void submit() {
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
        AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER.render(graphics, leftPos, topPos);
        for (int i = 0; i < 7; i++) AllGuiTextures.STOCK_KEEPER_REQUEST_BODY.render(graphics, leftPos, topPos + 36 + i * 20);
        AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER.render(graphics, leftPos, topPos + 176);
        renderRightPanel(graphics);
        if (orders) renderOrders(graphics);
        else {
            for (int i = 0; i < 54; i++) {
                int index = stockRow * 9 + i;
                if (index >= displayed.size()) break;
                var entry = displayed.get(index);
                renderEntry(graphics, entry.stack(), entry.amount(), 34 + i % 9 * 20, 40 + i / 9 * 20,
                        entry.network() != null, !entry.requestable());
            }
            graphics.drawString(font, TerminalData.text("basket"), leftPos + 34, topPos + 173, 0x4A2D31, false);
            List<Map.Entry<TerminalData.Selection, Integer>> entries = new ArrayList<>(basket.entrySet());
            basketOffset = Math.clamp(entries.size() - 9, 0, basketOffset);
            for (int i = 0; i < 9 && i + basketOffset < entries.size(); i++) {
                var value = entries.get(i + basketOffset);
                var entry = lookup(value.getKey());
                renderEntry(graphics, value.getKey().key().copyStackWithCount(1), value.getValue(), 34 + i * 20, 191,
                        value.getKey().network() != null, entry == null || !entry.requestable() || entry.amount() < value.getValue());
            }
            if (displayed.size() > 54) {
                int max = Math.max(1, (displayed.size() + 8) / 9 - 6);
                graphics.fill(leftPos + 233, topPos + 40, leftPos + 236, topPos + 159, 0xff71534a);
                graphics.fill(leftPos + 232, topPos + 40 + stockRow * 103 / max, leftPos + 237, topPos + 56 + stockRow * 103 / max, 0xffd8c5a0);
            }
        }
        String name = menu.clientNetworkName.isEmpty() ? TerminalData.text("no_network").getString() : menu.clientNetworkName;
        graphics.drawString(font, font.plainSubstrByWidth(name, 163), leftPos + 272, topPos + 205, 0xd9c6aa, true);
        graphics.drawString(font, TerminalData.text("deposit_hint"), leftPos + 272, topPos + 220, 0xd9c6aa, true);
    }

    private void renderRightPanel(GuiGraphics graphics) {
        int x = leftPos + DimensionLogisticsTerminalMenu.RIGHT_PANEL_X;
        int y = topPos;
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_HEADER.render(graphics, x, y);
        y += ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_HEADER.getHeight();
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_CRAFT.render(graphics, x, y);
        y += ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_CRAFT.getHeight();
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BUFFER.render(graphics, x, y);
        y += ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BUFFER.getHeight();
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_PLAYER.render(graphics, x, y);
        y += ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_PLAYER.getHeight();
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BUFFER.render(graphics, x, y);
        y += ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BUFFER.getHeight();
        ShHsGuiTextures.DIMENSION_LOGISTICS_TERMINAL_BOTTOM.render(graphics, x, y);
        graphics.drawCenteredString(font, title, x + 128, topPos + 6, 0x4A2D31);
    }

    private void renderOrders(GuiGraphics graphics) {
        CompoundTag order = currentOrder();
        graphics.drawString(font, TerminalData.text("orders"), leftPos + 70, topPos + 21, 0x4A2D31, false);
        if (selectedOrder == null) {
            graphics.drawString(font, TerminalData.text("no_orders"), leftPos + 34, topPos + 65, 0x4A2D31, false); return;
        }
        graphics.drawString(font, "◀ " + (orderIndex + 1) + " / " + menu.clientOrders.size() + " ▶", leftPos + 76, topPos + 44, 0x4A2D31, false);
        graphics.drawString(font, TerminalData.text(order.getBoolean("Ready") ? "ready" : "waiting"), leftPos + 34, topPos + 63, 0x4A2D31, false);
        graphics.drawString(font, TerminalData.text("progress", order.getInt("Held"), order.getInt("Packages")), leftPos + 34, topPos + 79, 0x4A2D31, false);
        graphics.drawString(font, TerminalData.text("missing"), leftPos + 34, topPos + 97, 0x4A2D31, false);
        List<TerminalStock.Entry> missing = new ArrayList<>();
        ListTag missingTags = order.getList("Missing", Tag.TAG_COMPOUND);
        for (int i = 0; i < missingTags.size(); i++) missing.add(TerminalData.entry(missingTags.getCompound(i), menu.player.registryAccess()));
        missingRow = Math.clamp(missingRow, 0, Math.max(0, (missing.size() + 8) / 9 - 3));
        for (int i = 0; i < 27 && i + missingRow * 9 < missing.size(); i++) {
            var entry = missing.get(i + missingRow * 9);
            renderEntry(graphics, entry.stack(), entry.amount(), 34 + i % 9 * 20, 113 + i / 9 * 20, true, false);
        }
    }

    private void renderEntry(GuiGraphics graphics, ItemStack stack, long amount, int x, int y, boolean external, boolean unavailable) {
        x += leftPos; y += topPos;
        AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, x, y);
        graphics.renderItem(stack, x + 1, y + 1);
        if (external) graphics.fill(x, y, x + 4, y + 3, 0xff52bbd0);
        if (unavailable) graphics.fill(x, y, x + 18, y + 18, 0x88ad3030);
        String count = amount >= 1000000 ? String.format(Locale.ROOT, "%.1fm", amount / 1000000d)
                : amount >= 10000 ? (amount / 1000) + "k" : Long.toString(amount);
        graphics.pose().pushPose(); graphics.pose().translate(x + 18, y + 12, 200); graphics.pose().scale(.65f, .65f, 1);
        graphics.drawString(font, count, -font.width(count), 0, 0xffffff, true); graphics.pose().popPose();
    }

    private TerminalStock.Entry hovered(int mouseX, int mouseY) {
        int x = mouseX - leftPos - 34, y = mouseY - topPos;
        if (x < 0 || x >= 180 || orders) return null;
        if (y >= 40 && y < 160) {
            int index = stockRow * 9 + (y - 40) / 20 * 9 + x / 20;
            return index < displayed.size() ? displayed.get(index) : null;
        }
        if (y >= 191 && y < 211) {
            var list = new ArrayList<>(basket.entrySet());
            int index = basketOffset + x / 20;
            if (index < list.size()) {
                var value = list.get(index);
                return new TerminalStock.Entry(value.getKey().key().copyStackWithCount(1), value.getValue(), value.getKey().network(), lookup(value.getKey()) != null);
            }
        }
        return null;
    }

    public Optional<Map.Entry<ItemStack, Rect2i>> hoveredIngredient(double mouseX, double mouseY) {
        var entry = hovered((int) (mouseX / uiScale), (int) (mouseY / uiScale));
        return entry == null ? Optional.empty() : Optional.of(Map.entry(entry.stack(), new Rect2i((int) mouseX - 8, (int) mouseY - 8, 16, 16)));
    }

    public Rect2i physicalBounds() {
        return new Rect2i((int) (leftPos * uiScale), (int) (topPos * uiScale), (int) (imageWidth * uiScale), (int) (imageHeight * uiScale));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = (int) (mouseX / uiScale), y = (int) (mouseY / uiScale);
        graphics.pose().pushPose(); graphics.pose().scale(uiScale, uiScale, 1);
        super.render(graphics, x, y, partialTick);
        renderTooltip(graphics, x, y);
        var entry = hovered(x, y);
        if (entry != null && menu.getCarried().isEmpty()) {
            List<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, entry.stack()));
            tooltip.add(TerminalData.text("amount", entry.amount()));
            tooltip.add(entry.network() == null ? TerminalData.text("local") : TerminalData.text("external", entry.network().toString().substring(0, 8)));
            if (!entry.requestable()) tooltip.add(TerminalData.text("unavailable"));
            graphics.renderComponentTooltip(font, tooltip, x, y);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX / uiScale, y = mouseY / uiScale;
        if (orders && y >= topPos + 38 && y < topPos + 58 && x >= leftPos + 32 && x < leftPos + 230 && !menu.clientOrders.isEmpty()) {
            orderIndex = Math.floorMod(orderIndex + (x < leftPos + 126 ? -1 : 1), menu.clientOrders.size());
            selectedOrder = null; missingRow = 0; updateButtons(); return true;
        }
        if (!orders && x >= leftPos + 30 && x < leftPos + 231 && y >= topPos + 38 && y < topPos + 163 && !menu.getCarried().isEmpty()) {
            TerminalPackets.request(menu, TerminalPackets.DEPOSIT, new CompoundTag()); return true;
        }
        var entry = hovered((int) x, (int) y);
        if (entry != null && (button == 0 || button == 1)) {
            var key = new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network());
            boolean inBasket = y >= topPos + 191;
            int delta = hasShiftDown() ? entry.stack().getMaxStackSize() : 1;
            if (inBasket || button == 1) delta = -delta;
            change(key, delta);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void change(TerminalData.Selection key, int delta) {
        TerminalStock.Entry stock = lookup(key);
        int old = basket.getOrDefault(key, 0);
        if (delta > 0 && (stock == null || !stock.requestable() || basket.size() >= TerminalData.MAX_LINES && old == 0)) return;
        long max = stock == null ? old : stock.amount();
        int total = basket.values().stream().mapToInt(Integer::intValue).sum();
        int amount = (int) Math.max(0, Math.min(Math.min(max, TerminalData.MAX_ITEMS - total + old), (long) old + delta));
        if (amount == 0) basket.remove(key); else basket.put(key, amount);
        submission = null; updateButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        double x = mouseX / uiScale, y = mouseY / uiScale;
        if (x >= leftPos + 28 && x <= leftPos + 238) {
            if (orders) { missingRow = Math.max(0, missingRow - (int) Math.signum(dy)); return true; }
            if (y >= topPos + 187 && y <= topPos + 216) {
                var entry = hovered((int) x, (int) y);
                if (hasControlDown() && entry != null) change(new TerminalData.Selection(new ItemStackKey(entry.stack()), entry.network()), (int) Math.signum(dy));
                else basketOffset = Math.max(0, Math.clamp(basket.size() - 9, 0, basketOffset - (int) Math.signum(dy)));
                return true;
            }
            stockRow -= (int) Math.signum(dy); filter(); return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(x / uiScale, y / uiScale, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused() && key != 256) return search.keyPressed(key, scan, modifiers) || search.canConsumeInput();
        return super.keyPressed(key, scan, modifiers);
    }
}
