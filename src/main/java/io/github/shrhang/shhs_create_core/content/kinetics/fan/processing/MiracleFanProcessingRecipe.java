package io.github.shrhang.shhs_create_core.content.kinetics.fan.processing;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import io.github.shrhang.shhs_create_core.content.registries.ShHsRecipeTypes;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class MiracleFanProcessingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

    public MiracleFanProcessingRecipe(ProcessingRecipeParams params) {
        super(ShHsRecipeTypes.MIRACLE, params);
    }

    @Override
    public boolean matches(SingleRecipeInput inv, Level level) {
        if (inv.isEmpty())
            return false;
        return ingredients.getFirst().test(inv.getItem(0));
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 12;
    }
}
