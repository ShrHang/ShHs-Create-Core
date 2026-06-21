package com.shrhang.shhs_create_core.content.util;

import com.shrhang.shhs_create_core.content.util.SpellCastHelper.SpellSource;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.WeakHashMap;

import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.attemptInitiateEntityCast;
/** 管理生物的连招队列调度，仅在非施法状态下推进下一发法术。 */
public class SpellQueueHelper {
    private static final int COMBO_INTERVAL_TICKS = 5;
    private static final WeakHashMap<LivingEntity, ComboState> QUEUES = new WeakHashMap<>();

    private static class ComboState {
        final List<SpellSource> spells;
        int index;
        long nextCastTime;
        boolean manaShortage;
        ComboState(List<SpellSource> spells, long firstCastTime) {
            this.spells = spells;
            this.index = 0;
            this.nextCastTime = firstCastTime;
            this.manaShortage = false;
        }
    }
    /** 提交连招剩余部分，从指定延迟后开始施放。 */
    public static void submitCombo(LivingEntity entity, List<SpellSource> remainingSpells, long delayFromNow) {
        if (remainingSpells.isEmpty()) return;
        QUEUES.put(entity, new ComboState(remainingSpells, entity.level().getGameTime() + delayFromNow));
    }
    /** 检查是否有活动的连招队列。 */
    public static boolean hasActiveCombo(LivingEntity entity) {
        ComboState state = QUEUES.get(entity);
        return state != null && state.index < state.spells.size();
    }
    /** 每 tick 推进队列，仅在非施法时执行下一发。 */
    public static void tickQueue(LivingEntity entity) {
        ComboState state = QUEUES.get(entity);
        if (state == null) return;
        if (state.index >= state.spells.size()) {
            QUEUES.remove(entity);
            return;
        }
        MagicData magicData = MagicData.getPlayerMagicData(entity);
        if (magicData.isCasting()) return;
        long gameTime = entity.level().getGameTime();
        if (gameTime < state.nextCastTime) return;
        SpellSource source = state.spells.get(state.index);
        AbstractSpell spell = source.spellData().getSpell();
        int level = source.spellData().getLevel();
        if (magicData.getMana() < spell.getManaCost(level)) {
            state.manaShortage = true;
            return;
        }
        if (!magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            ItemStack stack = entity.getItemBySlot(source.slot());
            attemptInitiateEntityCast(stack, entity, spell, level, source.castSource(), true, getSlotName(source.slot()));
        }
        state.index++;
        if (state.index < state.spells.size()) {
            state.nextCastTime = entity.level().getGameTime() + COMBO_INTERVAL_TICKS;
        } else {
            QUEUES.remove(entity);
        }
    }
    /** 清除生物的连招队列。 */
    public static void clearQueue(LivingEntity entity) {
        QUEUES.remove(entity);
    }
    private static String getSlotName(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) return "mainhand";
        if (slot == EquipmentSlot.OFFHAND) return "offhand";
        return "other";
    }
}