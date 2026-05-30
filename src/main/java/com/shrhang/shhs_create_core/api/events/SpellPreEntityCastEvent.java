package com.shrhang.shhs_create_core.api.events;

import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

public class SpellPreEntityCastEvent extends LivingEvent implements ICancellableEvent {
    private final String spellId;
    private final SchoolType schoolType;
    private final CastSource castSource;
    private final int spellLevel;

    public SpellPreEntityCastEvent(LivingEntity entity, String spellId, int spellLevel, SchoolType schoolType, CastSource castSource) {
        super(entity);
        this.spellId = spellId;
        this.schoolType = schoolType;
        this.castSource = castSource;
        this.spellLevel = spellLevel;
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
}
