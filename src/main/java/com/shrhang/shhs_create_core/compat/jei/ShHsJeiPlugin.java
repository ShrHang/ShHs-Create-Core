package com.shrhang.shhs_create_core.compat.jei;

import com.shrhang.shhs_create_core.compat.Mods;
import com.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import com.shrhang.shhs_create_core.compat.jei.portable_stock_ticker.PortableStockTickerGuiContainerHandler;
import com.shrhang.shhs_create_core.compat.jei.portable_stock_ticker.PortableStockTickerTransferHandler;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

@JeiPlugin
public class ShHsJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = rl("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> () -> CreateEnchantmentIndustry.jei(registration));
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
