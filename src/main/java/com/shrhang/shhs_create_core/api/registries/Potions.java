package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.data.ShHsPotionBuilder.ShHsPotionEntry;
import net.minecraft.world.effect.MobEffects;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class Potions {
    public static final ShHsPotionEntry INTANGIBLE = REGISTRATE
            .potion("intangible")
            .effect(Effects.INTANGIBLE, 20 * 60 * 3)
            .effect(MobEffects.INVISIBILITY, 20 * 60 * 3)
            .lang("Intangibility") // 仅在基础版本注册语言键即可，长效、强化版通过相同的baseName自动引用。
            .register();

    public static final ShHsPotionEntry LONG_INTANGIBLE = REGISTRATE
            .potion("long_intangible")
            .baseName("intangible")
            .effect(Effects.INTANGIBLE, 20 * 60 * 8)
            .effect(MobEffects.INVISIBILITY, 20 * 60 * 8)
            .register();

    public static void register() {
    }
}
