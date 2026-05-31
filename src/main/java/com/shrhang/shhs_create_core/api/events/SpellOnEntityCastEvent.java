package com.shrhang.shhs_create_core.api.events;

import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

public class SpellOnEntityCastEvent extends LivingEvent {
    private final String spellId;
    private final SchoolType schoolType;
    private final CastSource castSource;
    private final int spellLevel;
    private final int newSpellLevel;
    private final int manaCost;
    private final int newManaCost;

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

    public int getSpellLevel() {
        return spellLevel;
    }

    public int getNewSpellLevel() {
        return newSpellLevel;
    }

    public int getManaCost() {
        return manaCost;
    }

    public int getNewManaCost() {
        return newManaCost;
    }
}
