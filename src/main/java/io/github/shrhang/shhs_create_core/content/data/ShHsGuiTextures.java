package io.github.shrhang.shhs_create_core.content.data;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.createmod.catnip.gui.TextureSheetSegment;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum ShHsGuiTextures implements ScreenElement, TextureSheetSegment {
    // Dimension Logistics Terminal
    DIMENSION_LOGISTICS_TERMINAL_HEADER("create", "stock_keeper_categories", 32, 0, 192, 16),
    DIMENSION_LOGISTICS_TERMINAL_BACKGROUND("create", "stock_keeper_categories", 32, 32, 192, 16),
    DIMENSION_LOGISTICS_TERMINAL_CRAFT("dimension_logistics_terminal", 32, 0, 192, 56),
    DIMENSION_LOGISTICS_TERMINAL_PLAYER("dimension_logistics_terminal", 32, 64, 192, 90),
    DIMENSION_LOGISTICS_TERMINAL_BOTTOM("dimension_logistics_terminal", 32, 160, 192, 32),
    ;

    public final ResourceLocation location;
    private final int width;
    private final int height;
    private final int startX;
    private final int startY;

    ShHsGuiTextures(String location, int width, int height) {
        this(location, 0, 0, width, height);
    }

    ShHsGuiTextures(String location, int startX, int startY, int width, int height) {
        this(ShHsCreateCore.MODID, location, startX, startY, width, height);
    }

    ShHsGuiTextures(String namespace, String location, int startX, int startY, int width, int height) {
        this.location = ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/" + location + ".png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
    }

    @Override
    public int getStartX() {
        return this.startX;
    }

    @Override
    public int getStartY() {
        return this.startY;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, startX, startY, width, height);
    }

    @Override
    public ResourceLocation getLocation() {
        return this.location;
    }
}
