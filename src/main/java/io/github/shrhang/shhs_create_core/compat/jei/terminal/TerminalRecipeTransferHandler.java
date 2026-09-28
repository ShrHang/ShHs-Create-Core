package io.github.shrhang.shhs_create_core.compat.jei.terminal;

import io.github.shrhang.shhs_create_core.content.logistics.terminal.*;
import io.github.shrhang.shhs_create_core.content.registries.ShHsMenuTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class TerminalRecipeTransferHandler implements IRecipeTransferHandler<DimensionLogisticsTerminalMenu, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helpers;
    public TerminalRecipeTransferHandler(IRecipeTransferHandlerHelper helpers) { this.helpers = helpers; }
    @Override public Class<? extends DimensionLogisticsTerminalMenu> getContainerClass() { return DimensionLogisticsTerminalMenu.class; }
    @Override public Optional<MenuType<DimensionLogisticsTerminalMenu>> getMenuType() { return Optional.of(ShHsMenuTypes.DIMENSION_LOGISTICS_TERMINAL.get()); }
    @Override public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() { return RecipeTypes.CRAFTING; }

    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(DimensionLogisticsTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe,
                                                         IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (TerminalCrafting.plan(recipe.value(), menu.availableForCrafting()) == null)
            return helpers.createUserErrorWithTooltip(TerminalData.text("missing_materials"));
        if (doTransfer) {
            CompoundTag tag = new CompoundTag(); tag.putString("Recipe", recipe.id().toString()); tag.putBoolean("Max", maxTransfer);
            TerminalPackets.request(menu, TerminalPackets.FILL, tag);
        }
        return null;
    }
}
