package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Install explicitly, with a priority above ordinary weapon attack goals. */
public class MobSpellCastGoal extends Goal {
    private final Mob mob;

    public MobSpellCastGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide || !MobMagicManager.isEnabled(mob)) return false;
        var state = MobMagicManager.state(mob);
        if (!state.bound) return false;
        if (MobMagicManager.isCasting(mob) || MobSpellTactics.hasOffensiveRecast(mob)) return true;
        if (mob.level().getGameTime() < state.nextDecision) return false;
        boolean result = MobSpellTactics.canStart(mob);
        state.nextDecision = mob.level().getGameTime() + MobSpellTactics.DECISION_INTERVAL;
        if (result) state.nextDecision = mob.level().getGameTime();
        return result;
    }

    @Override
    public boolean canContinueToUse() {
        if (!MobMagicManager.isEnabled(mob) || MobMagicManager.state(mob).autoCastFailed || mob.isNoAi()) return false;
        var state = MobMagicManager.state(mob);
        return MobMagicManager.isCasting(mob) || MobSpellTactics.hasOffensiveRecast(mob)
                || state.tactic != MobSpellTactics.Tactic.NONE
                && mob.level().getGameTime() <= state.stageDeadline
                && mob.level().getGameTime() >= state.nextAction && MobSpellTactics.canStart(mob);
    }

    @Override
    public void start() {
        if (!MobMagicManager.isCasting(mob)) MobSpellTactics.attempt(mob);
    }

    @Override
    public void tick() {
        var state = MobMagicManager.state(mob);
        if (state.active != null || MobSpellTactics.hasOffensiveRecast(mob)) {
            mob.getNavigation().stop();
            if (state.active != null) MobSpellSupport.aim(mob, state.active.spell);
        } else if (mob.level().getGameTime() >= state.nextAction) {
            MobSpellTactics.attempt(mob);
        }
    }

    @Override
    public void stop() {
        if (MobMagicManager.isCasting(mob) || MobSpellTactics.hasOffensiveRecast(mob))
            MobMagicManager.cancelCast(mob);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
