package io.github.shrhang.shhs_create_core.content.data;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.createmod.catnip.gui.TextureSheetSegment;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum ShHsGuiTextures implements ScreenElement, TextureSheetSegment {
    // Dimension Logistics Terminal
    DIMENSION_LOGISTICS_TERMINAL_HEADER("dimension_logistics_terminal", 256, 20),
    DIMENSION_LOGISTICS_TERMINAL_CRAFT("dimension_logistics_terminal", 0, 32, 256, 62),
    DIMENSION_LOGISTICS_TERMINAL_BUFFER("dimension_logistics_terminal", 0, 96, 256, 4),
    DIMENSION_LOGISTICS_TERMINAL_PLAYER("dimension_logistics_terminal", 0, 112, 256, 89),
    DIMENSION_LOGISTICS_TERMINAL_BOTTOM("dimension_logistics_terminal", 0, 208, 256, 18),
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
        this.location = ShHsCreateCore.rl("textures/gui/" + location + ".png");
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
