package io.github.shrhang.shhs_create_core.content.data;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.api.registrate.ShHsSpellTag;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;
import static io.redspace.ironsspellbooks.api.registry.SpellRegistry.*;

public class ShHsTagKey {
    public static final TagKey<Block> FAN_PROCESSING_CATALYSTS_MIRACLE = TagKey.create(
            Registries.BLOCK, ShHsCreateCore.rl("fan_processing_catalysts/miracle"));

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

    public static final ShHsSpellTag ENTITY_SPELL_BLACKLIST;
    public static final ShHsSpellTag MOB_SPELL_CONTROL; // 控制
    public static final ShHsSpellTag MOB_SPELL_DAMAGE; // 攻击
    public static final ShHsSpellTag MOB_SPELL_APPROACH; // 突进
    public static final ShHsSpellTag MOB_SPELL_ESCAPE; // 脱身
    public static final ShHsSpellTag MOB_SPELL_HEAL; // 治疗
    public static final ShHsSpellTag MOB_SPELL_DEFENSE; // 防御
    public static final ShHsSpellTag MOB_SPELL_SUMMON; // 召唤
    public static final ShHsSpellTag MOB_SPELL_CLOSE_RANGE; // 近战

    static {
        ENTITY_SPELL_BLACKLIST =
                ShHsSpellTag.create(ShHsCreateCore.rl("entity_spell_blacklist")).addSpell(
                        GLUTTONY_SPELL,
                        PLANAR_SIGHT_SPELL,
                        POCKET_DIMENSION_SPELL,
                        PORTAL_SPELL,
                        RECALL_SPELL,
                        SACRIFICE_SPELL,
                        SHIELD_SPELL,
                        SPECTRAL_HAMMER_SPELL,
                        SUMMON_ENDER_CHEST_SPELL,
                        TELEKINESIS_SPELL,
                        THROW_SPELL,
                        TOUCH_DIG,
                        WOLOLO_SPELL
                );
        MOB_SPELL_CONTROL =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/control")).addSpell(
                        ROOT_SPELL,
                        FROSTWAVE_SPELL
                );
        MOB_SPELL_DAMAGE =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/damage")).addSpell(
                        FIREBALL_SPELL,
                        MAGIC_MISSILE_SPELL,
                        RAY_OF_FROST_SPELL,
                        WALL_OF_FIRE_SPELL,
                        ELDRITCH_BLAST_SPELL,
                        FLAMING_BARRAGE_SPELL,
                        RAISE_HELL_SPELL
                );
        MOB_SPELL_APPROACH =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/approach")).addSpell(
                        TELEPORT_SPELL,
                        BLOOD_STEP_SPELL,
                        BURNING_DASH_SPELL
                );
        MOB_SPELL_ESCAPE =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/escape")).addSpell(
                        TELEPORT_SPELL,
                        FROST_STEP_SPELL
                );
        MOB_SPELL_HEAL =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/heal")).addSpell(
                        HEAL_SPELL,
                        HEALING_CIRCLE_SPELL
                );
        MOB_SPELL_DEFENSE =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/defense")).addSpell(
                        OAKSKIN_SPELL,
                        FORTIFY_SPELL
                );
        MOB_SPELL_SUMMON =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/summon")).addSpell(
                        RAISE_DEAD_SPELL,
                        SUMMON_SWORDS,
                        SUMMON_VEX_SPELL,
                        SUMMON_HORSE_SPELL,
                        SUMMON_POLAR_BEAR_SPELL
                );
        MOB_SPELL_CLOSE_RANGE =
                ShHsSpellTag.create(ShHsCreateCore.rl("mob_spells/close_range")).addSpell(
                        FROSTWAVE_SPELL,
                        CONE_OF_COLD_SPELL,
                        FANG_WARD_SPELL,
                        STOMP_SPELL
                );
    }

    public static void init() {
        REGISTRATE.addDataGenerator(ProviderType.ENTITY_TAGS, ShHsTagKey::onEntityTagGen);
        REGISTRATE.addDataGenerator(SPELL_TAGS, ShHsTagKey::onSpellTagGen);
    }
}
