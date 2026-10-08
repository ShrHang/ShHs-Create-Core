package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import com.mojang.logging.LogUtils;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.*;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.spells.CastingMobAimingData;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.fire.WallOfFireSpell;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import static io.github.shrhang.shhs_create_core.content.data.ShHsTagKey.*;

public class MobSpellSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SUMMON_DATA = "shhs_create_core:mob_summon";

    public static boolean isSelf(AbstractSpell spell) {
        return MOB_SPELL_HEAL.contains(spell) || MOB_SPELL_DEFENSE.contains(spell)
                || MOB_SPELL_SUMMON.contains(spell);
    }

    public static boolean supports(AbstractSpell spell, int level, Mob mob) {
        if (spell.getRecastCount(level, mob) <= 0) return true;
        if (spell == SpellRegistry.WALL_OF_FIRE_SPELL.get()
                || spell.getEmptyCastData() instanceof SummonedEntitiesCastData) return true;
        // A player-specific finish override needs an explicit mob adaptation.
        try {
            if (spell.getClass().getMethod("onRecastFinished", ServerPlayer.class, RecastInstance.class,
                    RecastResult.class, ICastDataSerializable.class).getDeclaringClass() == AbstractSpell.class)
                return true;
        } catch (NoSuchMethodException exception) {
            LOGGER.debug("Missing native recast callback: {}", spell.getSpellId(), exception);
        }
        LOGGER.debug("Skipping mob spell {} for {}: player-specific recast finish needs adaptation",
                spell.getSpellId(), mob.getUUID());
        return false;
    }

    static boolean prepare(Mob mob, AbstractSpell spell) {
        MagicData data = MagicData.getPlayerMagicData(mob);
        if (spell == SpellRegistry.HEALING_CIRCLE_SPELL.get()) data.setAdditionalCastData(new TargetEntityCastData(mob));
        if (spell == SpellRegistry.TELEPORT_SPELL.get() || spell == SpellRegistry.FROST_STEP_SPELL.get()
                || spell == SpellRegistry.BLOOD_STEP_SPELL.get()) {
            var target = mob.getTarget();
            if (target == null) return false;
            boolean retreat = MOB_SPELL_ESCAPE.contains(spell) && mob.getHealth() < mob.getMaxHealth() * .35f;
            Vec3 origin = retreat ? mob.position() : target.position();
            Vec3 direction = retreat ? mob.position().subtract(target.position())
                    : target.getLookAngle().scale(-1);
            direction = new Vec3(direction.x, 0, direction.z).normalize();
            for (int i = 0; i < 12; i++) {
                Vec3 offset = direction.yRot((float) (i * Math.PI / 6)).scale(retreat ? 8 : 3);
                Vec3 destination = Utils.moveToRelativeGroundLevel(mob.level(), origin.add(offset), 5).add(0, .01, 0);
                if (safeDestination(mob, destination)) {
                    data.setAdditionalCastData(new TeleportSpell.TeleportData(destination));
                    return true;
                }
            }
            return false;
        }
        if (spell == SpellRegistry.RAY_OF_SIPHONING_SPELL.get())
            data.setAdditionalCastData(new CastingMobAimingData());
        return true;
    }

    static void aim(Mob mob, AbstractSpell spell) {
        var target = mob.getTarget();
        if (target == null || isSelf(spell)) return;
        if (spell == SpellRegistry.WALL_OF_FIRE_SPELL.get()) {
            var recast = MagicData.getPlayerMagicData(mob).getPlayerRecasts().getRecastInstance(spell.getSpellId());
            int index = recast == null ? 0 : recast.getTotalRecasts() - recast.getRemainingRecasts();
            Vec3 forward = target.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
            Vec3 lateral = new Vec3(-forward.z, 0, forward.x).scale((index - 1) * 3);
            SpellCastHelper.lookAt(mob, target.position().add(lateral).add(0, .1, 0));
        } else {
            SpellCastHelper.forceLookAtTarget(mob, target);
        }
    }

    static boolean useful(Mob mob, AbstractSpell spell) {
        if (MOB_SPELL_HEAL.contains(spell)) return mob.getHealth() < mob.getMaxHealth();
        if (spell == SpellRegistry.OAKSKIN_SPELL.get())
            return mob.getEffect(MobEffectRegistry.OAKSKIN) == null
                    || mob.getEffect(MobEffectRegistry.OAKSKIN).getDuration() < 40;
        if (spell == SpellRegistry.FORTIFY_SPELL.get())
            return mob.getEffect(MobEffectRegistry.FORTIFY) == null
                    || mob.getEffect(MobEffectRegistry.FORTIFY).getDuration() < 40;
        return true;
    }

    static boolean safeDestination(Mob mob, Vec3 destination) {
        var level = mob.level();
        BlockPos feet = BlockPos.containing(destination);
        var box = mob.getBoundingBox().move(destination.subtract(mob.position()));
        return level.isLoaded(feet) && level.getWorldBorder().isWithinBounds(box)
                && destination.y >= level.getMinBuildHeight() && box.maxY < level.getMaxBuildHeight()
                && level.noCollision(mob, box) && level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }

    public static void finishRecast(Mob mob, RecastInstance recast, RecastResult result) {
        var spell = SpellRegistry.getSpell(recast.getSpellId());
        var data = MagicData.getPlayerMagicData(mob);
        if (recast.getCastData() instanceof SummonedEntitiesCastData summons) {
            if (result == RecastResult.COUNTERSPELL) {
                data.getPlayerRecasts().forceAddRecast(recast);
                return;
            }
            for (var uuid : summons.getSummons().stream().toList()) {
                for (ServerLevel level : mob.getServer().getAllLevels()) {
                    Entity summon = level.getEntity(uuid);
                    if (summon != null) removeSummon(summon);
                }
            }
        } else if (spell instanceof WallOfFireSpell && recast.getCastData() instanceof WallOfFireSpell.FireWallData wall) {
            if (result != RecastResult.DEATH && result != RecastResult.COUNTERSPELL
                    && result != RecastResult.USER_CANCEL && wall.anchorPoints.size() >= 2) {
                WallOfFireEntity entity = new WallOfFireEntity(mob.level(), mob, wall.anchorPoints,
                        spell.getSpellPower(recast.getSpellLevel(), mob));
                Vec3 center = Vec3.ZERO;
                for (Vec3 anchor : wall.anchorPoints) center = center.add(anchor);
                entity.setPos(center.scale(1.0 / wall.anchorPoints.size()));
                mob.level().addFreshEntity(entity);
            }
        }
        MobMagicManager.addCooldown(mob, spell, recast.getCastSource());
        var state = MobMagicManager.state(mob);
        if (state.active != null && state.active.spell.equals(spell)) state.active.cooldownApplied = true;
    }

    static void trackSummons(Mob mob, RecastInstance recast) {
        if (!(recast.getCastData() instanceof SummonedEntitiesCastData summons)) return;
        for (var uuid : summons.getSummons()) {
            Entity entity = ((ServerLevel) mob.level()).getEntity(uuid);
            if (entity == null) continue;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", mob.getUUID());
            tag.putLong("expires", mob.level().getGameTime() + recast.getTicksRemaining());
            entity.getPersistentData().put(SUMMON_DATA, tag);
        }
    }

    static void reconcileSummons(Mob mob) {
        var data = MagicData.getPlayerMagicData(mob);
        var registered = SummonManager.getSummons(mob);
        for (var recast : data.getPlayerRecasts().getAllRecasts()) {
            if (recast.getCastData() instanceof SummonedEntitiesCastData summons) {
                for (var uuid : summons.getSummons().stream().toList()) {
                    if (!registered.contains(uuid)) {
                        summons.handleRemove(uuid, data, recast);
                        if (summons.getSummons().isEmpty()) break;
                    }
                }
            }
        }
    }

    public static void expireSummon(Entity entity) {
        if (!entity.getPersistentData().contains(SUMMON_DATA)) return;
        var tag = entity.getPersistentData().getCompound(SUMMON_DATA);
        if (entity.level().getGameTime() >= tag.getLong("expires")) removeSummon(entity);
        else if (tag.hasUUID("owner") && entity.level() instanceof ServerLevel level
                && level.getEntity(tag.getUUID("owner")) instanceof Mob owner
                && (!owner.isAlive() || !MobMagicManager.isEnabled(owner))) removeSummon(entity);
    }

    private static void removeSummon(Entity entity) {
        if (entity instanceof IMagicSummon summon) summon.onUnSummon();
        else entity.discard();
        SummonManager.removeSummon(entity);
    }
}
