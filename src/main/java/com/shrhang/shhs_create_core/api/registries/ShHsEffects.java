package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.data.ShHsMobEffectBuilder.ShHsMobEffectEntry;
import com.shrhang.shhs_create_core.content.effect.intangible.IntangibleMobEffect;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsEffects {
    public static final ShHsMobEffectEntry<IntangibleMobEffect> INTANGIBLE = REGISTRATE
            .effect("intangible", IntangibleMobEffect::new)
            .lang("Intangible")
            .desc("Turns the target ghostlike, allowing slow flight and wall phasing while limiting most incoming damage to 1.")
            .register();

    public static void register() {
    }
}
