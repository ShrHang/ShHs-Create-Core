package io.github.shrhang.shhs_create_core.mixin.fluidlogistics.content.processing.cooling;

import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import plus.dragons.createdragonsplus.common.kinetics.fan.freezing.FreezingRecipe;
import plus.dragons.createdragonsplus.common.registry.CDPRecipes;
import plus.dragons.createdragonsplus.config.CDPConfig;

import java.util.List;
import java.util.Optional;

@Pseudo
@Mixin(targets = "com.yision.fluidlogistics.content.processing.cooling.BulkCoolingFanProcessingType", remap = false)
public abstract class BulkCoolingFanProcessingTypeMixin {
    @Inject(method = "canProcess", at = @At("RETURN"), cancellable = true)
    private void shhsc_c$canProcessFreezing(ItemStack stack, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ())
            return;

        if (shhsc_c$findFreezingRecipe(stack, level).isPresent())
            cir.setReturnValue(true);
    }

    @Inject(method = "process", at = @At("RETURN"), cancellable = true)
    private void shhsc_c$processFreezing(ItemStack stack, Level level, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (cir.getReturnValue() != null)
            return;

        shhsc_c$findFreezingRecipe(stack, level)
                .map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe.value(), false))
                .ifPresent(cir::setReturnValue);
    }

    @Unique
    private Optional<RecipeHolder<FreezingRecipe>> shhsc_c$findFreezingRecipe(ItemStack stack, Level level) {
        if (!CDPConfig.recipes().enableBulkFreezing.get())
            return Optional.empty();

        return level.getRecipeManager()
                .getRecipeFor(CDPRecipes.FREEZING.getType(), new SingleRecipeInput(stack), level);
    }
}
