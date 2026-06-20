package com.shrhang.shhs_create_core.content.util;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

import static com.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static dev.xkmc.curseofpandora.init.registrate.CoPAttrs.SPELL;

public class SpellToleranceHelper {

    /**
     * 计算施法需要的魔力耐性
     */
    public static double getRequiredTolerance(int spellLevel, AbstractSpell spell, CastSource source) {
        SpellRarity rarity = spell.getRarity(spellLevel);
        return Math.max(getRelativeLevel(spellLevel, spell) + SERVER.rarityCoefficient.get() * rarity.getValue() - (source.consumesMana() ? SERVER.consumesManaCoefficient.get() : 0), 1);
    }

    /**
     * 获取法术在该等级在所处稀有度中是第几个等级。
     */
    public static int getRelativeLevel(int spellLevel, AbstractSpell spell) {
        int clampedLevel = Math.max(1, spellLevel);
        SpellRarity rarity = spell.getRarity(clampedLevel);
        return clampedLevel - spell.getMinLevelForRarity(rarity) + 1;
    }

    /**
     * 获取实体的魔力耐性
     */
    public static double getSpellTolerance(LivingEntity entity) {
        return Objects.requireNonNull(entity.getAttribute(SPELL)).getValue();
    }
}
