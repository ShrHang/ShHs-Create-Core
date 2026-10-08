package io.github.shrhang.shhs_create_core;

import com.wintercogs.beyonddimensions.api.dimensionnet.NetPermissionlevel;
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
        public final ModConfigSpec.IntValue hostilityDebitCardUseDurationTicks;
        public final ModConfigSpec.IntValue hostilityDebitCardCooldownTicks;
        public final ModConfigSpec.BooleanValue hostilityDebitCardGenerateParticles;
        public final ModConfigSpec.DoubleValue wizardMaxManaPerLev;
        public final ModConfigSpec.DoubleValue wizardManaRegenPerLev;
        public final ModConfigSpec.DoubleValue wizardCooldownReductionPerLev;
        public final ModConfigSpec.BooleanValue wizardImbueEquipment;

        public final ModConfigSpec.IntValue dimensionParcelStationMaxPerNetwork;
        public final ModConfigSpec.EnumValue<NetPermissionlevel> dimensionParcelStationConfigurePermission;

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
            hostilityDebitCardUseDurationTicks = builder
                    .comment("The use time in ticks required to swap profiles with the Hostility Debit Card. Set to 0 for immediate use.")
                    .defineInRange("hostilityDebitCardUseDurationTicks", 20, 0, 72000);
            hostilityDebitCardCooldownTicks = builder
                    .comment("The cooldown in ticks applied after a successful Hostility Debit Card profile swap. Set to 0 to disable it.")
                    .defineInRange("hostilityDebitCardCooldownTicks", 40, 0, 72000);
            hostilityDebitCardGenerateParticles = builder
                    .comment("Whether a successful Hostility Debit Card profile swap generates particles.")
                    .define("hostilityDebitCardGenerateParticles", true);
            builder.push("trait");
            builder.push("wizard");
            wizardImbueEquipment = builder
                    .comment("Imbue eligible existing equipment once when the Wizard trait is first initialized. Uses native arcane anvil restrictions; existing spells are preserved.")
                    .define("wizardImbueEquipment", true);
            wizardMaxManaPerLev = builder
                    .comment("The maximum mana increase per level of Wizard Trait. The maximum mana increase is calculated as trait level * WizardMaxManaPerLv.")
                    .defineInRange("wizardMaxManaPerLev", 200.0, 0, Float.MAX_VALUE);
            wizardManaRegenPerLev = builder
                    .comment("The mana regeneration increase per level of Wizard Trait. The mana regeneration increase is calculated as trait level * WizardManaRegenPerLv.")
                    .defineInRange("wizardManaRegenPerLev", 0.2, 0.0, Float.MAX_VALUE);
            wizardCooldownReductionPerLev = builder
                    .comment("Cooldown reduction attribute bonus per Wizard level. Uses the native cooldown soft cap; 0 disables this bonus.")
                    .defineInRange("wizardCooldownReductionPerLev", 0.1, 0.0, Float.MAX_VALUE);
            builder.pop(3);

            builder.push("logistics");
            builder.push("dimension_parcel_station");
            dimensionParcelStationMaxPerNetwork = builder
                    .comment("Maximum number of Dimension Parcel Stations bound to one dimension network.")
                    .comment("0 disables and unbinds all stations; -1 allows unlimited stations.")
                    .defineInRange("dimensionParcelStationMaxPerNetwork", -1, -1, Integer.MAX_VALUE);
            dimensionParcelStationConfigurePermission = builder
                    .comment("Minimum network permission required to change a Dimension Parcel Station's I/O settings.")
                    .defineEnum("dimensionParcelStationConfigurePermission", NetPermissionlevel.Manager);
            builder.pop(2);
        }
    }
}
