package io.github.shrhang.shhs_create_core.compat.jei.terminal;

import io.github.shrhang.shhs_create_core.content.logistics.terminal.DimensionLogisticsTerminalScreen;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;

import java.util.*;

public final class TerminalGuiHandler implements IGuiContainerHandler<DimensionLogisticsTerminalScreen> {
    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory builder,
            DimensionLogisticsTerminalScreen screen, double mouseX, double mouseY) {
        return screen.hoveredIngredient(mouseX, mouseY).flatMap(entry -> builder.createBuilder(entry.getKey()).buildWithArea(entry.getValue()));
    }
    @Override
    public List<Rect2i> getGuiExtraAreas(DimensionLogisticsTerminalScreen screen) { return List.of(); }
}
