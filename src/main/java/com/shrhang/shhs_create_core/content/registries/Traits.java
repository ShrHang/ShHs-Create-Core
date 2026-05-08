package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.Config;
import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.data.ShHsRegistrate;
import com.shrhang.shhs_create_core.content.data.ShHsTraitEntry;
import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import dev.xkmc.l2hostility.content.config.TraitConfig;
import dev.xkmc.l2hostility.content.traits.base.AttributeTrait;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class Traits {
    private static final ShHsRegistrate REGISTRATE = ShHsCreateCore.createRegistrate;

    public static final ShHsTraitEntry<AttributeTrait> AUGMENTED_REALITY = REGISTRATE.trait("augmented_reality", ()-> new AttributeTrait(
            (() -> 0xC9974C),
            new AttributeTrait.AttributeEntry("augmented_reality_index", CoPAttrs.REALITY,
                    Config.SERVER.realityTraitScale::get, AttributeModifier.Operation.ADD_VALUE)
            ), new TraitConfig(1000, 1, 7, 50))
            .lang("Augmented Reality").register();

    public static void register() {

    }
}
