package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper.SpellSource;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.shrhang.shhs_create_core.content.data.ShHsTagKey.*;

public class MobSpellTactics {
    enum Tactic { NONE, CONTROL, APPROACH, SUMMON, EMERGENCY, ATTACK }
    private record Candidate(SpellSource source, int level, int cost) {
        AbstractSpell spell() { return source.spellData().getSpell(); }
    }
    private record Decision(Tactic tactic, Candidate candidate) {}
    public static final int DECISION_INTERVAL = 10;
    private static final int RECOVERY_TICKS = 20;
    private static final int STAGE_TIMEOUT = 40;

    static boolean hasOffensiveRecast(Mob mob) {
        return MagicData.getPlayerMagicData(mob).getPlayerRecasts().getActiveRecasts().stream()
                .anyMatch(r -> !(r.getCastData() instanceof SummonedEntitiesCastData));
    }

    private static List<Candidate> candidates(Mob mob) {
        var data = MagicData.getPlayerMagicData(mob);
        Map<String, Candidate> bySpell = new LinkedHashMap<>();
        for (SpellSource source : MobMagicManager.getAvailableSources(mob)) {
            var spell = source.spellData().getSpell();
            int level = spell.getLevelFor(source.spellData().getLevel(), mob);
            int cost = source.castSource().consumesMana() ? Math.max(0, spell.getManaCost(level)) : 0;
            if (level < 1 || !spell.isEnabled() || ENTITY_SPELL_BLACKLIST.contains(spell)
                    || !MobSpellSupport.supports(spell, level, mob) || cost > data.getMana()
                    || data.getPlayerRecasts().hasRecastForSpell(spell)
                    || source.castSource() == CastSource.SCROLL && spell.getRecastCount(level, mob) > 0
                    || source.castSource().respectsCooldown() && data.getPlayerCooldowns().isOnCooldown(spell))
                continue;
            var candidate = new Candidate(source, level, cost);
            var old = bySpell.get(spell.getSpellId());
            boolean scroll = source.castSource() == CastSource.SCROLL;
            if (old == null || old.source.castSource() == CastSource.SCROLL && !scroll
                    || scroll == (old.source.castSource() == CastSource.SCROLL) && level > old.level)
                bySpell.put(spell.getSpellId(), candidate);
        }
        return new ArrayList<>(bySpell.values());
    }

    private static boolean damage(AbstractSpell spell) {
        return MOB_SPELL_DAMAGE.contains(spell) || !(MOB_SPELL_CONTROL.contains(spell)
                || MOB_SPELL_APPROACH.contains(spell) || MOB_SPELL_ESCAPE.contains(spell)
                || MOB_SPELL_HEAL.contains(spell) || MOB_SPELL_DEFENSE.contains(spell)
                || MOB_SPELL_SUMMON.contains(spell));
    }

    private static boolean matches(Tactic tactic, int stage, AbstractSpell spell) {
        return switch (tactic) {
            case CONTROL -> stage == 0 ? MOB_SPELL_CONTROL.contains(spell) : damage(spell);
            case APPROACH -> stage == 0 ? MOB_SPELL_APPROACH.contains(spell)
                    : damage(spell) && MOB_SPELL_CLOSE_RANGE.contains(spell);
            case SUMMON -> stage == 0 ? MOB_SPELL_SUMMON.contains(spell) : damage(spell);
            case ATTACK -> damage(spell);
            case EMERGENCY -> MOB_SPELL_HEAL.contains(spell) || MOB_SPELL_DEFENSE.contains(spell)
                    || MOB_SPELL_ESCAPE.contains(spell);
            default -> false;
        };
    }

    private static boolean usable(Mob mob, Candidate candidate) {
        return MobMagicManager.check(mob, candidate.spell(), candidate.level, candidate.source.castSource(), false)
                == MobMagicManager.CastAttemptResult.STARTED
                && MobSpellSupport.useful(mob, candidate.spell());
    }

    private static @Nullable Decision decide(Mob mob) {
        var state = MobMagicManager.state(mob);
        long now = mob.level().getGameTime();
        if (!MobMagicManager.isEnabled(mob) || state.autoCastFailed || state.active != null
                || now < state.nextAction || hasOffensiveRecast(mob) || mob.isNoAi()) return null;
        if (state.tactic == Tactic.NONE && now < state.nextDecision) return null;
        if (state.tactic != Tactic.NONE && (now > state.stageDeadline
                || !Objects.equals(state.comboTarget, mob.getTarget() == null ? null : mob.getTarget().getUUID())))
            state.clearCombo();
        var all = candidates(mob);
        List<Candidate> usable = all.stream().filter(c -> usable(mob, c)).toList();
        if (usable.isEmpty()) return null;
        if (mob.getHealth() < mob.getMaxHealth() * .35f) {
            for (var tag : List.of(MOB_SPELL_HEAL, MOB_SPELL_DEFENSE, MOB_SPELL_ESCAPE)) {
                var emergency = usable.stream().filter(c -> tag.contains(c.spell())).findFirst();
                if (emergency.isPresent()) return new Decision(Tactic.EMERGENCY, emergency.get());
            }
        }
        if (state.tactic != Tactic.NONE) {
            return usable.stream().filter(c -> matches(state.tactic, state.stage, c.spell())
                    && !state.usedSpells.contains(c.spell().getSpellId()))
                    .findFirst().map(c -> new Decision(state.tactic, c)).orElse(null);
        }
        List<Tactic> tactics = new ArrayList<>();
        if (mob.getTarget() != null && mob.distanceToSqr(mob.getTarget()) > 36) tactics.add(Tactic.APPROACH);
        tactics.add(Tactic.SUMMON);
        tactics.add(Tactic.CONTROL);
        float mana = MagicData.getPlayerMagicData(mob).getMana();
        for (Tactic tactic : tactics) {
            for (Candidate first : usable) {
                if (!matches(tactic, 0, first.spell())) continue;
                if (all.stream().anyMatch(next -> matches(tactic, 1, next.spell())
                        && !next.spell().equals(first.spell()) && first.cost + next.cost <= mana))
                    return new Decision(tactic, first);
            }
        }
        List<Candidate> attacks = new ArrayList<>(usable.stream().filter(c -> damage(c.spell())).toList());
        if (attacks.size() > 1) attacks.removeIf(c -> c.spell().getSpellId().equals(state.lastSpell));
        return attacks.isEmpty() ? null : new Decision(Tactic.ATTACK,
                attacks.get(mob.getRandom().nextInt(attacks.size())));
    }

    static boolean canStart(Mob mob) {
        return decide(mob) != null;
    }

    static boolean attempt(Mob mob) {
        var state = MobMagicManager.state(mob);
        Decision decision = decide(mob);
        if (decision == null) return false;
        if (state.tactic == Tactic.NONE || decision.tactic == Tactic.EMERGENCY) {
            state.clearCombo();
            state.tactic = decision.tactic;
            state.comboTarget = mob.getTarget() == null ? null : mob.getTarget().getUUID();
            state.stageDeadline = mob.level().getGameTime() + STAGE_TIMEOUT;
        }
        var result = MobMagicManager.tryCast(mob, decision.candidate.source);
        if (result == MobMagicManager.CastAttemptResult.STARTED) return true;
        for (var alternative : candidates(mob)) {
            if (alternative.spell().equals(decision.candidate.spell())
                    || state.usedSpells.contains(alternative.spell().getSpellId())
                    || !matches(state.tactic, state.stage, alternative.spell()) || !usable(mob, alternative))
                continue;
            if (MobMagicManager.tryCast(mob, alternative.source) == MobMagicManager.CastAttemptResult.STARTED)
                return true;
            break;
        }
        state.clearCombo();
        state.nextDecision = mob.level().getGameTime() + DECISION_INTERVAL;
        return false;
    }

    static void onCompleted(Mob mob, MobCastState.ActiveCast cast) {
        var state = MobMagicManager.state(mob);
        state.lastSpell = cast.spell.getSpellId();
        if (cast.recast || state.tactic == Tactic.NONE) return;
        state.usedSpells.add(cast.spell.getSpellId());
        state.stage++;
        int steps = state.tactic == Tactic.CONTROL ? 3
                : state.tactic == Tactic.APPROACH || state.tactic == Tactic.SUMMON ? 2 : 1;
        if (state.stage >= steps) {
            state.clearCombo();
            state.nextAction = mob.level().getGameTime() + RECOVERY_TICKS;
        } else {
            state.stageDeadline = mob.level().getGameTime() + STAGE_TIMEOUT;
        }
        state.nextDecision = state.nextAction;
    }
}
