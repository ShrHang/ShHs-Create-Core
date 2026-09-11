package io.github.shrhang.shhs_create_core;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ShHsConfig {
    public static final Client CLIENT;
    public static final Server SERVER;
    static final ModConfigSpec clientSpec;
    static final ModConfigSpec serverSpec;

    static {
        Pair<?, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = (Client) pair.getLeft();
        clientSpec = pair.getRight();
        pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = (Server) pair.getLeft();
        serverSpec = pair.getRight();
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, serverSpec);
    }

    public static void registerClient(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, clientSpec);
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue isToleranceTooltip;
        public final ModConfigSpec.BooleanValue disableContraptionDrillBreakParticles;
        Client(ModConfigSpec.Builder builder) {
            builder.push("magic");
            isToleranceTooltip = builder
                    .comment("Whether to show spell tolerance requirement in spell tooltips.")
                    .define("enableToleranceTooltip", true);
            builder.pop();
            builder.push("performance");
            disableContraptionDrillBreakParticles = builder
                    .comment("Whether to suppress block-breaking particles from moving contraption drills. Sounds and drops are unaffected.")
                    .define("disableContraptionDrillBreakParticles", true);
            builder.pop();
        }
    }

    public static class Server {
        public final ModConfigSpec.BooleanValue limitContraptionDrillBreakSounds;
        public final ModConfigSpec.IntValue contraptionDrillBreakSoundIntervalTicks;
        public final ModConfigSpec.BooleanValue limitContraptionDrillHitSounds;
        public final ModConfigSpec.IntValue contraptionDrillHitSoundIntervalTicks;
        public final ModConfigSpec.IntValue scrollPrintingCost;

        public final ModConfigSpec.BooleanValue isToleranceRequired;
        public final ModConfigSpec.DoubleValue rarityCoefficient;
        public final ModConfigSpec.DoubleValue consumesManaCoefficient;
        public final ModConfigSpec.DoubleValue mobManaRegenMultiplier;

        public final ModConfigSpec.DoubleValue realityTraitScale;
        public final ModConfigSpec.IntValue emptyTraitMinUseTicks;
        public final ModConfigSpec.DoubleValue wizardMaxManaPerLev;
        public final ModConfigSpec.DoubleValue wizardManaRegenPerLev;

        Server(ModConfigSpec.Builder builder) {
            builder.push("performance");
            limitContraptionDrillBreakSounds = builder
                    .comment("Limit default moving contraption drill break sounds per sound and 4x4x4 block cell. Custom block break sound hooks are unaffected.")
                    .define("limitContraptionDrillBreakSounds", true);
            contraptionDrillBreakSoundIntervalTicks = builder
                    .comment("Minimum server ticks between matching drill break sounds. 4 ticks is about 200 ms at 20 TPS.")
                    .defineInRange("contraptionDrillBreakSoundIntervalTicks", 4, 1, 100);
            limitContraptionDrillHitSounds = builder
                    .comment("Limit moving contraption drill hit sounds per sound and 4x4x4 block cell, independently of break sounds.")
                    .define("limitContraptionDrillHitSounds", true);
            contraptionDrillHitSoundIntervalTicks = builder
                    .comment("Minimum server ticks between matching drill hit sounds. Pitch and playback duration are unchanged.")
                    .defineInRange("contraptionDrillHitSoundIntervalTicks", 4, 1, 100);
            builder.pop();
            builder.push("compat");
            builder.push("enchantment_industry");
            scrollPrintingCost = builder
                    .comment("The cost of ink for printing a spell scroll per relative level in Enchantment Industry.")
                    .defineInRange("scrollPrintingCost", 250, 1, 1000);
            builder.pop(2);

            builder.push("magic");
            builder.push("player");
            isToleranceRequired = builder
                    .comment("Whether spell tolerance is required for casting spells.")
                    .define("isToleranceRequired", true);
            rarityCoefficient = builder
                    .comment("The coefficient of spell rarity in spell tolerance calculation. The required spell tolerance is calculated as relative spell level + RarityCoefficient * rarity value.")
                    .defineInRange("RarityCoefficient", 3.0, 0.0, 823);
            consumesManaCoefficient = builder
                    .comment("The coefficient for whether the spell consumes mana in spell tolerance calculation. The required spell tolerance is calculated as relative spell level - ConsumesManaCoefficient if the spell consumes mana.")
                    .defineInRange("ConsumesManaCoefficient", 1.0, 0.0, 823);
            builder.pop();
            builder.push("mob");
            mobManaRegenMultiplier = builder.worldRestart()
                    .defineInRange("mobManaRegenMultiplier", 1.0, 0.0, 100.0);
            builder.pop(2);

            builder.push("l2hostility");
            realityTraitScale = builder
                    .comment("The scale of reality trait in hostility calculation. The hostility increase from reality trait is calculated as reality trait level * RealityTraitScale.")
                    .defineInRange("realityTraitScale", 1.0, 0.0, 10000);
            emptyTraitMinUseTicks = builder
                    .comment("The minimum use time in ticks required for Empty Trait extraction. Set to 0 to allow immediate release.")
                    .defineInRange("emptyTraitMinUseTicks", 30, 0, 72000);
            builder.push("trait");
            builder.push("wizard");
            wizardMaxManaPerLev = builder
                    .comment("The maximum mana increase per level of Wizard Trait. The maximum mana increase is calculated as trait level * WizardMaxManaPerLv.")
                    .defineInRange("wizardMaxManaPerLev", 200.0, 0, Float.MAX_VALUE);
            wizardManaRegenPerLev = builder
                    .comment("The mana regeneration increase per level of Wizard Trait. The mana regeneration increase is calculated as trait level * WizardManaRegenPerLv.")
                    .defineInRange("wizardManaRegenPerLev", 0.2, 0.0, Float.MAX_VALUE);
            builder.pop(3);
        }
    }
}
