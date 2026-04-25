package com.shrhang.shhs_create_core.content.util;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.LivingEntity;

import static com.shrhang.shhs_create_core.Config.COMMON;
import static dev.xkmc.curseofpandora.init.registrate.CoPAttrs.SPELL;

public class SpellToleranceHelper {

    public static String lang = "text.shhs_create_core.event.no_enough_spell_tolerance";

    public static double calculateRequiredTolerance(int spellLevel, AbstractSpell spell, CastSource source) {
        return calculateRequiredTolerance(spellLevel, spell.getRarity(spellLevel).getValue(), source);
    }
    public static double calculateRequiredTolerance(int spellLevel, int rarityValue, CastSource source) {
        return calculateRequiredTolerance(spellLevel, rarityValue, COMMON.rarityCoefficient.get(), source);
    }
    public static double calculateRequiredTolerance(int spellLevel, int rarityValue, double coefficient, CastSource source) {
        return spellLevel + coefficient * rarityValue - (source.consumesMana() ? 1 : 0) - (source.respectsCooldown() ? 1 : 0);
    }

    public static double getSpellTolerance(LivingEntity entity) {
        return entity.getAttribute(SPELL).getValue();
    }
}
