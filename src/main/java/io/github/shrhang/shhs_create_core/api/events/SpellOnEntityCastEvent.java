package io.github.shrhang.shhs_create_core.api.events;

import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

public class SpellOnEntityCastEvent extends LivingEvent {
    private final String spellId;
    private final SchoolType schoolType;
    private final CastSource castSource;
    private final int spellLevel;
    private int newSpellLevel;
    private final int manaCost;
    private int newManaCost;

    public SpellOnEntityCastEvent(LivingEntity entity, String spellId, int spellLevel, int manaCost, SchoolType schoolType, CastSource castSource) {
        super(entity);
        this.spellId = spellId;
        this.schoolType = schoolType;
        this.castSource = castSource;
        this.spellLevel = spellLevel;
        this.newSpellLevel = spellLevel;
        this.manaCost = manaCost;
        this.newManaCost = manaCost;
    }

    public String getSpellId() {
        return spellId;
    }

    public SchoolType getSchoolType() {
        return schoolType;
    }

    public CastSource getCastSource() {
        return castSource;
    }

    public int getOriginalSpellLevel() {
        return spellLevel;
    }

    public int getSpellLevel() {
        return newSpellLevel;
    }

    public void setSpellLevel(int spellLevel) {
        this.newSpellLevel = spellLevel;
    }

    public int getOriginalManaCost() {
        return manaCost;
    }

    public int getManaCost() {
        return newManaCost;
    }

    public void setManaCost(int mana) {
        this.newManaCost = mana;
    }
}
