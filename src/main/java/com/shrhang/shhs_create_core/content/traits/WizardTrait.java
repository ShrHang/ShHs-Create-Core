package com.shrhang.shhs_create_core.content.traits;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.magic.MobMagicManager;
import com.shrhang.shhs_create_core.content.util.SpellQueueHelper;
import com.shrhang.shhs_create_core.content.util.SpellCastHelper;
import com.shrhang.shhs_create_core.content.util.SpellCastHelper.SpellSource;
import dev.xkmc.l2hostility.content.traits.legendary.LegendaryTrait;
import dev.xkmc.l2hostility.init.registrate.LHEnchantments;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.loot.SpellFilter;
import io.redspace.ironsspellbooks.registries.DataAttachmentRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.*;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;
/** 巫师词条，赋予生物施法能力。负责法术书初始化、属性加成，以及决策周期内的法术选择（单发或连招）。施法状态机由 MobMagicManager 推进，连招队列由 SpellQueueHelper 调度。 */
public class WizardTrait extends LegendaryTrait {
    // 配置参数
    private static final float COMBO_TRIGGER_MANA_RATIO = 0.5f;
    private static final float COMBO_CHANCE = 0.3f;
    private static final int COMBO_MAX_HITS = 3;
    private static final float DELAY_CHANCE = 0.1f;
    private static final float LOW_MANA_DELAY_RATIO = 0.2f;
    private static final int DECISION_INTERVAL = 40;
    private static final int MAX_SPELLBOOK_SLOTS = 20;
    private static final int MAX_RANDOM_ATTEMPTS = 100;
    private static final float COMBO_TERMINATOR_SHORT_CHANCE = 0.25f;
    private static final int COMBO_SEARCH_ATTEMPTS = 20;
    // 属性修饰符
    private static final ResourceLocation MANA_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_mana_bonus");
    private static final ResourceLocation REGEN_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_regen_bonus");
    private static final float MANA_BONUS_PER_LEVEL = 0.2f;
    private static final float REGEN_BONUS_PER_LEVEL = 0.1f;
    // 认知值存储键
    private static final String PERCEPTION_TAG = "wizard_mana_perception";
    private static final float PERCEPTION_INITIAL = 1.0f;
    private static final float PERCEPTION_MAX = 2.0f;
    private static final float PERCEPTION_INCREASE = 0.2f;
    private static final float PERCEPTION_DECAY = 0.01f;
    // 法术缓存结构
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
            SpellEntry(SpellSource source, int cost) { this.source = source; this.cost = cost; }
        }
    }
    // 搜索返回值
    private static class SearchResult {
        final List<SpellSource> combo;
        final int attempts;
        SearchResult(List<SpellSource> combo, int attempts) { this.combo = combo; this.attempts = attempts; }
    }

    public WizardTrait(IntSupplier color) {
        super(color);
    }

    @Override
    public void postInit(LivingEntity entity, int traitLV) {
        applyAttributeBonuses(entity, traitLV);
        grantSpellbook(entity, traitLV);
        entity.setData(DataAttachmentRegistry.MAGIC_DATA, new MagicData(true));
        // 预填充缓存（延迟到首次决策时构建）
    }

    private void applyAttributeBonuses(LivingEntity entity, int traitLV) {
        if (traitLV <= 0) return;
        AttributeInstance manaAttr = entity.getAttribute(MAX_MANA);
        AttributeInstance regenAttr = entity.getAttribute(MANA_REGEN);
        if (manaAttr == null || regenAttr == null) return;
        manaAttr.removeModifier(MANA_MODIFIER_ID);
        regenAttr.removeModifier(REGEN_MODIFIER_ID);
        manaAttr.addPermanentModifier(new AttributeModifier(MANA_MODIFIER_ID, MANA_BONUS_PER_LEVEL * traitLV, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        regenAttr.addPermanentModifier(new AttributeModifier(REGEN_MODIFIER_ID, REGEN_BONUS_PER_LEVEL * traitLV, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private void grantSpellbook(LivingEntity entity, int traitLV) {
        if (traitLV == 0) {
            ItemStack offhand = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (offhand.is(ItemRegistry.WIMPY_SPELL_BOOK.get()) &&
                    offhand.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(LHEnchantments.VANISH.holder()) != 0) {
                entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            return;
        }
        ItemStack book = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(LHEnchantments.VANISH.holder(), 1);
        EnchantmentHelper.setEnchantments(book, enchants.toImmutable());
        ISpellContainerMutable container = ISpellContainer.create(Math.min(traitLV * 2, MAX_SPELLBOOK_SLOTS), true, false).mutableCopy();
        SpellFilter filter = new SpellFilter();
        RandomSource random = entity.getRandom();
        for (int i = 0; i < traitLV * 2; i++) {
            AbstractSpell spell = null;
            int attempts = 0;
            while (attempts++ < MAX_RANDOM_ATTEMPTS) {
                AbstractSpell candidate = filter.getRandomSpell(random, s -> isSpellAllowed(entity, s));
                int level = random.nextIntBetweenInclusive(1, Math.min(traitLV, candidate.getMaxLevel()));
                if (container.addSpell(candidate, level, false)) {
                    spell = candidate;
                    break;
                }
            }
            if (spell == null) {
                var active = container.getActiveSpells();
                if (!active.isEmpty()) {
                    var existing = active.get(random.nextInt(active.size()));
                    if (!container.addSpell(existing.getSpell(), existing.getLevel(), false)) break;
                } else break;
            }
        }
        ISpellContainer.set(book, container.toImmutable());
        entity.setItemInHand(InteractionHand.OFF_HAND, book);
    }

    @Override
    public boolean allow(LivingEntity entity, int difficulty, int maxModLv) {
        return super.allow(entity, difficulty, maxModLv) &&
                entity instanceof Targeting &&
                !(entity instanceof AbstractSpellCastingMob);
    }

    @Override
    public void tick(LivingEntity entity, int level) {
        MobMagicManager.tick(entity);
        SpellQueueHelper.tickQueue(entity);
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
    // ---------- 决策辅助 ----------
    private boolean isDecisionTime(LivingEntity entity) {
        return entity.level().getGameTime() % DECISION_INTERVAL == 0;
    }

    private boolean hasValidTarget(LivingEntity entity) {
        if (!(entity instanceof Targeting targeting)) return false;
        return targeting.getTarget() != null;
    }

    private float calculateManaRatio(LivingEntity entity, MagicData magicData) {
        float maxMana = (float) entity.getAttributeValue(MAX_MANA);
        return maxMana > 0 ? magicData.getMana() / maxMana : 0;
    }

    private boolean shouldSkipDecision(LivingEntity entity, float manaRatio, float perception) {
        float threshold = LOW_MANA_DELAY_RATIO * perception;
        return manaRatio < threshold || entity.getRandom().nextFloat() < DELAY_CHANCE;
    }

    private boolean shouldAttemptCombo(float manaRatio, RandomSource random) {
        return manaRatio > COMBO_TRIGGER_MANA_RATIO && random.nextFloat() < COMBO_CHANCE;
    }
    // ---------- 缓存构建 ----------
    private SpellCache buildSpellCache(LivingEntity entity) {
        List<SpellSource> allSpells = SpellCastHelper.getEntitySpells(entity);
        return allSpells.isEmpty() ? null : new SpellCache(allSpells);
    }
    // ---------- 连招执行 ----------
    private boolean tryExecuteCombo(LivingEntity entity, SpellCache cache, MagicData magicData) {
        if (!(entity instanceof Targeting targeting) || targeting.getTarget() == null) return false;
        if (cache.instantSortedByCost.isEmpty()) return false;
        float currentMana = magicData.getMana();
        SearchResult result = findOptimalCombo(cache, currentMana, COMBO_MAX_HITS, entity.getRandom());
        if (result.combo == null || result.combo.isEmpty()) {
            updatePerception(entity, true, result.attempts);
            return false;
        }
        if (result.attempts > COMBO_SEARCH_ATTEMPTS / 2) {
            updatePerception(entity, false, result.attempts);
        }
        // 验证连招内所有法术
        for (SpellSource src : result.combo) {
            if (!validateSpell(entity, src)) return false;
        }
        List<SpellSource> combo = result.combo;
        SpellSource first = combo.get(0);
        // 立即施放第一个
        AbstractSpell spell = first.spellData().getSpell();
        int level = first.spellData().getLevel();
        if (!magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            ItemStack stack = entity.getItemBySlot(first.slot());
            attemptInitiateEntityCast(stack, entity, spell, level, first.castSource(), true, getSlotName(first.slot()));
        }
        // 剩余法术提交给队列
        if (combo.size() > 1) {
            SpellQueueHelper.submitCombo(entity, combo.subList(1, combo.size()), 5);
        } else {
            updatePerception(entity, false, 0);
        }
        return true;
    }
    // ---------- 单发执行 ----------
    private void executeSingleSpell(LivingEntity entity, SpellCache cache, MagicData magicData) {
        if (cache.allSources.isEmpty()) return;
        RandomSource random = entity.getRandom();
        SpellSource selection = cache.allSources.get(random.nextInt(cache.allSources.size()));
        if (!validateSpell(entity, selection)) return;
        SpellData spellData = selection.spellData();
        AbstractSpell spell = spellData.getSpell();
        if (!isSpellAllowed(entity, spell)) return;
        int cost = spell.getManaCost(spellData.getLevel());
        if (magicData.getMana() < cost) {
            updatePerception(entity, true, 0);
            return;
        }
        ItemStack stack = entity.getItemBySlot(selection.slot());
        boolean success = attemptInitiateEntityCast(stack, entity, spell, spellData.getLevel(), selection.castSource(), true, getSlotName(selection.slot()));
        if (success) {
            updatePerception(entity, false, 0);
        }
    }
    // ---------- 连招搜索算法 ----------
    private SearchResult findOptimalCombo(SpellCache cache, float currentMana, int maxHits, RandomSource random) {
        List<SpellCache.SpellEntry> instants = cache.instantSortedByCost;
        if (instants.isEmpty() || maxHits < 2) return new SearchResult(null, 0);
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
            if (firstUpper < instants.get(0).cost) continue;
            SpellCache.SpellEntry first = pickBelow.apply(instants, firstUpper);
            if (first == null) continue;
            List<SpellCache.SpellEntry> termCandidates;
            if (random.nextFloat() < COMBO_TERMINATOR_SHORT_CHANCE || cache.nonInstantSortedByCost.isEmpty()) {
                termCandidates = instants;
            } else {
                termCandidates = cache.nonInstantSortedByCost;
            }
            List<SpellCache.SpellEntry> filteredTerm = termCandidates.stream().filter(e -> e != first).collect(Collectors.toList());
            if (filteredTerm.isEmpty()) continue;
            float termUpper = currentMana - first.cost;
            SpellCache.SpellEntry terminator = pickBelow.apply(filteredTerm, termUpper);
            if (terminator == null) continue;
            if (maxHits >= 3) {
                float remain = currentMana - first.cost - terminator.cost;
                if (remain >= instants.get(0).cost) {
                    List<SpellCache.SpellEntry> middleCandidates = instants.stream().filter(e -> e != first && e != terminator).collect(Collectors.toList());
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
    // ---------- 法术验证 ----------
    private boolean validateSpell(LivingEntity entity, SpellSource source) {
        ItemStack stack = entity.getItemBySlot(source.slot());
        if (!ISpellContainer.isSpellContainer(stack)) return false;
        ISpellContainer container = ISpellContainer.get(stack);
        AbstractSpell spell = source.spellData().getSpell();
        int level = source.spellData().getLevel();
        for (var slot : container.getActiveSpells()) {
            SpellData data = slot.spellData();
            if (data.getSpell() == spell && data.getLevel() == level) return true;
        }
        return false;
    }

    // ---------- 认知管理 ----------
    private float getPerception(LivingEntity entity) {
        return entity.getPersistentData().getFloat(PERCEPTION_TAG);
    }

    private void updatePerception(LivingEntity entity, boolean manaShortage, int searchAttempts) {
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
    // ---------- 工具 ----------
    private static String getSlotName(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) return "mainhand";
        if (slot == EquipmentSlot.OFFHAND) return "offhand";
        return "other";
    }
    @SuppressWarnings("unused")
    public static void clearCache(LivingEntity entity) {
        SpellQueueHelper.clearQueue(entity);
    }
}