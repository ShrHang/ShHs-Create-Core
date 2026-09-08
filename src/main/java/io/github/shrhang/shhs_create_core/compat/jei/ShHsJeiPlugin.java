package io.github.shrhang.shhs_create_core.compat.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import io.github.shrhang.shhs_create_core.compat.jei.category.FanMiracleCategory;
import io.github.shrhang.shhs_create_core.compat.jei.portable_stock_ticker.PortableStockTickerGuiContainerHandler;
import io.github.shrhang.shhs_create_core.compat.jei.portable_stock_ticker.PortableStockTickerTransferHandler;
import io.github.shrhang.shhs_create_core.content.data.ShHsLang;
import io.github.shrhang.shhs_create_core.content.kinetics.fan.processing.MiracleFanProcessingRecipe;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerScreen;
import io.github.shrhang.shhs_create_core.content.registries.ShHsFluids;
import io.github.shrhang.shhs_create_core.content.registries.ShHsRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.rl;

@JeiPlugin
public class ShHsJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = rl("jei_plugin");

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();

    private void loadCategories() {
        if (!allCategories.isEmpty())
            return;

        allCategories.add(new CreateRecipeCategory.Builder<>(MiracleFanProcessingRecipe.class)
                .addTypedRecipes(ShHsRecipeTypes.MIRACLE)
                .catalystStack(() -> {
                    ItemStack stack = AllBlocks.ENCASED_FAN.asStack();
                    stack.set(DataComponents.CUSTOM_NAME, ShHsLang.component("text", "fan_miracle.fan")
                            .withStyle(style -> style.withItalic(false)));
                    return stack;
                })
                .catalyst(() -> ShHsFluids.MIRACLE.getBucket().orElse(Items.BUCKET))
                .doubleItemIcon(AllItems.PROPELLER.get(), ShHsFluids.MIRACLE.getBucket().orElse(Items.BUCKET))
                .emptyBackground(178, 72)
                .build(rl("fan_miracle"), FanMiracleCategory::new));
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        loadCategories();
        registration.addRecipeCategories(allCategories.toArray(IRecipeCategory[]::new));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        loadCategories();
        allCategories.forEach(category -> category.registerRecipes(registration));

        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> () -> CreateEnchantmentIndustry.jei(registration));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        loadCategories();
        allCategories.forEach(category -> category.registerCatalysts(registration));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(PortableStockTickerScreen.class, new PortableStockTickerGuiContainerHandler());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new PortableStockTickerTransferHandler(registration.getJeiHelpers()));
    }
}
