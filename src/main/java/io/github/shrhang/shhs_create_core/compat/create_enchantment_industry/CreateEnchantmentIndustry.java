package io.github.shrhang.shhs_create_core.compat.create_enchantment_industry;

import io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.fluid.printing.ScollPrintingBehaviour;
import io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.integration.jei.category.printing.ScollPrintingRecipeJEI;
import mezz.jei.api.registration.IRecipeRegistration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import plus.dragons.createenchantmentindustry.api.registry.CEIRegistries;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviourProvider;
import plus.dragons.createenchantmentindustry.integration.jei.category.printing.PrintingCategory;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.MODID;

public class CreateEnchantmentIndustry {
    private static final DeferredRegister<PrintingBehaviourProvider> PRINTING_BEHAVIOURS =
            DeferredRegister.create(CEIRegistries.PRINTING_BEHAVIOUR_PROVIDER, MODID);

    static {
        PRINTING_BEHAVIOURS.register("scroll", () -> new PrintingBehaviourProvider(ScollPrintingBehaviour::create));
    }

    public static void register(IEventBus modEventBus) {
        PRINTING_BEHAVIOURS.register(modEventBus);
    }

    public static void jei(IRecipeRegistration registration) {
        registration.addRecipes(PrintingCategory.TYPE, ScollPrintingRecipeJEI.listAll());
    }
}
