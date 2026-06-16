package com.shrhang.shhs_create_core.compat.jei.portable_stock_ticker;

import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerScreen;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;

import java.util.Optional;

public final class PortableStockTickerGuiContainerHandler implements IGuiContainerHandler<PortableStockTickerScreen> {
    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory builder, PortableStockTickerScreen screen, double mouseX, double mouseY) {
        return screen.getHoveredIngredient((int) mouseX, (int) mouseY)
                .flatMap(pair -> builder.createBuilder(pair.getFirst()).buildWithArea(pair.getSecond()));
    }
}
