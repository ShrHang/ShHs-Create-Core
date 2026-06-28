package com.shrhang.shhs_create_core.compat.create_enchantment_industry;

import com.shrhang.shhs_create_core.compat.create_enchantment_industry.fluid.printing.ScollPrintingBehaviour;
import com.shrhang.shhs_create_core.compat.create_enchantment_industry.integration.jei.category.printing.ScollPrintingRecipeJEI;
import mezz.jei.api.registration.IRecipeRegistration;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviour;
import plus.dragons.createenchantmentindustry.integration.jei.category.printing.PrintingCategory;

public class CreateEnchantmentIndustry {
    private static boolean isRegistered = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CreateEnchantmentIndustry::onServerStart);
    }

    public static void jei(IRecipeRegistration registration) {
        registration.addRecipes(PrintingCategory.TYPE, ScollPrintingRecipeJEI.listAll());
    }

    private static void onServerStart(final ServerAboutToStartEvent event) {
        if (!isRegistered) {
            PrintingBehaviour.register(ScollPrintingBehaviour::create);
            isRegistered = true;
        }
    }
}
