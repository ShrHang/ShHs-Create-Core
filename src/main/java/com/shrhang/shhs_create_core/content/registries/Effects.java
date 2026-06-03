package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.data.ShHsMobEffectBuilder.ShHsMobEffectEntry;
import com.shrhang.shhs_create_core.content.effect.IntangibleMobEffect;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class Effects {
    public static final ShHsMobEffectEntry<IntangibleMobEffect> INTANGIBLE = REGISTRATE
            .effect("intangible", IntangibleMobEffect::new)
            .lang("Intangible")
            .register();

    public static void register() {
    }
}
