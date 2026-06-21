package com.shrhang.shhs_create_core.content.util;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.*;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;
/**
 * 法术决策辅助类，负责从实体法术列表中决策单发或连招施法。
 * 包含法术缓存、连招搜索、感知管理以及执行单发/连招的核心逻辑。
 */
public class SpellDecisionHelper {
    // 决策参数
    private static final float COMBO_TRIGGER_MANA_RATIO = 0.5f;   // 触发连招的最低法力比例
    private static final float COMBO_CHANCE = 0.3f;              // 尝试连招的概率
    private static final int COMBO_MAX_HITS = 3;                 // 连招最大法术数量
    private static final float DELAY_CHANCE = 0.1f;              // 随机延迟施法的概率
    private static final float LOW_MANA_DELAY_RATIO = 0.2f;      // 低法力延迟阈值系数
    private static final int DECISION_INTERVAL = 40;             // 决策间隔（tick）
    private static final float COMBO_TERMINATOR_SHORT_CHANCE = 0.25f; // 终止法术选择短施法类型的概率
    private static final int COMBO_SEARCH_ATTEMPTS = 20;         // 连招搜索尝试次数
    // 感知管理参数
    private static final String PERCEPTION_TAG = "wizard_mana_perception";
    private static final float PERCEPTION_INITIAL = 1.0f;
    private static final float PERCEPTION_MAX = 2.0f;
    private static final float PERCEPTION_INCREASE = 0.2f;
    private static final float PERCEPTION_DECAY = 0.01f;
    /**
     * 法术缓存，按法力消耗排序，便于连招搜索。
     */
    private static class SpellCache {
        final List<SpellSource> allSources;
        final List<SpellEntry> instantSortedByCost;
        final List<SpellEntry> nonInstantSortedByCost;

        SpellCache(List<SpellSource> sources) {
            this.allSources = Collections.unmodifiableList(sources);
            List<SpellEntry> tempInstant = new ArrayList<>();
            List<SpellEntry> tempNonInstant = new ArrayList<>();
            for (SpellSource src : sources) {
                int cost = src.spellData().getSpell().getManaCost(src.spellData().getLevel());
                boolean isInstant = src.spellData().getSpell().getCastType() == CastType.INSTANT;
                SpellEntry entry = new SpellEntry(src, cost);
                if (isInstant) tempInstant.add(entry);
                else tempNonInstant.add(entry);
            }
            tempInstant.sort(Comparator.comparingInt(e -> e.cost));
            tempNonInstant.sort(Comparator.comparingInt(e -> e.cost));
            this.instantSortedByCost = Collections.unmodifiableList(tempInstant);
            this.nonInstantSortedByCost = Collections.unmodifiableList(tempNonInstant);
        }

        static class SpellEntry {
            final SpellSource source;
            final int cost;
            SpellEntry(SpellSource source, int cost) {
                this.source = source;
                this.cost = cost;
            }
        }
    }
    /**
     * 连招搜索结果，包含选中的法术列表和尝试次数。
     */
    private static class SearchResult {
        final List<SpellSource> combo;
        final int attempts;
        SearchResult(List<SpellSource> combo, int attempts) {
            this.combo = combo;
            this.attempts = attempts;
        }
    }
    /**
     * 决策入口，由外部每 tick 调用。
     * 仅当满足时间间隔、有目标、且不在连招队列中时才进行决策。
     */
    public static void tick(LivingEntity entity) {
        if (SpellQueueHelper.hasActiveCombo(entity)) return;
        if (!isDecisionTime(entity)) return;
        if (!hasValidTarget(entity)) return;

        SpellCache cache = buildSpellCache(entity);
        if (cache == null) return;

        MagicData magicData = MagicData.getPlayerMagicData(entity);
        float manaRatio = calculateManaRatio(entity, magicData);
        float perception = getPerception(entity);

        if (shouldSkipDecision(entity, manaRatio, perception)) return;

        if (shouldAttemptCombo(manaRatio, entity.getRandom())) {
            if (tryExecuteCombo(entity, cache, magicData)) return;
            cache = buildSpellCache(entity);
            if (cache == null) return;
        }

        executeSingleSpell(entity, cache, magicData);
    }
    /**
     * 判断是否到达决策时间点（每 40 tick 一次）。
     */
    private static boolean isDecisionTime(LivingEntity entity) {
        return entity.level().getGameTime() % DECISION_INTERVAL == 0;
    }
    /**
     * 检查实体是否有有效的攻击目标。
     */
    private static boolean hasValidTarget(LivingEntity entity) {
        if (!(entity instanceof Targeting targeting)) return false;
        return targeting.getTarget() != null;
    }
    /**
     * 计算当前法力比例（当前法力/最大法力）。
     */
    private static float calculateManaRatio(LivingEntity entity, MagicData magicData) {
        float maxMana = (float) entity.getAttributeValue(MAX_MANA);
        return maxMana > 0 ? magicData.getMana() / maxMana : 0;
    }
    /**
     * 判断是否跳过本次决策（低法力或随机延迟）。
     */
    private static boolean shouldSkipDecision(LivingEntity entity, float manaRatio, float perception) {
        float threshold = LOW_MANA_DELAY_RATIO * perception;
        return manaRatio < threshold || entity.getRandom().nextFloat() < DELAY_CHANCE;
    }
    /**
     * 是否尝试连招（法力充足且随机概率命中）。
     */
    private static boolean shouldAttemptCombo(float manaRatio, RandomSource random) {
        return manaRatio > COMBO_TRIGGER_MANA_RATIO && random.nextFloat() < COMBO_CHANCE;
    }
    /**
     * 构建当前实体的法术缓存（从各个物品栏获取法术）。
     */
    private static SpellCache buildSpellCache(LivingEntity entity) {
        List<SpellSource> allSpells = SpellCastHelper.getEntitySpells(entity);
        return allSpells.isEmpty() ? null : new SpellCache(allSpells);
    }
    /**
     * 尝试执行连招，若成功返回 true。
     * 会搜索最优连招，并立即施放第一个，其余加入队列。
     */
    private static boolean tryExecuteCombo(LivingEntity entity, SpellCache cache, MagicData magicData) {
        if (!(entity instanceof Targeting targeting) || targeting.getTarget() == null) return false;
        if (cache.instantSortedByCost.isEmpty()) return false;

        float currentMana = magicData.getMana();
        SearchResult result = findOptimalCombo(cache, currentMana, entity.getRandom());
        if (result.combo == null || result.combo.isEmpty()) {
            updatePerception(entity, true, result.attempts);
            return false;
        }

        if (result.attempts > COMBO_SEARCH_ATTEMPTS / 2) {
            updatePerception(entity, false, result.attempts);
        }

        for (SpellSource src : result.combo) {
            if (validateSpell(entity, src)) return false;
        }

        List<SpellSource> combo = result.combo;
        SpellSource first = combo.getFirst();

        AbstractSpell spell = first.spellData().getSpell();
        int level = first.spellData().getLevel();
        if (!magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            ItemStack stack = entity.getItemBySlot(first.slot());
            attemptInitiateEntityCast(stack, entity, spell, level, first.castSource(), true, getSlotName(first.slot()));
        }

        if (combo.size() > 1) {
            SpellQueueHelper.submitCombo(entity, combo.subList(1, combo.size()), 5);
        } else {
            updatePerception(entity, false, 0);
        }
        return true;
    }
    /**
     * 执行单发法术：随机选取一个可用法术，校验后施放。
     */
    private static void executeSingleSpell(LivingEntity entity, SpellCache cache, MagicData magicData) {
        if (cache.allSources.isEmpty()) return;

        RandomSource random = entity.getRandom();
        SpellSource selection = cache.allSources.get(random.nextInt(cache.allSources.size()));
        if (validateSpell(entity, selection)) return;

        SpellData spellData = selection.spellData();
        AbstractSpell spell = spellData.getSpell();
        if (!SpellCastHelper.isSpellAllowed(entity, spell)) return;

        int cost = spell.getManaCost(spellData.getLevel());
        if (magicData.getMana() < cost) {
            updatePerception(entity, true, 0);
            return;
        }

        ItemStack stack = entity.getItemBySlot(selection.slot());
        boolean success = attemptInitiateEntityCast(stack, entity, spell, spellData.getLevel(),
                selection.castSource(), true, getSlotName(selection.slot()));
        if (success) {
            updatePerception(entity, false, 0);
        }
    }
    /**
     * 搜索最优连招组合，返回选定法术列表和尝试次数。
     * 算法尝试选择一个起始法术（消耗约60%法力）和一个终止法术，若最大连击数≥3则尝试插入中间法术。
     */
    private static SearchResult findOptimalCombo(SpellCache cache, float currentMana, RandomSource random) {
        List<SpellCache.SpellEntry> instants = cache.instantSortedByCost;
        if (instants.isEmpty() || SpellDecisionHelper.COMBO_MAX_HITS < 2) return new SearchResult(null, 0);

        // 从列表中选取消耗不超过上限的随机法术
        java.util.function.BiFunction<List<SpellCache.SpellEntry>, Float, SpellCache.SpellEntry> pickBelow = (list, max) -> {
            if (list.isEmpty()) return null;
            int lo = 0, hi = list.size() - 1;
            int idx = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (list.get(mid).cost <= max) {
                    idx = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (idx == -1) return null;
            int chosen = random.nextInt(idx + 1);
            return list.get(chosen);
        };

        int attempts = 0;
        for (int attempt = 0; attempt < COMBO_SEARCH_ATTEMPTS; attempt++) {
            attempts++;
            float firstUpper = (attempt == 0) ? currentMana * 0.6f : currentMana * (0.6f - attempt * 0.02f);
            if (firstUpper < instants.getFirst().cost) continue;

            SpellCache.SpellEntry first = pickBelow.apply(instants, firstUpper);
            if (first == null) continue;

            // 选择终止法术候选列表：可能为瞬发或非瞬发（根据概率决定）
            List<SpellCache.SpellEntry> termCandidates;
            if (random.nextFloat() < COMBO_TERMINATOR_SHORT_CHANCE || cache.nonInstantSortedByCost.isEmpty()) {
                termCandidates = instants;
            } else {
                termCandidates = cache.nonInstantSortedByCost;
            }

            List<SpellCache.SpellEntry> filteredTerm = termCandidates.stream()
                    .filter(e -> e != first)
                    .collect(Collectors.toList());
            if (filteredTerm.isEmpty()) continue;

            float termUpper = currentMana - first.cost;
            SpellCache.SpellEntry terminator = pickBelow.apply(filteredTerm, termUpper);
            if (terminator == null) continue;

            // 若允许3连击，尝试插入中间法术
            if (SpellDecisionHelper.COMBO_MAX_HITS >= 3) {
                float remain = currentMana - first.cost - terminator.cost;
                if (remain >= instants.getFirst().cost) {
                    List<SpellCache.SpellEntry> middleCandidates = instants.stream()
                            .filter(e -> e != first && e != terminator)
                            .collect(Collectors.toList());
                    SpellCache.SpellEntry middle = pickBelow.apply(middleCandidates, remain);
                    if (middle != null) {
                        return new SearchResult(Arrays.asList(first.source, middle.source, terminator.source), attempts);
                    }
                }
            } else {
                return new SearchResult(Arrays.asList(first.source, terminator.source), attempts);
            }
        }
        return new SearchResult(null, attempts);
    }
    /**
     * 验证法术源是否仍存在于实体的物品栏中（防止物品被换掉）。
     */
    private static boolean validateSpell(LivingEntity entity, SpellSource source) {
        ItemStack stack = entity.getItemBySlot(source.slot());
        if (!ISpellContainer.isSpellContainer(stack)) return true;
        ISpellContainer container = ISpellContainer.get(stack);
        AbstractSpell spell = source.spellData().getSpell();
        int level = source.spellData().getLevel();
        for (var slot : container.getActiveSpells()) {
            SpellData data = slot.spellData();
            if (data.getSpell() == spell && data.getLevel() == level) return false;
        }
        return true;
    }
    /**
     * 获取当前实体的感知值（法力感知，用于调整低法力延迟阈值）。
     */
    private static float getPerception(LivingEntity entity) {
        return entity.getPersistentData().getFloat(PERCEPTION_TAG);
    }
    /**
     * 更新感知值：法力短缺增加感知，施法成功则减少；搜索尝试次数也会影响增量。
     */
    private static void updatePerception(LivingEntity entity, boolean manaShortage, int searchAttempts) {
        float perception = getPerception(entity);
        if (perception == 0) perception = PERCEPTION_INITIAL;
        float range = PERCEPTION_MAX - PERCEPTION_INITIAL;
        float offset = perception - PERCEPTION_INITIAL;
        float baseIncrement = manaShortage ? PERCEPTION_INCREASE : -PERCEPTION_DECAY;
        float costFactor = searchAttempts > 0 ? (1.0f + (searchAttempts / (float) COMBO_SEARCH_ATTEMPTS)) : 1.0f;
        float scale = manaShortage ? (1.0f - offset / range) : (offset / range);
        float increment = baseIncrement * scale * costFactor;
        float newPerception = Math.min(PERCEPTION_MAX, Math.max(PERCEPTION_INITIAL, perception + increment));
        entity.getPersistentData().putFloat(PERCEPTION_TAG, newPerception);
    }
    /**
     * 获取槽位名称（用于施法记录）。
     */
    private static String getSlotName(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) return "mainhand";
        if (slot == EquipmentSlot.OFFHAND) return "offhand";
        return "other";
    }
}