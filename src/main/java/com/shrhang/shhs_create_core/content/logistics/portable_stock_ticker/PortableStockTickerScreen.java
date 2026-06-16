package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.Mods;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen.SearchSyncMode;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import mezz.jei.api.runtime.IIngredientFilter;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class PortableStockTickerScreen extends AbstractSimiContainerScreen<PortableStockTickerMenu> {
    private static final AllGuiTextures NUMBERS = AllGuiTextures.NUMBERS;
    private static final AllGuiTextures HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final AllGuiTextures BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final AllGuiTextures FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;

    private static final int COLS = 9;
    private static final int ROW_HEIGHT = 20;
    private static final int COL_WIDTH = 20;

    private final LerpedFloat itemScroll = LerpedFloat.linear().startWithValue(0);
    private final List<BigItemStack> displayedItems = new ArrayList<>();
    private final List<BigItemStack> itemsToOrder = new ArrayList<>();
    private final List<CraftableBigItemStack> recipesToOrder = new ArrayList<>();
    private List<List<BigItemStack>> currentItemSource;

    private EditBox searchBox;
    private EditBox addressBox;
    private boolean scrollHandleActive;
    private boolean refreshSearchNextTick = true;
    private boolean moveToTopNextTick = true;
    private boolean canRequestCraftingPackage;
    private String previousJEISearchText = "";

    private int itemsX;
    private int itemsY;
    private int orderY;
    private int jeiSyncX;
    private int besideSearchButtonY;
    private int windowWidth;
    private int windowHeight;
    private int emptyTicks;
    private int successTicks;

    public PortableStockTickerScreen(PortableStockTickerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        inventoryLabelY = -1000;
        titleLabelY = -1000;
        menu.screenReference = this;
    }

    @Override
    protected void init() {
        int appropriateHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 10;
        appropriateHeight -= Mth.positiveModulo(
                appropriateHeight - HEADER.getHeight() - FOOTER.getHeight(),
                BODY.getHeight()
        );
        appropriateHeight = Math.min(
                appropriateHeight,
                HEADER.getHeight() + FOOTER.getHeight() + BODY.getHeight() * 17
        );

        imageWidth = windowWidth = 226;
        imageHeight = windowHeight = appropriateHeight;
        super.init();
        clearWidgets();

        itemsX = leftPos + (windowWidth - COLS * COL_WIDTH) / 2 + 1;
        itemsY = topPos + 33;
        orderY = topPos + windowHeight - 72;
        jeiSyncX = leftPos + 25;
        besideSearchButtonY = topPos + 18;

        Component searchLabel = CreateLang.translateDirect("gui.stock_keeper.search_items");
        searchBox = new EditBox(font, leftPos + 71, topPos + 22, 100, 9, searchLabel);
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0x4A2D31);
        addWidget(searchBox);

        String previousAddress = addressBox == null ? "" : addressBox.getValue();
        addressBox = new EditBox(font, leftPos + 27, topPos + windowHeight - 36, 92, 10,
                CreateLang.translateDirect("gui.stock_keeper.package_address"));
        addressBox.setBordered(false);
        addressBox.setTextColor(0x714A40);
        addressBox.setMaxLength(64);
        addressBox.setValue(previousAddress);
        addRenderableWidget(addressBox);

        refreshSearchNextTick = true;
        moveToTopNextTick = true;
        syncJEI(true);
        PacketDistributor.sendToServer(new PortableStockRequestPacket(menu.networkId));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot != null) {
            snapshot.tick();
        }

        if (shouldSyncFromJEI()) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            syncJEI(true);
        }

        boolean allEmpty = displayedItems.isEmpty();
        emptyTicks = allEmpty ? emptyTicks + 1 : 0;
        successTicks = successTicks > 0 && itemsToOrder.isEmpty() ? successTicks + 1 : 0;

        List<List<BigItemStack>> clientStockSnapshot = snapshot == null ? null : snapshot.stockSnapshot();
        if (clientStockSnapshot != currentItemSource) {
            currentItemSource = clientStockSnapshot;
            refreshSearchResults(false);
            revalidateOrders();
        }

        if (refreshSearchNextTick) {
            refreshSearchNextTick = false;
            refreshSearchResults(moveToTopNextTick);
            moveToTopNextTick = false;
        }

        itemScroll.tickChaser();
        if (Math.abs(itemScroll.getValue() - itemScroll.getChaseTarget()) < 1 / 16f) {
            itemScroll.setValue(itemScroll.getChaseTarget());
        }

        if (snapshot == null || snapshot.ticksSinceLastUpdate() > 15) {
            PacketDistributor.sendToServer(new PortableStockRequestPacket(menu.networkId));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, -300);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        pose.popPose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        float currentScroll = itemScroll.getValue(partialTick);
        HoveredSlot hoveredSlot = getHoveredSlot(mouseX, mouseY);

        int x = leftPos;
        int y = topPos;

        HEADER.render(graphics, x - 15, y);
        y += HEADER.getHeight();
        for (int i = 0; i < (windowHeight - HEADER.getHeight() - FOOTER.getHeight()) / BODY.getHeight(); i++) {
            BODY.render(graphics, x - 15, y);
            y += BODY.getHeight();
        }
        FOOTER.render(graphics, x - 15, y);

        if (addressBox.getValue().isBlank() && !addressBox.isFocused()) {
            graphics.drawString(
                    font,
                    CreateLang.translateDirect("gui.stock_keeper.package_address").copy().withStyle(ChatFormatting.ITALIC),
                    addressBox.getX(),
                    addressBox.getY(),
                    0xffCDBCA8,
                    false
            );
        }

        for (int index = 0; index < COLS; index++) {
            if (itemsToOrder.size() <= index) {
                break;
            }
            BigItemStack entry = itemsToOrder.get(index);
            boolean isHovered = hoveredSlot.area() == HoveredArea.ORDER && hoveredSlot.index() == index;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(itemsX + index * COL_WIDTH, orderY, 0);
            renderItemEntry(graphics, entry, isHovered, true);
            pose.popPose();
        }

        if (itemsToOrder.size() > COLS) {
            graphics.drawString(
                    font,
                    Component.literal("[+" + (itemsToOrder.size() - COLS) + "]"),
                    x + windowWidth - 40,
                    orderY + 21,
                    0xF8F8EC,
                    false
            );
        }

        if (!recipesToOrder.isEmpty()) {
            int jeiX = x + (windowWidth - COL_WIDTH * recipesToOrder.size()) / 2 + 1;
            int jeiY = orderY - 31;
            PoseStack recipePose = graphics.pose();
            recipePose.pushPose();
            recipePose.translate(jeiX, jeiY, 200);
            int xOffset = -3;
            AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_LEFT.render(graphics, xOffset, -3);
            xOffset += 10;
            for (int i = 0; i <= (recipesToOrder.size() - 1) * 5; i++) {
                AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_MIDDLE.render(graphics, xOffset, -3);
                xOffset += 4;
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_RIGHT.render(graphics, xOffset, -3);
            for (int index = 0; index < recipesToOrder.size(); index++) {
                CraftableBigItemStack craftable = recipesToOrder.get(index);
                boolean isHovered = hoveredSlot.area() == HoveredArea.RECIPE && hoveredSlot.index() == index;
                recipePose.pushPose();
                recipePose.translate(index * COL_WIDTH, 0, 0);
                renderItemEntry(graphics, craftable, isHovered, true);
                recipePose.popPose();
            }
            recipePose.popPose();
        }

        boolean justSent = itemsToOrder.isEmpty() && successTicks > 0;
        if (isConfirmHovered(mouseX, mouseY) && !justSent) {
            AllGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER.render(graphics, x + windowWidth - 81, topPos + windowHeight - 41);
        }

        graphics.drawString(font, title, x + windowWidth / 2 - font.width(title) / 2, topPos + 4, 0x714A40, false);
        Component sendLabel = CreateLang.translateDirect("gui.stock_keeper.send");
        graphics.drawString(
                font,
                sendLabel,
                x + windowWidth - 42 - font.width(sendLabel) / 2,
                topPos + windowHeight - 35,
                0x252525,
                false
        );

        if (justSent) {
            Component sentLabel = CreateLang.translateDirect("gui.stock_keeper.request_sent");
            float alpha = Mth.clamp((successTicks + partialTick - 10f) / 5f, 0f, 1f);
            if (alpha > 0) {
                int msgX = x + windowWidth / 2 - (font.width(sentLabel) + 10) / 2;
                int msgY = orderY + 5;
                int color = new Color(0x8C5D4B).setAlpha(alpha).getRGB();
                int width = font.width(sentLabel) + 14;
                AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_L.render(graphics, msgX - 8, msgY - 4);
                stretchTexture(graphics, msgX, msgY - 4, width, 16, AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_M);
                AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_R.render(graphics, msgX + font.width(sentLabel) + 10, msgY - 4);
                graphics.drawString(font, sentLabel, msgX + 5, msgY, color, false);
            }
        }

        int itemWindowX = x + 21;
        int itemWindowX2 = itemWindowX + 184;
        int itemWindowY = topPos + 17;
        int itemWindowY2 = topPos + windowHeight - 80;

        graphics.enableScissor(itemWindowX - 5, itemWindowY, itemWindowX2 + 10, itemWindowY2);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, -currentScroll * ROW_HEIGHT, 0);

        for (int sliceY = -2; sliceY < getMaxScroll() * ROW_HEIGHT + windowHeight - 72;
             sliceY += AllGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight()) {
            if (sliceY - currentScroll * ROW_HEIGHT < -20) {
                continue;
            }
            if (sliceY - currentScroll * ROW_HEIGHT > windowHeight - 72) {
                continue;
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_BG.render(graphics, x + 22, topPos + sliceY + 18);
        }

        AllGuiTextures.STOCK_KEEPER_REQUEST_SEARCH.render(graphics, x + 42, searchBox.getY() - 5);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        if (searchBox.getValue().isBlank() && !searchBox.isFocused()) {
            Component searchMessage = searchBox.getMessage();
            graphics.drawString(
                    font,
                    searchMessage,
                    x + windowWidth / 2 - font.width(searchMessage) / 2,
                    searchBox.getY(),
                    0xff4A2D31,
                    false
            );
        }

        if (Mods.JEI.isLoaded()) {
            AllConfigs.client().syncRecipeViewerSearch.get().buttonTexture.render(graphics, jeiSyncX, besideSearchButtonY);
        }

        if (displayedItems.isEmpty()) {
            Component message = getTroubleshootingMessage();
            float alpha = Mth.clamp((emptyTicks - 10f) / 5f, 0f, 1f);
            if (alpha > 0) {
                int lineX = x + windowWidth / 2 - font.width(message) / 2;
                int lineY = itemsY + 20;
                graphics.drawString(font, message, lineX + 1, lineY + 1, new Color(0x4A2D31).setAlpha(alpha).getRGB(), false);
                graphics.drawString(font, message, lineX, lineY, new Color(0xF8F8EC).setAlpha(alpha).getRGB(), false);
            }
        }

        for (int index = 0; index < displayedItems.size(); index++) {
            int itemY = itemsY + 4 + (index / COLS) * ROW_HEIGHT;
            float cullY = itemY - currentScroll * ROW_HEIGHT;
            if (cullY < topPos) {
                continue;
            }
            if (cullY > topPos + windowHeight - 72) {
                break;
            }
            boolean isHovered = hoveredSlot.area() == HoveredArea.ITEM && hoveredSlot.index() == index;
            BigItemStack entry = displayedItems.get(index);
            pose.pushPose();
            pose.translate(itemsX + (index % COLS) * COL_WIDTH, itemY, 0);
            renderItemEntry(graphics, entry, isHovered, false);
            pose.popPose();
        }

        pose.popPose();
        graphics.disableScissor();

        int visibleWindowHeight = windowHeight - 92;
        int totalHeight = getMaxScroll() * ROW_HEIGHT + visibleWindowHeight;
        int barSize = Math.max(5, Mth.floor((float) visibleWindowHeight / totalHeight * (visibleWindowHeight - 2)));
        if (barSize < visibleWindowHeight - 2) {
            int barX = itemsX + COLS * COL_WIDTH;
            int barY = topPos + 15;
            pose.pushPose();
            pose.translate(0, (currentScroll * ROW_HEIGHT) / totalHeight * (visibleWindowHeight - 2), 0);
            AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
            graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize, pad.getStartX(), pad.getStartY(),
                    pad.getWidth(), pad.getHeight(), 256, 256);
            AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
            if (barSize > 16) {
                AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
            pose.popPose();
        }
    }

    public Optional<Pair<ItemStack, Rect2i>> getHoveredIngredient(int mouseX, int mouseY) {
        HoveredSlot hoveredSlot = getHoveredSlot(mouseX, mouseY);
        if (hoveredSlot.isNone()) {
            return Optional.empty();
        }

        int x;
        int y;
        BigItemStack entry;
        if (hoveredSlot.area() == HoveredArea.RECIPE) {
            int jeiX = leftPos + (windowWidth - COL_WIDTH * recipesToOrder.size()) / 2 + 1;
            int jeiY = orderY - 31;
            x = jeiX + hoveredSlot.index() * COL_WIDTH;
            y = jeiY;
            entry = recipesToOrder.get(hoveredSlot.index());
        } else if (hoveredSlot.area() == HoveredArea.ORDER) {
            x = itemsX + hoveredSlot.index() * COL_WIDTH;
            y = orderY;
            entry = itemsToOrder.get(hoveredSlot.index());
        } else {
            x = itemsX + (hoveredSlot.index() % COLS) * COL_WIDTH;
            y = itemsY + 4 + (hoveredSlot.index() / COLS) * ROW_HEIGHT;
            entry = displayedItems.get(hoveredSlot.index());
        }

        return Optional.of(Pair.of(entry.stack.copy(), new Rect2i(x, y, 18, 18)));
    }

    public boolean isRecipeQueued(Recipe<?> recipe) {
        for (CraftableBigItemStack craftable : recipesToOrder) {
            if (craftable.recipe == recipe) {
                return true;
            }
        }
        return false;
    }

    public int getOrderSlotCount() {
        return itemsToOrder.size();
    }

    public void clearSearchBox() {
        searchBox.setValue("");
        refreshSearchNextTick = true;
    }

    public InventorySummary getAvailableSummary() {
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        return snapshot == null ? null : snapshot.summary();
    }

    public void requestCraftableTransfer(Recipe<?> recipe, int amount) {
        if (!(recipe instanceof CraftingRecipe craftingRecipe) || minecraft == null || minecraft.level == null) {
            return;
        }
        CraftableBigItemStack target = null;
        for (CraftableBigItemStack craftable : recipesToOrder) {
            if (craftable.recipe == recipe) {
                target = craftable;
                break;
            }
        }
        if (target == null) {
            ItemStack output = craftingRecipe.getResultItem(minecraft.level.registryAccess());
            if (output.isEmpty()) {
                return;
            }
            target = new CraftableBigItemStack(output.copy(), craftingRecipe);
            target.count = 0;
            recipesToOrder.add(target);
        }
        requestCraftable(target, amount);
    }

    private void refreshSearchResults(boolean scrollBackUp) {
        displayedItems.clear();
        if (scrollBackUp) {
            itemScroll.startWithValue(0);
        }

        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot == null) {
            clampScrollBar();
            return;
        }

        String value = searchBox.getValue().toLowerCase(Locale.ROOT);
        boolean modSearch;
        boolean tagSearch = false;
        if ((modSearch = value.startsWith("@")) || (tagSearch = value.startsWith("#"))) {
            value = value.substring(1);
        }
        String filterValue = value;

        for (BigItemStack entry : snapshot.summary().getStacksByCount()) {
            ItemStack stack = entry.stack;
            if (filterValue.isBlank()) {
                displayedItems.add(entry);
                continue;
            }
            if (modSearch) {
                String namespace = stack.getItemHolder().unwrapKey().map(key -> key.location().getNamespace()).orElse("");
                if (namespace.contains(filterValue)) {
                    displayedItems.add(entry);
                }
                continue;
            }
            if (tagSearch) {
                if (stack.getTags().anyMatch(key -> key.location().toString().contains(filterValue))) {
                    displayedItems.add(entry);
                }
                continue;
            }
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String path = stack.getItemHolder().unwrapKey().map(key -> key.location().getPath()).orElse("");
            if (name.contains(filterValue) || path.contains(filterValue)) {
                displayedItems.add(entry);
            }
        }

        clampScrollBar();
        updateCraftableAmounts();
    }

    private void renderItemEntry(GuiGraphics graphics, BigItemStack entry, boolean isHovered, boolean renderingOrders) {
        int customCount = entry.count;
        ItemStack stackWithCount = entry.stack.copyWithCount(Math.clamp(customCount, 1, entry.stack.getMaxStackSize()));

        if (!renderingOrders) {
            BigItemStack order = getOrderForItem(entry.stack);
            if (entry.count < BigItemStack.INF && order != null) {
                customCount = Math.max(0, customCount - order.count);
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, 0, 0);
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        float hoverScale = isHovered ? 1.075f : 1f;
        pose.translate((COL_WIDTH - 18) / 2.0, (ROW_HEIGHT - 18) / 2.0, 0);
        pose.translate(9, 9, 0);
        pose.scale(hoverScale, hoverScale, hoverScale);
        pose.translate(-9, -9, 0);
        if (customCount != 0) {
            GuiGameElement.of(stackWithCount).render(graphics);
        }
        pose.popPose();

        pose.pushPose();
        pose.translate(0, 0, 190);
        if (customCount != 0) {
            graphics.renderItemDecorations(font, stackWithCount, 1, 1, "");
        }
        pose.translate(0, 0, 10);
        if (customCount > 1) {
            drawItemCount(graphics, customCount);
        }
        pose.popPose();
    }

    private void drawItemCount(GuiGraphics graphics, int count) {
        String text = count >= 1_000_000 ? (count / 1_000_000) + "m"
                : count >= 10_000 ? (count / 1_000) + "k"
                : count >= 1_000 ? ((count * 10) / 1_000) / 10f + "k"
                : count >= 100 ? Integer.toString(count)
                : " " + count;

        if (count >= BigItemStack.INF) {
            text = "+";
        }
        if (text.isBlank()) {
            return;
        }

        int x = (int) Math.floor(-text.length() * 2.5);
        for (char c : text.toCharArray()) {
            int xOffset = switch (c) {
                case '.' -> 60;
                case 'k' -> 64;
                case 'm' -> 70;
                case '+' -> 84;
                default -> (c - '0') * 6;
            };
            int spriteWidth = switch (c) {
                case '.' -> 3;
                case 'm' -> 7;
                case '+' -> 9;
                default -> NUMBERS.getWidth();
            };
            if (c == ' ') {
                x += 4;
                continue;
            }
            RenderSystem.enableBlend();
            graphics.blit(NUMBERS.location, 14 + x, 10, 0, NUMBERS.getStartX() + xOffset, NUMBERS.getStartY(),
                    spriteWidth, NUMBERS.getHeight(), 256, 256);
            x += spriteWidth - 1;
        }
    }

    private BigItemStack getOrderForItem(ItemStack stack) {
        for (BigItemStack entry : itemsToOrder) {
            if (ItemStack.isSameItemSameComponents(stack, entry.stack)) {
                return entry;
            }
        }
        return null;
    }

    private void revalidateOrders() {
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot == null) {
            itemsToOrder.clear();
            return;
        }
        InventorySummary summary = snapshot.summary();
        itemsToOrder.removeIf(entry -> {
            entry.count = Math.min(summary.getCountOf(entry.stack), entry.count);
            return entry.count <= 0;
        });
    }

    private HoveredSlot getHoveredSlot(int mouseX, int mouseY) {
        int adjustedX = mouseX + 1;
        if (adjustedX < itemsX || adjustedX >= itemsX + COLS * COL_WIDTH) {
            return HoveredSlot.NONE;
        }

        if (mouseY >= orderY && mouseY < orderY + ROW_HEIGHT) {
            int col = (adjustedX - itemsX) / COL_WIDTH;
            if (col >= 0 && col < itemsToOrder.size()) {
                return new HoveredSlot(HoveredArea.ORDER, col);
            }
            return HoveredSlot.NONE;
        }

        if (mouseY >= orderY - 31 && mouseY < orderY - 31 + ROW_HEIGHT) {
            int jeiX = leftPos + (windowWidth - COL_WIDTH * recipesToOrder.size()) / 2 + 1;
            int col = Mth.floorDiv(adjustedX - jeiX, COL_WIDTH);
            if (col >= 0 && col < recipesToOrder.size()) {
                return new HoveredSlot(HoveredArea.RECIPE, col);
            }
        }

        if (mouseY < topPos + 16 || mouseY > topPos + windowHeight - 80 || !itemScroll.settled()) {
            return HoveredSlot.NONE;
        }

        int localY = mouseY - itemsY;
        int row = Mth.floor((localY - 4) / (float) ROW_HEIGHT + itemScroll.getChaseTarget());
        int col = (adjustedX - itemsX) / COL_WIDTH;
        int slot = row * COLS + col;
        if (slot < 0 || slot >= displayedItems.size()) {
            return HoveredSlot.NONE;
        }
        return new HoveredSlot(HoveredArea.ITEM, slot);
    }

    private boolean isConfirmHovered(int mouseX, int mouseY) {
        int confirmX = leftPos + 143;
        int confirmY = topPos + windowHeight - 39;
        return mouseX >= confirmX && mouseX < confirmX + 78 && mouseY >= confirmY && mouseY < confirmY + 18;
    }

    private Component getTroubleshootingMessage() {
        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot == null) {
            return CreateLang.translateDirect("gui.stock_keeper.checking_stocks");
        }
        if (snapshot.summary().isEmpty()) {
            return searchBox.getValue().isBlank()
                    ? CreateLang.translateDirect("gui.stock_keeper.inventories_empty")
                    : CreateLang.translateDirect("gui.stock_keeper.no_search_results");
        }
        return CreateLang.translateDirect("gui.stock_keeper.no_search_results");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean leftClick = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean rightClick = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        if (rightClick && searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setValue("");
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            searchBox.setFocused(true);
            return true;
        }

        if (addressBox.isFocused()) {
            boolean result = addressBox.mouseClicked(mouseX, mouseY, button);
            if (addressBox.isHovered() || result)
                return result;
            addressBox.setFocused(false);
        }
        if (searchBox.isFocused()) {
            if (searchBox.isHovered())
                return searchBox.mouseClicked(mouseX, mouseY, button);
            searchBox.setFocused(false);
        }

        int barX = itemsX + COLS * COL_WIDTH - 1;
        if (getMaxScroll() > 0 && leftClick && mouseX > barX && mouseX <= barX + 8
                && mouseY > topPos + 15 && mouseY < topPos + windowHeight - 82) {
            scrollHandleActive = true;
            if (minecraft != null && minecraft.isWindowActive()) {
                GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), 208897, GLFW.GLFW_CURSOR_HIDDEN);
            }
            return true;
        }

        if (itemScroll.getChaseTarget() == 0 && leftClick && mouseY > besideSearchButtonY && mouseY <= besideSearchButtonY + 15) {
            // Jei Sync Mode
            if (Mods.JEI.isLoaded() && mouseX > jeiSyncX && mouseX <= jeiSyncX + 15) {
                SearchSyncMode.cycleConfig();
                refreshSearchNextTick = true;
                moveToTopNextTick = true;
                syncJEI(false);
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
                return true;
            }
        }

        if (leftClick && isConfirmHovered((int) mouseX, (int) mouseY)) {
            sendOrder();
            playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
            return true;
        }

        HoveredSlot hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);
        if (hoveredSlot.isNone() || (!leftClick && !rightClick)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int transfer = hasShiftDown() ? 64 : hasControlDown() ? 10 : 1;
        if (hoveredSlot.area() == HoveredArea.RECIPE) {
            CraftableBigItemStack craftable = recipesToOrder.get(hoveredSlot.index());
            if (rightClick && craftable.count == 0) {
                recipesToOrder.remove(craftable);
                return true;
            }
            requestCraftable(craftable, rightClick ? -transfer : transfer);
            return true;
        }

        if (hoveredSlot.area() == HoveredArea.ORDER) {
            BigItemStack entry = itemsToOrder.get(hoveredSlot.index());
            entry.count -= transfer;
            if (entry.count <= 0) {
                itemsToOrder.remove(hoveredSlot.index());
            }
            updateCraftableAmounts();
            return true;
        }

        BigItemStack entry = displayedItems.get(hoveredSlot.index());
        if (rightClick) {
            BigItemStack existingOrder = getOrderForItem(entry.stack);
            if (existingOrder != null) {
                existingOrder.count -= transfer;
                if (existingOrder.count <= 0) {
                    itemsToOrder.remove(existingOrder);
                }
                updateCraftableAmounts();
            }
            return true;
        }

        addOrder(entry, transfer);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollHandleActive && minecraft.isWindowActive()) {
            scrollHandleActive = false;
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), 208897, GLFW.GLFW_CURSOR_NORMAL);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (pButton != GLFW.GLFW_MOUSE_BUTTON_LEFT || !scrollHandleActive)
            return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);

        Window window = minecraft.getWindow();
        double scaleX = window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double scaleY = window.getGuiScaledHeight() / (double) window.getScreenHeight();

        int windowH = windowHeight - 92;
        int totalH = getMaxScroll() * ROW_HEIGHT + windowH;
        int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));

        int minY = getGuiTop() + 15 + barSize / 2;
        int maxY = getGuiTop() + 15 + windowH - barSize / 2;

        if (barSize >= windowH - 2)
            return true;

        int barX = itemsX + COLS * COL_WIDTH;
        double target = (pMouseY - getGuiTop() - 15 - barSize / 2.0) * totalH / (windowH - 2) / ROW_HEIGHT;
        itemScroll.chase(Mth.clamp(target, 0, getMaxScroll()), 0.8, Chaser.EXP);

        if (minecraft.isWindowActive()) {
            double forceX = (barX + 2) / scaleX;
            double forceY = Mth.clamp(pMouseY, minY, maxY) / scaleY;
            GLFW.glfwSetCursorPos(window.getWindow(), forceX, forceY);
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
            return true;

        HoveredSlot hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);
        if (hoveredSlot.isNone() || (hoveredSlot.area() == HoveredArea.ITEM && !hasShiftDown() && getMaxScroll() != 0)) {
            float newTarget = Mth.clamp(itemScroll.getChaseTarget() + (float) Math.ceil(Math.abs(scrollY)) * (float) -Math.signum(scrollY), 0, getMaxScroll());
            itemScroll.chase(newTarget, 0.5, Chaser.EXP);
            return true;
        }

        int transfer = (int) Math.ceil(Math.abs(scrollY)) * (hasControlDown() ? 10 : 1);
        boolean remove = scrollY < 0;

        if (hoveredSlot.area() == HoveredArea.RECIPE) {
            requestCraftable(recipesToOrder.get(hoveredSlot.index()), remove ? -transfer : transfer);
            return true;
        }

        if (hoveredSlot.area() == HoveredArea.ORDER) {
            BigItemStack entry = itemsToOrder.get(hoveredSlot.index());
            entry.count += remove ? -transfer : transfer;
            if (entry.count <= 0) {
                itemsToOrder.remove(hoveredSlot.index());
            }
            updateCraftableAmounts();
            return true;
        }

        BigItemStack source = displayedItems.get(hoveredSlot.index());
        if (remove) {
            BigItemStack existing = getOrderForItem(source.stack);
            if (existing != null) {
                existing.count -= transfer;
                if (existing.count <= 0) {
                    itemsToOrder.remove(existing);
                }
                updateCraftableAmounts();
            }
            return true;
        }

        addOrder(source, transfer);
        return true;
    }

    private void clampScrollBar() {
        float clamped = Mth.clamp(itemScroll.getChaseTarget(), 0, getMaxScroll());
        if (clamped != itemScroll.getChaseTarget()) {
            itemScroll.startWithValue(clamped);
        }
    }

    private int getMaxScroll() {
        int visibleHeight = windowHeight - 84;
        int totalRows = 2 + Mth.ceil(displayedItems.size() / (float) COLS);
        return (int) Math.max(0, (totalRows * ROW_HEIGHT - visibleHeight + 50) / (float) ROW_HEIGHT);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        String previous = searchBox.getValue();
        if (addressBox.isFocused() && addressBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (!searchBox.charTyped(codePoint, modifiers)) {
            return false;
        }
        if (!previous.equals(searchBox.getValue())) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            syncJEI(false);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!addressBox.isFocused() && !searchBox.isFocused() && minecraft.options.keyChat.matches(keyCode, scanCode)) {
            searchBox.setFocused(true);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER && hasShiftDown()) {
            sendOrder();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }
        if (addressBox.isFocused() && addressBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        String previous = searchBox.getValue();
        if (!searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return searchBox.isFocused() && searchBox.isVisible() && keyCode != GLFW.GLFW_KEY_ESCAPE
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!previous.equals(searchBox.getValue())) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            syncJEI(false);
        }
        return true;
    }

    private void addOrder(BigItemStack source, int amount) {
        BigItemStack existing = getOrderForItem(source.stack);
        if (existing == null) {
            if (itemsToOrder.size() >= COLS) {
                return;
            }
            existing = new BigItemStack(source.stack.copyWithCount(1), 0);
            itemsToOrder.add(existing);
        }
        existing.count = Math.min(source.count, existing.count + amount);
        updateCraftableAmounts();
    }

    private void sendOrder() {
        revalidateOrders();
        if (itemsToOrder.isEmpty()) {
            return;
        }
        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(BigItemStack.duplicateWrappers(itemsToOrder));
        if (canRequestCraftingPackage && !itemsToOrder.isEmpty() && !recipesToOrder.isEmpty()) {
            List<CraftingEntry> craftList = new ArrayList<>();
            for (CraftableBigItemStack craftable : recipesToOrder) {
                if (!(craftable.recipe instanceof CraftingRecipe craftingRecipe) || minecraft == null || minecraft.level == null) {
                    continue;
                }
                int craftedCount = 0;
                int targetCount = craftable.count / craftable.getOutputCount(minecraft.level);
                List<BigItemStack> mutableOrder = BigItemStack.duplicateWrappers(itemsToOrder);
                while (craftedCount < targetCount) {
                    PackageOrder pattern = new PackageOrder(
                            FactoryPanelScreen.convertRecipeToPackageOrderContext(craftingRecipe, mutableOrder, true)
                    );
                    int maxCrafts = targetCount - craftedCount;
                    int availableCrafts = 0;
                    boolean itemsExhausted = false;
                    while (availableCrafts < maxCrafts && !itemsExhausted) {
                        List<BigItemStack> previousSnapshot = BigItemStack.duplicateWrappers(mutableOrder);
                        itemsExhausted = true;
                        boolean failedPattern = false;
                        for (BigItemStack patternStack : pattern.stacks()) {
                            if (patternStack.stack.isEmpty()) {
                                continue;
                            }
                            boolean matched = false;
                            for (BigItemStack ordered : mutableOrder) {
                                if (!ItemStack.isSameItemSameComponents(ordered.stack, patternStack.stack) || ordered.count == 0) {
                                    continue;
                                }
                                ordered.count -= 1;
                                itemsExhausted = false;
                                matched = true;
                                break;
                            }
                            if (!matched) {
                                mutableOrder = previousSnapshot;
                                failedPattern = true;
                                break;
                            }
                        }
                        if (failedPattern) {
                            break;
                        }
                        availableCrafts++;
                    }
                    if (availableCrafts == 0) {
                        break;
                    }
                    craftList.add(new CraftingEntry(pattern, availableCrafts));
                    craftedCount += availableCrafts;
                }
            }
            order = new PackageOrderWithCrafts(order.orderedStacks(), craftList);
        }
        PacketDistributor.sendToServer(new PortablePackageOrderRequestPacket(
                menu.networkId,
                order,
                addressBox.getValue()
        ));
        itemsToOrder.clear();
        recipesToOrder.clear();
        PacketDistributor.sendToServer(new PortableStockRequestPacket(menu.networkId));
        successTicks = 1;
    }

    private void requestCraftable(CraftableBigItemStack craftable, int requestedDifference) {
        boolean takeOrdersAway = requestedDifference < 0;
        if (takeOrdersAway) {
            requestedDifference = Math.max(-craftable.count, requestedDifference);
        }
        if (requestedDifference == 0 || minecraft == null || minecraft.level == null) {
            return;
        }

        PortableStockTickerClientData.Snapshot snapshot = PortableStockTickerClientData.get(menu.networkId);
        if (snapshot == null) {
            return;
        }

        InventorySummary availableItems = snapshot.summary().copy();
        Function<ItemStack, Integer> countModifier = stack -> {
            BigItemStack ordered = getOrderForItem(stack);
            return ordered == null ? 0 : -ordered.count;
        };

        if (takeOrdersAway) {
            availableItems = new InventorySummary();
            for (BigItemStack ordered : itemsToOrder) {
                availableItems.add(ordered.stack, ordered.count);
            }
            countModifier = stack -> 0;
        }

        Pair<Integer, List<List<BigItemStack>>> craftingResult =
                maxCraftable(craftable, availableItems, countModifier, takeOrdersAway ? -1 : COLS - itemsToOrder.size());
        int outputCount = craftable.getOutputCount(minecraft.level);
        int adjustToRecipeAmount = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount) * outputCount;
        int maxCraftable = Math.min(adjustToRecipeAmount, craftingResult.getFirst());
        if (maxCraftable == 0) {
            return;
        }

        craftable.count += takeOrdersAway ? -maxCraftable : maxCraftable;
        for (List<BigItemStack> list : craftingResult.getSecond()) {
            int remaining = maxCraftable / outputCount;
            for (BigItemStack entry : list) {
                if (remaining <= 0) {
                    break;
                }
                int toTransfer = Math.min(remaining, entry.count);
                BigItemStack order = getOrderForItem(entry.stack);
                if (takeOrdersAway) {
                    if (order != null) {
                        order.count -= toTransfer;
                        if (order.count == 0) {
                            itemsToOrder.remove(order);
                        }
                    }
                } else {
                    if (order == null) {
                        order = new BigItemStack(entry.stack.copyWithCount(1), 0);
                        itemsToOrder.add(order);
                    }
                    order.count += toTransfer;
                }
                remaining -= entry.count;
            }
        }

        updateCraftableAmounts();
    }

    private void updateCraftableAmounts() {
        if (minecraft == null || minecraft.level == null) {
            canRequestCraftingPackage = false;
            return;
        }

        InventorySummary usedItems = new InventorySummary();
        InventorySummary availableItems = new InventorySummary();
        for (BigItemStack ordered : itemsToOrder) {
            availableItems.add(ordered.stack, ordered.count);
        }

        for (CraftableBigItemStack craftable : recipesToOrder) {
            Pair<Integer, List<List<BigItemStack>>> craftingResult =
                    maxCraftable(craftable, availableItems, stack -> -usedItems.getCountOf(stack), -1);
            int maxCraftable = craftingResult.getFirst();
            int outputCount = craftable.getOutputCount(minecraft.level);
            craftable.count = Math.min(craftable.count, maxCraftable);
            for (List<BigItemStack> list : craftingResult.getSecond()) {
                int remaining = craftable.count / outputCount;
                for (BigItemStack entry : list) {
                    if (remaining <= 0) {
                        break;
                    }
                    usedItems.add(entry.stack, Math.min(remaining, entry.count));
                    remaining -= entry.count;
                }
            }
        }

        canRequestCraftingPackage = false;
        for (BigItemStack ordered : itemsToOrder) {
            if (usedItems.getCountOf(ordered.stack) != ordered.count) {
                return;
            }
        }
        canRequestCraftingPackage = !recipesToOrder.isEmpty();
    }

    private Pair<Integer, List<List<BigItemStack>>> maxCraftable(CraftableBigItemStack craftable,
                                                                  InventorySummary summary,
                                                                  Function<ItemStack, Integer> countModifier,
                                                                  int newTypeLimit) {
        if (minecraft == null || minecraft.level == null) {
            return Pair.of(0, List.of());
        }

        List<Ingredient> ingredients = craftable.getIngredients();
        List<List<BigItemStack>> validEntriesByIngredient = new ArrayList<>();
        List<BigItemStack> alreadyCreated = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<BigItemStack> valid = new ArrayList<>();
            for (List<BigItemStack> list : summary.getItemMap().values()) {
                entries:
                for (BigItemStack entry : list) {
                    if (!ingredient.test(entry.stack)) {
                        continue;
                    }
                    for (BigItemStack visited : alreadyCreated) {
                        if (!ItemStack.isSameItemSameComponents(visited.stack, entry.stack)) {
                            continue;
                        }
                        valid.add(visited);
                        continue entries;
                    }
                    BigItemStack asBis = new BigItemStack(entry.stack, summary.getCountOf(entry.stack) + countModifier.apply(entry.stack));
                    if (asBis.count > 0) {
                        valid.add(asBis);
                        alreadyCreated.add(asBis);
                    }
                }
            }
            if (valid.isEmpty()) {
                return Pair.of(0, List.of());
            }
            valid.sort((left, right) -> -Integer.compare(summary.getCountOf(left.stack), summary.getCountOf(right.stack)));
            validEntriesByIngredient.add(valid);
        }

        if (newTypeLimit != -1) {
            int toRemove = (int) validEntriesByIngredient.stream()
                    .flatMap(List::stream)
                    .filter(entry -> getOrderForItem(entry.stack) == null)
                    .distinct()
                    .count() - newTypeLimit;
            for (int i = 0; i < toRemove; i++) {
                removeLeastEssentialItemStack(validEntriesByIngredient);
            }
        }

        validEntriesByIngredient = resolveIngredientAmounts(validEntriesByIngredient);
        int minCount = Integer.MAX_VALUE;
        for (List<BigItemStack> list : validEntriesByIngredient) {
            int sum = 0;
            for (BigItemStack entry : list) {
                sum += entry.count;
            }
            minCount = Math.min(sum, minCount);
        }
        if (minCount == 0) {
            return Pair.of(0, List.of());
        }

        int outputCount = craftable.getOutputCount(minecraft.level);
        return Pair.of(minCount * outputCount, validEntriesByIngredient);
    }

    private void removeLeastEssentialItemStack(List<List<BigItemStack>> validIngredients) {
        List<BigItemStack> longest = null;
        int most = 0;
        for (List<BigItemStack> list : validIngredients) {
            int count = (int) list.stream().filter(entry -> getOrderForItem(entry.stack) == null).count();
            if (longest != null && count <= most) {
                continue;
            }
            longest = list;
            most = count;
        }
        if (longest == null || longest.isEmpty()) {
            return;
        }

        BigItemStack chosen = null;
        for (int i = 0; i < longest.size(); i++) {
            BigItemStack entry = longest.get(longest.size() - 1 - i);
            if (getOrderForItem(entry.stack) != null) {
                continue;
            }
            chosen = entry;
            break;
        }
        if (chosen == null) {
            return;
        }
        for (List<BigItemStack> list : validIngredients) {
            list.remove(chosen);
        }
    }

    private List<List<BigItemStack>> resolveIngredientAmounts(List<List<BigItemStack>> validIngredients) {
        List<List<BigItemStack>> resolvedIngredients = new ArrayList<>();
        for (int i = 0; i < validIngredients.size(); i++) {
            resolvedIngredients.add(new ArrayList<>());
        }

        boolean everythingTaken = false;
        while (!everythingTaken) {
            everythingTaken = true;
            for (int i = 0; i < validIngredients.size(); i++) {
                List<BigItemStack> list = validIngredients.get(i);
                List<BigItemStack> resolvedList = resolvedIngredients.get(i);
                ingredientLoop:
                for (BigItemStack bigItemStack : list) {
                    if (bigItemStack.count == 0) {
                        continue;
                    }
                    bigItemStack.count -= 1;
                    everythingTaken = false;
                    for (BigItemStack resolvedItemStack : resolvedList) {
                        if (ItemStack.isSameItemSameComponents(resolvedItemStack.stack, bigItemStack.stack)) {
                            resolvedItemStack.count++;
                            continue ingredientLoop;
                        }
                    }
                    resolvedList.add(new BigItemStack(bigItemStack.stack, 1));
                    continue ingredientLoop;
                }
            }
        }

        return resolvedIngredients;
    }

    private boolean shouldSyncFromJEI() {
        return Mods.JEI.isLoaded()
                && CreateJEI.runtime != null
                && CreateJEI.runtime.getIngredientListOverlay().hasKeyboardFocus()
                && !previousJEISearchText.equals(CreateJEI.runtime.getIngredientFilter().getFilterText());
    }

    private void syncJEI(boolean fromJei) {
        if (!Mods.JEI.isLoaded() || CreateJEI.runtime == null) {
            return;
        }
        SearchSyncMode mode = AllConfigs.client().syncRecipeViewerSearch.get();
        if (mode == SearchSyncMode.NONE) {
            return;
        }
        IIngredientFilter filter = CreateJEI.runtime.getIngredientFilter();
        if (mode.isBothOr(SearchSyncMode.SYNC_FROM_JEI) && fromJei) {
            previousJEISearchText = filter.getFilterText();
            searchBox.setValue(previousJEISearchText);
        } else if (mode.isBothOr(SearchSyncMode.SYNC_FROM_STOCK_KEEPER) && !fromJei) {
            filter.setFilterText(searchBox.getValue());
        }
    }

    private void stretchTexture(GuiGraphics graphics, int x, int y, int width, int height, AllGuiTextures texture) {
        graphics.blit(
                texture.location,
                x,
                y,
                width,
                height,
                texture.getStartX(),
                texture.getStartY(),
                texture.getWidth(),
                texture.getHeight(),
                256,
                256
        );
    }

    private enum HoveredArea {
        NONE,
        ITEM,
        ORDER,
        RECIPE
    }

    private record HoveredSlot(HoveredArea area, int index) {
        private static final HoveredSlot NONE = new HoveredSlot(HoveredArea.NONE, -1);

        private boolean isNone() {
            return area == HoveredArea.NONE;
        }
    }

}
