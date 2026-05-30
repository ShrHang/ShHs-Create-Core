package com.shrhang.shhs_create_core;

import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Client CLIENT;
    public static final Common COMMON;
    public static final Server SERVER;
    static final ModConfigSpec clientSpec;
    static final ModConfigSpec commonSpec;
    static final ModConfigSpec serverSpec;

    static {
        Pair<?, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = (Client) pair.getLeft();
        clientSpec = pair.getRight();
        pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = (Common) pair.getLeft();
        commonSpec = pair.getRight();
        pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = (Server) pair.getLeft();
        serverSpec = pair.getRight();
    }

    public static void init(ModContainer modContainer) {
        if (FMLEnvironment.dist.isClient()) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, clientSpec);
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
        modContainer.registerConfig(ModConfig.Type.COMMON, commonSpec);
        modContainer.registerConfig(ModConfig.Type.SERVER, serverSpec);
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue isToleranceTooltip;
        Client(ModConfigSpec.Builder builder) {
            builder.push("magic");
            isToleranceTooltip = builder
                    .comment("Whether to show spell tolerance requirement in spell tooltips.")
                    .define("enableToleranceTooltip", true);
            builder.pop();
        }
    }

    public static class Common {
        public final ModConfigSpec.BooleanValue isToleranceRequired;
        public final ModConfigSpec.DoubleValue rarityCoefficient;
        Common(ModConfigSpec.Builder builder) {
            builder.push("magic");
            isToleranceRequired = builder
                    .comment("Whether spell tolerance is required for casting spells.")
                    .define("isToleranceRequired", true);
            rarityCoefficient = builder
                    .comment("The coefficient of spell rarity in spell tolerance calculation. The required spell tolerance is calculated as spell level + RarityCoefficient * rarity value.")
                    .defineInRange("RarityCoefficient", 3.0, 0.0, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    public static class Server {
        public final ModConfigSpec.DoubleValue mobManaRegenMultiplier;
        public final ModConfigSpec.DoubleValue realityTraitScale;
        public final ModConfigSpec.IntValue scrollPrintingCost;
        Server(ModConfigSpec.Builder builder) {
            builder.push("enchantment_industry");
            scrollPrintingCost = builder
                    .comment("The cost of printing a spell scroll per lv in Enchantment Industry.")
                    .defineInRange("scrollPrintingCost", 250, 1, 1000);
            builder.pop();
            builder.push("l2hostility");
            mobManaRegenMultiplier = builder.worldRestart()
                    .defineInRange("mobManaRegenMultiplier", 1.0, 0.0, 100.0);
            realityTraitScale = builder
                    .comment("The scale of reality trait in hostility calculation. The hostility increase from reality trait is calculated as reality trait level * RealityTraitScale.")
                    .defineInRange("realityTraitScale", 1.0, 0.0, 10000);
            builder.pop();
        }
    }
}
