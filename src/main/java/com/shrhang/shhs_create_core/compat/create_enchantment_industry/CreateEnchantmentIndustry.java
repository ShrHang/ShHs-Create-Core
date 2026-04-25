package com.shrhang.shhs_create_core.compat.create_enchantment_industry;

import com.shrhang.shhs_create_core.compat.create_enchantment_industry.fluid.printing.ScollPrintingBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviour;

public class CreateEnchantmentIndustry {
    private static boolean isRegistered = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CreateEnchantmentIndustry::onServerStart);
    }

    @SubscribeEvent
    public static void onServerStart(final ServerAboutToStartEvent event) {
        if (!isRegistered) {
            PrintingBehaviour.register(ScollPrintingBehaviour::create);
            isRegistered = true;
        }
    }
}
