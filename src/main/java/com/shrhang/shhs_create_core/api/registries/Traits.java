package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.Config;
import com.shrhang.shhs_create_core.content.data.ShHsTraitBuilder;
import com.shrhang.shhs_create_core.content.traits.WizardTrait;
import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import dev.xkmc.l2hostility.content.config.TraitConfig;
import dev.xkmc.l2hostility.content.traits.base.AttributeTrait;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class Traits {
    public static final ShHsTraitBuilder.ShHsTraitEntry<AttributeTrait> AUGMENTED_REALITY;
    public static final ShHsTraitBuilder.ShHsTraitEntry<WizardTrait> WIZARD;

    static {
        AUGMENTED_REALITY = REGISTRATE.trait("augmented_reality", ()-> new AttributeTrait(
                        (() -> 0xC9974C),
                        new AttributeTrait.AttributeEntry("augmented_reality_index", CoPAttrs.REALITY,
                                Config.SERVER.realityTraitScale::get, AttributeModifier.Operation.ADD_VALUE)
                ), new TraitConfig(1000, 1, 7, 50))
                .lang("Augmented Reality").register();

        WIZARD = REGISTRATE.trait("wizard", ()-> new WizardTrait(
                        (() -> 0x5e5d82)
                ), new TraitConfig(50, 1, 10, 50))
                .addWhitelist(entry -> entry.add(
                        EntityType.ENDERMAN, EntityType.SPIDER, EntityType.CAVE_SPIDER,
                        EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
                        EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER))
                .desc("The mob can cast spells.")
                .lang("Wizard").register();
    }

    public static void register() {
    }
}
