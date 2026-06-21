package com.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import com.shrhang.shhs_create_core.ShHsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;

import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.entityCastSpell;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

/** 管理生物当前施法的状态机推进，包括冷却计时、法术读条、持续施法触发与法力恢复。 */
public class MobMagicManager {
    public static final int MANA_REGEN_TICKS = 10;
    public static final int CONTINUOUS_CAST_TICK_INTERVAL = 10; // 持续施法的间隔时间，显然它不能是每tick都判断一次

    /** 每 tick 调用，推进当前施法状态。 */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        Level level = entity.level();
        boolean doManaRegen = Objects.requireNonNull(level.getServer()).getTickCount() % MANA_REGEN_TICKS == 0;
        MagicData magicData = MagicData.getPlayerMagicData(entity);
        // 冷却与重施法计时
        magicData.getPlayerCooldowns().tick(1);
        magicData.getPlayerRecasts().tick(2);

        // 判断当前是否在施法
        if (magicData.isCasting()) {
            var spell = SpellRegistry.getSpell(magicData.getCastingSpellId());
            if ((spell.getCastType() == CastType.LONG && !entity.isUsingItem()) || spell.getCastType() == CastType.INSTANT) { // 吟唱或瞬发
                if (magicData.getCastDurationRemaining() <= 0) {
                    entityCastSpell(spell, level, magicData.getCastingSpellLevel(), entity, magicData.getCastSource(), true);
                    if (magicData.getCastSource() == CastSource.SCROLL) {
                        removeMobsScroll(entity);
                    }
                    spell.onServerCastComplete(level, magicData.getCastingSpellLevel(), entity, magicData, false);
                }
            } else if (spell.getCastType() == CastType.CONTINUOUS && magicData.getCastDurationRemaining() % CONTINUOUS_CAST_TICK_INTERVAL == 0) { // 持续施法
                if (magicData.getCastDurationRemaining() <= 0 || (magicData.getCastSource().consumesMana() && magicData.getMana() - spell.getManaCost(magicData.getCastingSpellLevel()) * 2 < 0)) {
                    entityCastSpell(spell, level, magicData.getCastingSpellLevel(), entity, magicData.getCastSource(), true);
                    if (magicData.getCastSource() == CastSource.SCROLL) {
                        removeMobsScroll(entity);
                    }
                    spell.onServerCastComplete(level, magicData.getCastingSpellLevel(), entity, magicData, false);
                } else { // 如果持续施法还在进行中，继续触发施法效果
                    entityCastSpell(spell, level, magicData.getCastingSpellLevel(), entity, magicData.getCastSource(), false);
                }
            }

            magicData.handleCastDuration(); // 维护施法持续时间
            if (magicData.isCasting()) {
                spell.onServerCastTick(level, magicData.getCastingSpellLevel(), entity, magicData);
            }
        }

        // 判断是否需要恢复法力值
        if (doManaRegen) {
            regenMana(entity, magicData);
        }
    }

    /** 恢复法力值，每 10 tick 调用一次。 */
    public static boolean regenMana(LivingEntity entity, MagicData magicData) {
        int maxMana = (int) entity.getAttributeValue(MAX_MANA);
        var mana = magicData.getMana();
        if (mana != maxMana) {
            var increment = maxMana * entity.getAttributeValue(MANA_REGEN) * .01f * ShHsConfig.SERVER.mobManaRegenMultiplier.get().floatValue();
            magicData.setMana((float) Mth.clamp(magicData.getMana() + increment, 0, maxMana));
            return true;
        }
        return false;
    }

    /**
     * 消耗生物正在使用的卷轴。castingItemStack 会在
     * <p>{@link com.shrhang.shhs_create_core.content.util.SpellCastHelper#attemptInitiateEntityCast(ItemStack, LivingEntity, AbstractSpell, int, CastSource, boolean, String)} </p>
     * 中被设置为施法物品。
     */
    public static void removeMobsScroll(LivingEntity entity) {
        ItemStack potentialScroll = MagicData.getPlayerMagicData(entity).getPlayerCastingItem();
        if (potentialScroll.getItem() instanceof Scroll) {
            potentialScroll.shrink(1);
        }
    }

    /** 给生物添加法术冷却。 */
    public static void addCooldown(LivingEntity entity, AbstractSpell spell, CastSource castSource) {
        int effectiveCooldown = getEffectiveSpellCooldown(spell, entity, castSource);
        MagicData.getPlayerMagicData(entity).getPlayerCooldowns().addCooldown(spell, effectiveCooldown);
    }

    /** 计算法术的实际冷却时间，受属性与施法来源影响。 */
    public static int getEffectiveSpellCooldown(AbstractSpell spell, LivingEntity entity, CastSource castSource) {
        double cooldownModifier = entity.getAttributeValue(COOLDOWN_REDUCTION);
        float itemCoolDownModifer = 1;
        if (castSource == CastSource.SWORD) {
            itemCoolDownModifer = ServerConfigs.SWORDS_CD_MULTIPLIER.get().floatValue();
        }
        return (int) (spell.getSpellCooldown() * (2 - Utils.softCapFormula(cooldownModifier)) * itemCoolDownModifer);
    }
}