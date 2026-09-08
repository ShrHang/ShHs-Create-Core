package io.github.shrhang.shhs_create_core.mixin.fluidlogistics.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.yision.fluidlogistics.content.processing.cooling.BulkCoolingRecipe;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plus.dragons.createdragonsplus.common.kinetics.fan.freezing.FreezingRecipe;
import plus.dragons.createdragonsplus.common.registry.CDPRecipes;
import plus.dragons.createdragonsplus.config.CDPConfig;
import plus.dragons.createdragonsplus.integration.CDPIntegrationContributions;
import plus.dragons.createdragonsplus.integration.jei.CDPJeiPlugin;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.yision.fluidlogistics.compat.jei.FluidLogisticsJEI", remap = false)
public abstract class FluidLogisticsJEIMixin {
    @Shadow
    private CreateRecipeCategory<BulkCoolingRecipe> bulkCooling;

    @Inject(method = "registerRecipes", at = @At("TAIL"))
    private void shhsc_c$addFreezingRecipesToBulkCooling(IRecipeRegistration registration, CallbackInfo ci) {
        if (!CDPConfig.recipes().enableBulkFreezing.get() || bulkCooling == null)
            return;

        RecipeManager recipeManager = CDPJeiPlugin.getRecipeManager();
        List<RecipeHolder<FreezingRecipe>> freezingRecipes = new ArrayList<>(
                recipeManager.getAllRecipesFor(CDPRecipes.FREEZING.getType()));
        CDPIntegrationContributions.gatherFreezingJeiRecipes(recipeManager, freezingRecipes);

        if (freezingRecipes.isEmpty())
            return;

        registration.addRecipes(bulkCooling.getRecipeType(), freezingRecipes.stream()
                .map(FluidLogisticsJEIMixin::shhsc_c$asBulkCoolingRecipe)
                .toList());
    }

    @Unique
    private static RecipeHolder<BulkCoolingRecipe> shhsc_c$asBulkCoolingRecipe(RecipeHolder<FreezingRecipe> recipe) {
        return new RecipeHolder<>(
                shhsc_c$bulkCoolingFreezingRecipeId(recipe.id()),
                new FreezingBulkCoolingRecipe(recipe.value()));
    }

    @Unique
    private static ResourceLocation shhsc_c$bulkCoolingFreezingRecipeId(ResourceLocation sourceId) {
        return ShHsCreateCore.rl("compat/fluidlogistics/fan_cooling/freezing/"
                + sourceId.getNamespace() + "/" + sourceId.getPath());
    }

    @Unique
    private static class FreezingBulkCoolingRecipe extends BulkCoolingRecipe {
        public FreezingBulkCoolingRecipe(FreezingRecipe recipe) {
            super(recipe.getParams());
        }
    }
}
