package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;
import static io.redspace.ironsspellbooks.api.registry.SpellRegistry.*;

public class ShHsTagKey {
    public static final Map<ResourceLocation, Consumer<RegistrateTagsProvider.IntrinsicImpl<EntityType<?>>>> ENTITY_TAG_BUILDER = new TreeMap<>();
    public static void onEntityTagGen(RegistrateTagsProvider.IntrinsicImpl<EntityType<?>> provider) {
        ENTITY_TAG_BUILDER.values().forEach(e -> e.accept(provider));
    }

    public static final ProviderType<RegistrateTagsProvider.Impl<AbstractSpell>> SPELL_TAGS =
            ProviderType.registerDynamicTag("shh", "spell_tags", SPELL_REGISTRY_KEY);
    public static final Map<ResourceLocation, Consumer<RegistrateTagsProvider<AbstractSpell>>> SPELL_TAG_BUILDER = new TreeMap<>();
    public static void onSpellTagGen(RegistrateTagsProvider<AbstractSpell> provider) {
        SPELL_TAG_BUILDER.values().forEach(e -> e.accept(provider));
    }

    public static final ShHsSpellTag ENTITY_SPELL_BLACKLIST =
            ShHsSpellTag.create(ShHsCreateCore.rl("entity_spell_blacklist")).addSpell(
                    GLUTTONY_SPELL,
                    PLANAR_SIGHT_SPELL,
                    POCKET_DIMENSION_SPELL,
                    SHIELD_SPELL,
                    SPECTRAL_HAMMER_SPELL,
                    SUMMON_ENDER_CHEST_SPELL,
                    TELEKINESIS_SPELL,
                    THROW_SPELL,
                    WOLOLO_SPELL
            );

    public static void init() {
        REGISTRATE.addDataGenerator(ProviderType.ENTITY_TAGS, ShHsTagKey::onEntityTagGen);
        REGISTRATE.addDataGenerator(SPELL_TAGS, ShHsTagKey::onSpellTagGen);
    }
}
