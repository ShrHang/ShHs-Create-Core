package io.github.shrhang.shhs_create_core.mixin.create.compat.jei.category;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.utility.CreateLang;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(value = CreateRecipeCategory.class, remap = false)
public abstract class CreateRecipeCategoryMixin {
    @Inject(method = "addStochasticTooltip", at = @At("HEAD"), cancellable = true)
    private static void shhsc_c$addStochasticTooltip(ProcessingOutput output, CallbackInfoReturnable<IRecipeSlotRichTooltipCallback> cir) {
        cir.setReturnValue((view, tooltip) -> {
            float chance = output.getChance();
            if (chance != 1.0F) {
                tooltip.add(CreateLang.translateDirect("recipe.processing.chance", shhsc_c$formatChance(chance))
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    @Unique
    private static String shhsc_c$formatChance(float chance) {
        return String.format(Locale.ROOT, "%.2f", chance * 100.0F);
    }
}
