package com.shrhang.shhs_create_core.content.util;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

import static com.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static dev.xkmc.curseofpandora.init.registrate.CoPAttrs.SPELL;

public class SpellToleranceHelper {
    public static double calculateRequiredTolerance(int spellLevel, AbstractSpell spell, CastSource source) {
        return calculateRequiredTolerance(spellLevel, spell.getRarity(spellLevel).getValue(), source);
    }
    public static double calculateRequiredTolerance(int spellLevel, int rarityValue, CastSource source) {
        return calculateRequiredTolerance(spellLevel, rarityValue, SERVER.rarityCoefficient.get(), source);
    }
    public static double calculateRequiredTolerance(int spellLevel, int rarityValue, double coefficient, CastSource source) {
        return Math.max(1, spellLevel + coefficient * rarityValue - (source.consumesMana() ? 0 : 2));
    }

    public static double getSpellTolerance(LivingEntity entity) {
        return Objects.requireNonNull(entity.getAttribute(SPELL)).getValue();
    }
}
