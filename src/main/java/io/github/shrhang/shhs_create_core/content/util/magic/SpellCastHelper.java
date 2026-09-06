package io.github.shrhang.shhs_create_core.content.util.magic;

import io.github.shrhang.shhs_create_core.api.events.SpellOnEntityCastEvent;
import io.github.shrhang.shhs_create_core.api.events.SpellPreEntityCastEvent;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.fire.BurningDashSpell;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.shrhang.shhs_create_core.content.data.ShHsTagKey.ENTITY_SPELL_BLACKLIST;
import static io.redspace.ironsspellbooks.api.registry.SchoolRegistry.HOLY_RESOURCE;
import static net.minecraft.world.entity.EquipmentSlot.MAINHAND;
import static net.minecraft.world.entity.EquipmentSlot.OFFHAND;

public class SpellCastHelper {
    /**
     * 法术的准入口，会检查施法条件与事件，所有施法都会经过这个方法处理。
     */
    public static boolean attemptInitiateEntityCast(ItemStack stack, LivingEntity entity, AbstractSpell spell, int spellLevel, CastSource castSource, boolean triggerCooldown, String castingEquipmentSlot) {
        Level level = entity.level();
        if (level.isClientSide) return false;
        MagicData entityMagicData = MagicData.getPlayerMagicData(entity);
        if (!entityMagicData.isCasting()) {
            if (!canBeCastedBy(spell, spellLevel, castSource, entityMagicData, entity).isSuccess() ||
                    !spell.checkPreCastConditions(level, spellLevel, entity, entityMagicData) ||
                    NeoForge.EVENT_BUS.post(new SpellPreEntityCastEvent(entity, spell.getSpellId(), spellLevel, spell.getSchoolType(), castSource)).isCanceled()) {
                return false;
            }

            if (spell == SpellRegistry.TELEPORT_SPELL.get() || spell == SpellRegistry.FROST_STEP_SPELL.get()) {
                setTeleportLocationBehindTarget((Targeting) entity,10);
            } else if (spell == SpellRegistry.BLOOD_STEP_SPELL.get()) {
                setTeleportLocationBehindTarget((Targeting) entity,3);
            } else if (spell == SpellRegistry.BURNING_DASH_SPELL.get()) {
                setBurningDashDirectionData(entity);
            }

            int effectiveCastTime = spell.getEffectiveCastTime(spellLevel, entity);

            entityMagicData.initiateCast(spell, spellLevel, effectiveCastTime, castSource, castingEquipmentSlot);
            entityMagicData.setPlayerCastingItem(stack);

            spell.onServerPreCast(level, spellLevel, entity, entityMagicData);

            return true;
        }
        return false;
    }

    /**
     * 判断法术是否允许被该实体使用。
     * 移除重施法次数限制，允许多段法术。
     */
    public static boolean isSpellAllowed(LivingEntity entity, AbstractSpell spell) {
        return !ENTITY_SPELL_BLACKLIST.contains(spell)
                && !(entity.getType().is(EntityTypeTags.UNDEAD) && spell.getSchoolType().getId() == HOLY_RESOURCE);
    }

    public static CastResult canBeCastedBy(AbstractSpell spell, int spellLevel, CastSource castSource, MagicData entityMagicData, LivingEntity entity) {
        if (entityMagicData.getPlayerCooldowns().isOnCooldown(spell))
            return new CastResult(CastResult.Type.FAILURE);
        else if (castSource.consumesMana() && entityMagicData.getMana() < spell.getManaCost(spellLevel))
            return new CastResult(CastResult.Type.FAILURE);
        else return new CastResult(CastResult.Type.SUCCESS);
    }

    /**
     * 调用法术的onCast方法，并处理施法结果相关的事件、法力消耗与冷却。适用于所有实体的施法，包括玩家与怪物。
     */
    public static void entityCastSpell(AbstractSpell spell, Level world, int spellLevel, LivingEntity entity, CastSource castSource, boolean triggerCooldown) {
        MagicData magicData = MagicData.getPlayerMagicData(entity);
        var entityRecasts = magicData.getPlayerRecasts();
        boolean entityAlreadyHasRecast = entityRecasts.hasRecastForSpell(spell.getSpellId());

        var event = new SpellOnEntityCastEvent(entity, spell.getSpellId(), spellLevel, spell.getManaCost(spellLevel), spell.getSchoolType(), castSource);
        NeoForge.EVENT_BUS.post(event);

        if (castSource.consumesMana() && !entityAlreadyHasRecast)
            magicData.setMana(Math.max(magicData.getMana() - event.getManaCost(), 0));
        if (entity instanceof Targeting targeting && targeting.getTarget() != null)
            forceLookAtTarget(entity, targeting.getTarget());
        spell.onCast(world, event.getSpellLevel(), entity, castSource, magicData);

        var entityHasRecastsLeft = entityRecasts.hasRecastForSpell(spell.getSpellId());
        if (entityAlreadyHasRecast && entityHasRecastsLeft) {
            entityRecasts.decrementRecastCount(spell.getSpellId());
        } else if (!entityHasRecastsLeft && triggerCooldown) {
            MobMagicManager.addCooldown(entity, spell, castSource);
        }
    }

    /**
     * 强制让实体面向目标，适用于施法时需要面向目标的情况。
     */
    public static void forceLookAtTarget(LivingEntity entity, LivingEntity target) {
        if (target != null) {
            double d0 = target.getX() - entity.getX();
            double d2 = target.getZ() - entity.getZ();
            double d1 = target.getEyeY() - entity.getEyeY();

            double d3 = Math.sqrt(d0 * d0 + d2 * d2);
            float f = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
            float f1 = (float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI)));
            entity.setXRot(f1 % 360);
            entity.setYRot(f % 360);
        }
    }

    /**
     * 设置实体的传送位置为目标的后方，距离由distance参数决定。适用于施法时需要传送到目标后方的情况。
     */
    public static void setTeleportLocationBehindTarget(Targeting entity, int distance) {
        LivingEntity livingEntity = (LivingEntity) entity;
        var target = entity.getTarget();
        boolean valid = false;
        var magicData = MagicData.getPlayerMagicData(livingEntity);
        if (target != null) {
            var rotation = target.getLookAngle().normalize().scale(-distance);
            var pos = target.position();
            var teleportPos = rotation.add(pos);
            for (int i = 0; i < 24; i++) {
                Vec3 randomness = Utils.getRandomVec3(.15f * i).multiply(1, 0, 1);
                teleportPos = Utils.moveToRelativeGroundLevel(livingEntity.level(), target.position().subtract(new Vec3(0, 0, distance / (float) (i / 7 + 1)).yRot(-(target.getYRot() + i * 45) * Mth.DEG_TO_RAD)).add(randomness), 5);
                teleportPos = new Vec3(teleportPos.x, teleportPos.y + .1f, teleportPos.z);
                var reposBB = livingEntity.getBoundingBox().move(teleportPos.subtract(livingEntity.position()));
                if (!livingEntity.level().collidesWithSuffocatingBlock(livingEntity, reposBB.inflate(-.05f))) {
                    valid = true;
                    break;
                }
            }
            if (valid) {
                magicData.setAdditionalCastData(new TeleportSpell.TeleportData(teleportPos));
            } else {
                magicData.setAdditionalCastData(new TeleportSpell.TeleportData(livingEntity.position()));
            }
        } else {
            magicData.setAdditionalCastData(new TeleportSpell.TeleportData(livingEntity.position()));
        }
    }

    /**
     * 设置实体释放烈焰冲锋的方向数据。
     */
    public static void setBurningDashDirectionData(LivingEntity entity) {
        MagicData.getPlayerMagicData(entity).setAdditionalCastData(new BurningDashSpell.BurningDashDirectionOverrideCastData());
    }

    /**
     * 获取实体身上的法术源
     * @param entity 传入的实体
     * @return {@link SpellSource} 列表
     */
    public static List<SpellSource> getEntitySpells(LivingEntity entity) {
        Map<AbstractSpell, SpellSource> highestLevelSpells = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (ISpellContainer.isSpellContainer(stack)) {
                ISpellContainer spellContainer = ISpellContainer.get(stack);
                if (spellContainer.isSpellWheel() && (!spellContainer.mustEquip() || !(slot.equals(MAINHAND) || slot.equals(OFFHAND)))) {
                    CastSource castSource = CastSource.SWORD;
                    if (stack.getItem() instanceof Scroll) {
                        castSource = CastSource.SCROLL;
                    } else if (stack.getItem() instanceof SpellBook) {
                        castSource = CastSource.SPELLBOOK;
                    }
                    var activeSpells = spellContainer.getActiveSpells();
                    for (SpellSlot spellSlot : activeSpells) {
                        SpellData currentData = spellSlot.spellData();
                        AbstractSpell spell = currentData.getSpell();
                        if (spell == SpellRegistry.none()) continue;
                        SpellSource existingOption = highestLevelSpells.get(spell);
                        if (existingOption == null || currentData.getLevel() > existingOption.spellData().getLevel()) {
                            highestLevelSpells.put(spell, new SpellSource(currentData, castSource, slot));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(highestLevelSpells.values());
    }

    /**
     * 记录一个法术来源的信息，包括法术数据、施法源和所在的装备槽位。
     */
    public record SpellSource(SpellData spellData, CastSource castSource, EquipmentSlot slot) {}
}