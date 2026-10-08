package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import com.mojang.logging.LogUtils;
import io.github.shrhang.shhs_create_core.ShHsConfig;
import io.github.shrhang.shhs_create_core.api.events.SpellOnEntityCastEvent;
import io.github.shrhang.shhs_create_core.api.events.SpellPreEntityCastEvent;
import io.github.shrhang.shhs_create_core.content.registries.ShHsAttachmentTypes;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper.SpellSource;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.*;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.registries.DataAttachmentRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

public class MobMagicManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int ACTION_INTERVAL = 5;
    private static int tagGeneration;

    public enum CastAttemptResult {
        STARTED, NOT_ENABLED, BUSY, INVALID_SOURCE, INVALID_CONDITIONS, INSUFFICIENT_MANA,
        ON_COOLDOWN, EVENT_CANCELLED
    }

    /** Bridge implemented only on PlayerRecasts; not a second recast data holder. */
    public interface RecastOwner {
        void shhs$bind(Mob mob);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(MobMagicManager::onTick);
        NeoForge.EVENT_BUS.addListener(MobMagicManager::onDamage);
        NeoForge.EVENT_BUS.addListener(MobMagicManager::onLeave);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobMagicManager::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobMagicManager::onCounterspell);
        NeoForge.EVENT_BUS.addListener(MobMagicManager::onTags);
    }

    static MobCastState state(Mob mob) {
        return mob.getData(ShHsAttachmentTypes.MOB_CAST_STATE);
    }

    public static boolean isEnabled(Mob mob) {
        return mob.hasData(ShHsAttachmentTypes.MOB_CAST_STATE) && state(mob).enabled;
    }

    public static boolean enable(Mob mob) {
        if (mob.level().isClientSide || mob instanceof IMagicEntity) return false;
        var state = state(mob);
        boolean existing = mob.hasData(DataAttachmentRegistry.MAGIC_DATA);
        state.enabled = true;
        state.autoCastFailed = false;
        bind(mob, !existing && !state.initialized);
        return true;
    }

    public static void disable(Mob mob) {
        if (mob.level().isClientSide || !isEnabled(mob)) return;
        bind(mob, false);
        cancelCast(mob);
        MagicData.getPlayerMagicData(mob).getPlayerRecasts().removeAll(RecastResult.USER_CANCEL);
        var state = state(mob);
        state.enabled = false;
        state.clearCombo();
    }

    private static void bind(Mob mob, boolean fillMana) {
        var state = state(mob);
        if (state.bound) return;
        MagicData old = MagicData.getPlayerMagicData(mob);
        float mana = old.getMana();
        CompoundTag saved = new CompoundTag();
        old.saveNBTData(saved, mob.registryAccess());
        MagicData data = new MagicData(false);
        data.loadNBTData(saved, mob.registryAccess());
        data.setSyncedData(new SyncedSpellData(mob));
        data.setMana(fillMana ? (float) mob.getAttributeValue(MAX_MANA) : mana);
        mob.setData(DataAttachmentRegistry.MAGIC_DATA, data);
        ((RecastOwner) data.getPlayerRecasts()).shhs$bind(mob);
        state.bound = true;
        state.initialized = true;
        state.tagGeneration = tagGeneration;
        state.nextDecision = mob.level().getGameTime() + Math.floorMod(mob.getId(), MobSpellTactics.DECISION_INTERVAL);
        if (state.pendingCooldown != null) {
            var spell = SpellRegistry.getSpell(state.pendingCooldown);
            if (!data.getPlayerRecasts().hasRecastForSpell(spell))
                addCooldown(mob, spell, state.pendingSource);
            state.pendingCooldown = null;
        }
        for (var recast : data.getPlayerRecasts().getAllRecasts()) {
            if (!(recast.getCastData() instanceof SummonedEntitiesCastData))
                data.getPlayerRecasts().removeRecast(recast, RecastResult.USER_CANCEL);
        }
    }

    public static List<SpellSource> getAvailableSources(Mob mob) {
        return SpellCastHelper.getEntitySpells(mob);
    }

    public static boolean isCasting(Mob mob) {
        return isEnabled(mob) && state(mob).active != null;
    }

    public static CastAttemptResult tryCast(Mob mob, SpellSource source) {
        if (mob.level().isClientSide || !isEnabled(mob)) return CastAttemptResult.NOT_ENABLED;
        bind(mob, false);
        var state = state(mob);
        if (state.autoCastFailed || state.active != null || MobSpellTactics.hasOffensiveRecast(mob)
                || MagicData.getPlayerMagicData(mob).isCasting()
                || mob.level().getGameTime() < state.nextAction) return CastAttemptResult.BUSY;
        ItemStack stack = source.stack(mob);
        if (!source.isValid(mob, stack)) return CastAttemptResult.INVALID_SOURCE;
        AbstractSpell spell = source.spellData().getSpell();
        int level = spell.getLevelFor(source.spellData().getLevel(), mob);
        if (MagicData.getPlayerMagicData(mob).getPlayerRecasts().hasRecastForSpell(spell))
            return CastAttemptResult.BUSY;
        return start(mob, spell, level, source.castSource(), source, false);
    }

    static CastAttemptResult check(Mob mob, AbstractSpell spell, int level, CastSource source, boolean recast) {
        if (!mob.isAlive() || mob.isNoAi() || level < 1 || !SpellCastHelper.isSpellAllowed(mob, spell)
                || spell.getCastType() == CastType.NONE || !MobSpellSupport.supports(spell, level, mob))
            return CastAttemptResult.INVALID_CONDITIONS;
        if (source == CastSource.SCROLL && spell.getRecastCount(level, mob) > 0)
            return CastAttemptResult.INVALID_CONDITIONS;
        var data = MagicData.getPlayerMagicData(mob);
        if (!recast && source.respectsCooldown() && data.getPlayerCooldowns().isOnCooldown(spell))
            return CastAttemptResult.ON_COOLDOWN;
        if (!recast && source.consumesMana() && data.getMana() < Math.max(0, spell.getManaCost(level)))
            return CastAttemptResult.INSUFFICIENT_MANA;
        if (!MobSpellSupport.isSelf(spell)) {
            var target = mob.getTarget();
            double range = io.github.shrhang.shhs_create_core.content.data.ShHsTagKey.MOB_SPELL_CLOSE_RANGE.contains(spell)
                    ? 6 : 24;
            if (target == null || !target.isAlive() || target.level() != mob.level()
                    || mob.isAlliedTo(target) || mob.distanceToSqr(target) > range * range
                    || !mob.hasLineOfSight(target) || spell.shouldAIStopCasting(level, mob, target))
                return CastAttemptResult.INVALID_CONDITIONS;
        }
        return CastAttemptResult.STARTED;
    }

    private static CastAttemptResult start(Mob mob, AbstractSpell spell, int level,
                                          CastSource source, SpellSource item, boolean recast) {
        var result = check(mob, spell, level, source, recast);
        if (result != CastAttemptResult.STARTED) return result;
        var data = MagicData.getPlayerMagicData(mob);
        var state = state(mob);
        boolean self = MobSpellSupport.isSelf(spell);
        var target = self ? null : mob.getTarget();
        var cast = new MobCastState.ActiveCast(spell, level, source, item,
                item == null ? ItemStack.EMPTY : item.stack(mob).copy(),
                target == null ? null : target.getUUID(), self, recast);
        try {
            MobSpellSupport.aim(mob, spell);
            if (!spell.checkPreCastConditions(mob.level(), level, mob, data)
                    || !MobSpellSupport.prepare(mob, spell)) {
                data.resetAdditionalCastData();
                return CastAttemptResult.INVALID_CONDITIONS;
            }
            if (NeoForge.EVENT_BUS.post(new SpellPreEntityCastEvent(mob, spell.getSpellId(), level,
                    spell.getSchoolType(), source)).isCanceled()) {
                data.resetAdditionalCastData();
                return CastAttemptResult.EVENT_CANCELLED;
            }
            if (mob.isUsingItem()) mob.stopUsingItem();
            state.active = cast;
            data.initiateCast(spell, level, Math.max(0, spell.getEffectiveCastTime(level, mob)), source,
                    item == null ? "recast" : item.slotName());
            data.setPlayerCastingItem(item == null ? ItemStack.EMPTY : item.stack(mob));
            spell.onServerPreCast(mob.level(), level, mob, data);
            if (state.active == cast && (spell.getCastType() == CastType.INSTANT
                    || data.getCastDurationRemaining() == 0)) {
                boolean success = emit(mob, cast);
                finish(mob, !success);
            }
            return CastAttemptResult.STARTED;
        } catch (RuntimeException exception) {
            fail(mob, spell.getSpellId(), exception);
            return CastAttemptResult.INVALID_CONDITIONS;
        }
    }

    private static boolean validActive(Mob mob, MobCastState.ActiveCast cast) {
        if (!SpellCastHelper.isSpellAllowed(mob, cast.spell) || !mob.isAlive() || mob.isNoAi()) return false;
        if (cast.recast && !MagicData.getPlayerMagicData(mob).getPlayerRecasts().hasRecastForSpell(cast.spell))
            return false;
        if (!cast.recast && !(cast.source == CastSource.SCROLL && cast.emitted)
                && !cast.item.isValid(mob, cast.snapshot)) return false;
        if (!cast.self) {
            var target = mob.getTarget();
            if (target == null || !target.isAlive() || target.level() != mob.level()
                    || !target.getUUID().equals(cast.target) || mob.isAlliedTo(target)
                    || !mob.hasLineOfSight(target) || cast.spell.shouldAIStopCasting(cast.level, mob, target))
                return false;
            double range = io.github.shrhang.shhs_create_core.content.data.ShHsTagKey.MOB_SPELL_CLOSE_RANGE.contains(cast.spell)
                    ? 6 : 24;
            return !(mob.distanceToSqr(target) > range * range);
        }
        return true;
    }

    private static boolean emit(Mob mob, MobCastState.ActiveCast cast) {
        if (!validActive(mob, cast)) return false;
        var state = state(mob);
        var data = MagicData.getPlayerMagicData(mob);
        var event = new SpellOnEntityCastEvent(mob, cast.spell.getSpellId(), cast.level,
                cast.spell.getManaCost(cast.level), cast.spell.getSchoolType(), cast.source);
        NeoForge.EVENT_BUS.post(event);
        int cost = cast.source.consumesMana() && !cast.recast ? Math.max(0, event.getManaCost()) : 0;
        if (event.getSpellLevel() < 1 || data.getMana() < cost || state.active != cast
                || !validActive(mob, cast)) return false;
        if (!cast.recast && cast.source == CastSource.SCROLL && !cast.emitted) cast.item.stack(mob).shrink(1);
        data.setMana(data.getMana() - cost);
        // Commit before onCast: exceptions can occur after a partial world effect.
        cast.emitted = true;
        state.pendingCooldown = cast.spell.getSpellId();
        state.pendingSource = cast.source;
        MobSpellSupport.aim(mob, cast.spell);
        cast.spell.onCast(mob.level(), event.getSpellLevel(), mob, cast.source, data);
        var recasts = data.getPlayerRecasts();
        if (cast.recast && recasts.hasRecastForSpell(cast.spell))
            recasts.decrementRecastCount(cast.spell);
        var instance = recasts.getRecastInstance(cast.spell.getSpellId());
        if (instance != null) {
            state.pendingCooldown = null;
            state.recastTarget = cast.target;
            MobSpellSupport.trackSummons(mob, instance);
        }
        return true;
    }

    private static void finish(Mob mob, boolean cancelled) {
        var state = state(mob);
        var cast = state.active;
        if (cast == null) return;
        var data = MagicData.getPlayerMagicData(mob);
        state.active = null;
        try {
            if (cast.emitted && !cast.cooldownApplied && !data.getPlayerRecasts().hasRecastForSpell(cast.spell))
                addCooldown(mob, cast.spell, cast.source);
            cast.spell.onServerCastComplete(mob.level(), cast.level, mob, data, cancelled);
        } finally {
            data.resetCastingState();
            data.setPlayerCastingItem(ItemStack.EMPTY);
            state.pendingCooldown = null;
            state.nextAction = mob.level().getGameTime() + ACTION_INTERVAL;
            if (cancelled || !cast.emitted) state.clearCombo();
            else MobSpellTactics.onCompleted(mob, cast);
        }
    }

    public static void cancelCast(Mob mob) {
        if (mob.level().isClientSide || !isEnabled(mob)) return;
        try {
            finish(mob, true);
            var recasts = MagicData.getPlayerMagicData(mob).getPlayerRecasts();
            for (var recast : recasts.getAllRecasts()) {
                if (!(recast.getCastData() instanceof SummonedEntitiesCastData))
                    recasts.removeRecast(recast, RecastResult.USER_CANCEL);
            }
        }
        catch (RuntimeException exception) { fail(mob, "cancel", exception); }
        state(mob).clearCombo();
    }

    public static void addCooldown(Mob mob, AbstractSpell spell, CastSource source) {
        if (source == CastSource.SCROLL || spell.equals(SpellRegistry.none())) return;
        double reduction = 2 - Utils.softCapFormula(mob.getAttributeValue(COOLDOWN_REDUCTION));
        float multiplier = source == CastSource.SWORD ? ServerConfigs.SWORDS_CD_MULTIPLIER.get().floatValue() : 1;
        int ticks = Math.max(0, (int) (spell.getSpellCooldown() * reduction * multiplier));
        MagicData.getPlayerMagicData(mob).getPlayerCooldowns().addCooldown(spell, ticks);
    }

    public static void fail(Mob mob, String spellId, RuntimeException exception) {
        var state = state(mob);
        if (!state.autoCastFailed)
            LOGGER.error("Mob spell failed: entity={}, spell={}, source={}", mob.getUUID(), spellId,
                    state.active == null ? "recast" : state.active.source, exception);
        state.autoCastFailed = true;
        state.clearCombo();
        var cast = state.active;
        if (cast != null) {
            try { finish(mob, true); }
            catch (RuntimeException cleanup) {
                state.active = null;
                MagicData.getPlayerMagicData(mob).resetCastingState();
                LOGGER.error("Mob cast cleanup failed: {}", mob.getUUID(), cleanup);
            }
        }
        var data = MagicData.getPlayerMagicData(mob);
        data.resetCastingState();
        data.setPlayerCastingItem(ItemStack.EMPTY);
        // A failed offensive group must not later complete with a normal timeout effect.
        for (var recast : data.getPlayerRecasts().getAllRecasts()) {
            if (!(recast.getCastData() instanceof SummonedEntitiesCastData))
                data.getPlayerRecasts().removeRecast(recast, RecastResult.USER_CANCEL);
        }
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        MobSpellSupport.expireSummon(event.getEntity());
        if (!(event.getEntity() instanceof Mob mob) || !isEnabled(mob) || mob instanceof IMagicEntity) return;
        try {
            bind(mob, false);
            var state = state(mob);
            var data = MagicData.getPlayerMagicData(mob);
            if (!mob.isAlive()) { disable(mob); return; }
            if (state.tagGeneration != tagGeneration) {
                state.tagGeneration = tagGeneration;
                state.clearCombo();
            }
            data.getPlayerCooldowns().tick(1);
            data.getPlayerRecasts().tick(1);
            data.setMana(Mth.clamp(data.getMana(), 0, (float) mob.getAttributeValue(MAX_MANA)));
            if (mob.tickCount % 10 == 0) {
                float max = (float) mob.getAttributeValue(MAX_MANA);
                float increment = (float) (max * mob.getAttributeValue(MANA_REGEN) * .01
                        * ShHsConfig.SERVER.mobManaRegenMultiplier.get());
                data.setMana(Mth.clamp(data.getMana() + increment, 0, max));
                MobSpellSupport.reconcileSummons(mob);
            }
            var cast = state.active;
            if (cast != null) {
                if (!validActive(mob, cast)) { finish(mob, true); return; }
                MobSpellSupport.aim(mob, cast.spell);
                int elapsed = data.getCastDuration() - data.getCastDurationRemaining();
                if (cast.spell.getCastType() == CastType.CONTINUOUS) {
                    if (elapsed % 10 == 0 && cast.lastPulse != elapsed) {
                        cast.lastPulse = elapsed;
                        if (!emit(mob, cast)) { finish(mob, true); return; }
                    }
                    data.handleCastDuration();
                    if (data.getCastDurationRemaining() == 0) { finish(mob, false); return; }
                } else {
                    data.handleCastDuration();
                    if (data.getCastDurationRemaining() == 0) {
                        boolean success = emit(mob, cast);
                        finish(mob, !success);
                        return;
                    }
                }
                if (state.active == cast) cast.spell.onServerCastTick(mob.level(), cast.level, mob, data);
            } else if (!state.autoCastFailed && mob.level().getGameTime() >= state.nextAction) {
                for (var recast : data.getPlayerRecasts().getActiveRecasts()) {
                    if (recast.getCastData() instanceof SummonedEntitiesCastData) continue;
                    if (mob.getTarget() == null || !Objects.equals(state.recastTarget, mob.getTarget().getUUID())) {
                        data.getPlayerRecasts().removeRecast(recast, RecastResult.USER_CANCEL);
                        state.clearCombo();
                        continue;
                    }
                    start(mob, SpellRegistry.getSpell(recast.getSpellId()), recast.getSpellLevel(),
                            recast.getCastSource(), null, true);
                    state.nextAction = mob.level().getGameTime() + ACTION_INTERVAL;
                    break;
                }
            }
        } catch (RuntimeException exception) {
            fail(mob, state(mob).active == null ? "tick" : state(mob).active.spell.getSpellId(), exception);
        }
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && isCasting(mob)
                && event.getNewDamage() > 0 && state(mob).active.spell.getCastType() == CastType.LONG)
            cancelCast(mob);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide
                || !isEnabled(mob)) return;
        try {
            bind(mob, false);
            finish(mob, true);
        }
        catch (RuntimeException exception) { fail(mob, "death", exception); }
        finally {
            MagicData.getPlayerMagicData(mob).getPlayerRecasts().removeAll(RecastResult.DEATH);
            state(mob).enabled = false;
        }
    }

    private static void onCounterspell(CounterSpellEvent event) {
        if (event.isCanceled() || !(event.target instanceof Mob mob) || mob.level().isClientSide
                || !isEnabled(mob)) return;
        bind(mob, false);
        try { finish(mob, true); }
        catch (RuntimeException exception) { fail(mob, "counterspell", exception); }
        finally {
            state(mob).clearCombo();
            MagicData.getPlayerMagicData(mob).getPlayerRecasts().removeAll(RecastResult.COUNTERSPELL);
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && isEnabled(mob))
            cancelCast(mob); // Unloading ends offensive casts, not summon ownership or opt-in state.
    }

    private static void onTags(TagsUpdatedEvent event) {
        tagGeneration++;
    }
}
