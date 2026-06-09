package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.data.ShHsPotionBuilder.ShHsPotionEntry;
import com.shrhang.shhs_create_core.content.data.ShHsPotionBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsPotions {
    public static final ShHsPotionEntry INTANGIBLE = REGISTRATE
            .potion("intangible")
            .effect(ShHsEffects.INTANGIBLE, 20 * 60 * 16, 8)
            .recipe(Potions.AWKWARD, ShHsItems.EMPTY_TRAIT)
            .lang("Intangibility")
            .register();

    public static final ShHsPotionEntry LONG_INTANGIBLE = REGISTRATE
            .potion("long_intangible")
            .baseName("intangible")
            .effect(ShHsEffects.INTANGIBLE, 20 * 60 * 28, 8)
            .recipe(INTANGIBLE, Items.REDSTONE)
            .register();

    public static final ShHsPotionEntry STRONG_INTANGIBLE = REGISTRATE
            .potion("strong_intangible")
            .baseName("intangible")
            .effect(ShHsEffects.INTANGIBLE, 20 * 60 * 8, 16)
            .recipe(INTANGIBLE, Items.GLOWSTONE_DUST)
            .register();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterBrewingRecipesEvent event) -> ShHsPotionBuilder.registerBrewingRecipes(event));
    }
}
