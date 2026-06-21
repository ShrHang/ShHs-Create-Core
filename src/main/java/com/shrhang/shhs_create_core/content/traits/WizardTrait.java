package com.shrhang.shhs_create_core.content.traits;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.magic.MobMagicManager;
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
import net.minecraft.nbt.CompoundTag;
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
import static io.redspace.ironsspellbooks.api.magic.SpellSelectionManager.OFFHAND;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN;
import static net.minecraft.world.entity.EquipmentSlot.MAINHAND;

/**
 * 感知值存储于实体的 PersistentData 中持久化；法术缓存与指纹打包为 CachedSpellData 存储于 WeakHashMap；
 * 连招状态同样使用 WeakHashMap 管理。
 */
public class WizardTrait extends LegendaryTrait {
    // 可调参数
    private static final float COMBO_TRIGGER_MANA_RATIO = 0.5f;
    private static final float COMBO_CHANCE = 0.3f;
    private static final int COMBO_MAX_HITS = 3;
    private static final float DELAY_CHANCE = 0.1f;
    private static final float LOW_MANA_DELAY_RATIO = 0.2f;
    private static final int COMBO_INTERVAL_TICKS = 5;
    private static final int DECISION_INTERVAL = 40;
    // 认知记忆（持久化）
    private static final float PERCEPTION_INITIAL = 1.0f;
    private static final float PERCEPTION_MAX = 2.0f;
    private static final float PERCEPTION_INCREASE = 0.2f;
    private static final float PERCEPTION_DECAY = 0.01f;
    private static final String PERCEPTION_TAG = "wizard_mana_perception";
    // 属性修饰符
    private static final ResourceLocation MANA_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_mana_bonus");
    private static final ResourceLocation REGEN_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_regen_bonus");
    private static final float MANA_BONUS_PER_LEVEL = 0.2f;
    private static final float REGEN_BONUS_PER_LEVEL = 0.1f;
    // 法术书生成
    private static final int MAX_SPELLBOOK_SLOTS = 20;
    private static final int MAX_RANDOM_ATTEMPTS = 100;
    // 连招搜索（下降搜索）
    private static final float COMBO_TERMINATOR_SHORT_CHANCE = 0.25f;
    private static final int COMBO_SEARCH_ATTEMPTS = 20;
    // 缓存与状态
    private static final Map<LivingEntity, ComboState> COMBO_STATES = new WeakHashMap<>();
    private static final Map<LivingEntity, CachedSpellData> SPELL_DATA = new WeakHashMap<>();
    // 内部数据结构
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
    // 精简法术缓存
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
        private static class SpellEntry {
            final SpellSource source;
            final int cost;
            SpellEntry(SpellSource source, int cost) {
                this.source = source;
                this.cost = cost;
            }
        }
    }

    private static class Fingerprint {
        final int hash;
        final int count;
        Fingerprint(int hash, int count) { this.hash = hash; this.count = count; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Fingerprint that)) return false;
            return hash == that.hash && count == that.count;
        }
        @Override
        public int hashCode() { return Objects.hash(hash, count); }
    }

    private static class CachedSpellData {
        final SpellCache cache;
        final Fingerprint fingerprint;
        CachedSpellData(SpellCache cache, Fingerprint fingerprint) {
            this.cache = cache;
            this.fingerprint = fingerprint;
        }
    }

    public WizardTrait(IntSupplier color) {
        super(color);
    }

    @Override
    public void postInit(LivingEntity entity, int traitLV) {
        applyAttributeBonuses(entity, traitLV);
        grantSpellbook(entity, traitLV);
        entity.setData(DataAttachmentRegistry.MAGIC_DATA, new MagicData(true));
        getOrRefreshCache(entity);
    }

    private void applyAttributeBonuses(LivingEntity entity, int traitLV) {
        if (traitLV <= 0) return;
        AttributeInstance manaAttr = entity.getAttribute(MAX_MANA);
        AttributeInstance regenAttr = entity.getAttribute(MANA_REGEN);
        if (manaAttr == null || regenAttr == null) return;
        manaAttr.removeModifier(MANA_MODIFIER_ID);
        regenAttr.removeModifier(REGEN_MODIFIER_ID);
        manaAttr.addPermanentModifier(new AttributeModifier(
                MANA_MODIFIER_ID,
                MANA_BONUS_PER_LEVEL * traitLV,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        ));
        regenAttr.addPermanentModifier(new AttributeModifier(
                REGEN_MODIFIER_ID,
                REGEN_BONUS_PER_LEVEL * traitLV,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        ));
    }

    private void grantSpellbook(LivingEntity entity, int traitLV) {
        if (traitLV == 0) {
            ItemStack offhand = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (offhand.is(ItemRegistry.WIMPY_SPELL_BOOK.get()) &&
                    offhand.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                            .getLevel(LHEnchantments.VANISH.holder()) != 0) {
                entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            return;
        }
        ItemStack book = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(LHEnchantments.VANISH.holder(), 1);
        EnchantmentHelper.setEnchantments(book, enchants.toImmutable());
        ISpellContainerMutable container = ISpellContainer.create(
                Math.min(traitLV * 2, MAX_SPELLBOOK_SLOTS), true, false
        ).mutableCopy();
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
                    if (!container.addSpell(existing.getSpell(), existing.getLevel(), false))
                        break;
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
    // 核心 Tick（仅流程控制）
    @Override
    public void tick(LivingEntity entity, int level) {
        MobMagicManager.tick(entity);
        if (processComboQueue(entity)) return;
        if (!isDecisionTime(entity)) return;
        if (!hasValidTarget(entity)) return;
        SpellCache cache = getOrRefreshCache(entity);
        if (cache == null) return;
        MagicData magicData = MagicData.getPlayerMagicData(entity);
        float manaRatio = calculateManaRatio(entity, magicData);
        float perception = getPerception(entity);
        if (shouldSkipDecision(entity, manaRatio, perception)) return;
        if (shouldAttemptCombo(manaRatio, entity.getRandom())) {
            if (tryExecuteCombo(entity, cache, magicData)) return;
            cache = getOrRefreshCache(entity);
            if (cache == null) return;
        }
        executeSingleSpell(entity, cache, magicData);
    }
    // 子方法：决策周期与目标检查
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
    // 子方法：连招队列处理
    private boolean processComboQueue(LivingEntity entity) {
        ComboState state = COMBO_STATES.get(entity);
        if (state == null) return false;
        long gameTime = entity.level().getGameTime();
        if (gameTime >= state.nextCastTime && state.index < state.spells.size()) {
            castNextComboSpell(entity, state);
        }
        return true;
    }
    private void castNextComboSpell(LivingEntity entity, ComboState state) {
        SpellSource source = state.spells.get(state.index);
        AbstractSpell spell = source.spellData().getSpell();
        int level = source.spellData().getLevel();
        MagicData magicData = MagicData.getPlayerMagicData(entity);
        int cost = spell.getManaCost(level);
        if (magicData.getMana() < cost) {
            state.manaShortage = true;
        } else if (!magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            ItemStack stack = entity.getItemBySlot(source.slot());
            attemptInitiateEntityCast(stack, entity, spell, level,
                    source.castSource(), true, getSlotName(source.slot()));
        }
        state.index++;
        if (state.index < state.spells.size()) {
            state.nextCastTime = entity.level().getGameTime() + COMBO_INTERVAL_TICKS;
        } else {
            float perception = getPerception(entity);
            updatePerception(entity, perception, state.manaShortage, 0);
            COMBO_STATES.remove(entity);
        }
    }
    // 子方法：缓存管理
    private SpellCache getOrRefreshCache(LivingEntity entity) {
        Fingerprint current = computeFingerprint(entity);
        CachedSpellData data = SPELL_DATA.get(entity);
        if (data == null || !data.fingerprint.equals(current)) {
            List<SpellSource> allSpells = SpellCastHelper.getEntitySpells(entity);
            SpellCache newCache = allSpells.isEmpty() ? null : new SpellCache(allSpells);
            SPELL_DATA.put(entity, new CachedSpellData(newCache, current));
            return newCache;
        }
        return data.cache;
    }
    private Fingerprint computeFingerprint(LivingEntity entity) {
        int hash = 0;
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && ISpellContainer.isSpellContainer(stack)) {
                ISpellContainer container = ISpellContainer.get(stack);
                for (var spellSlot : container.getActiveSpells()) {
                    SpellData data = spellSlot.spellData();
                    hash = 31 * hash + data.getSpell().getSpellId().hashCode();
                    hash = 31 * hash + data.getLevel();
                    count++;
                }
            }
        }
        return new Fingerprint(hash, count);
    }
    // 子方法：逆验证（抵抗哈希碰撞与非法修改造成的映射错误）
    private boolean validateSpell(LivingEntity entity, SpellSource source) {
        ItemStack stack = entity.getItemBySlot(source.slot());
        if (!ISpellContainer.isSpellContainer(stack)) return false;
        ISpellContainer container = ISpellContainer.get(stack);
        AbstractSpell spell = source.spellData().getSpell();
        int level = source.spellData().getLevel();
        for (var slot : container.getActiveSpells()) {
            SpellData data = slot.spellData();
            if (data.getSpell() == spell && data.getLevel() == level)
                return true;
        }
        return false;
    }
    private boolean validateSpells(LivingEntity entity, List<SpellSource> sources) {
        for (SpellSource src : sources) {
            if (!validateSpell(entity, src)) {
                SPELL_DATA.remove(entity);
                return false;
            }
        }
        return true;
    }
    // 子方法：认知管理（统一入口）
    private float getPerception(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        float value = tag.getFloat(PERCEPTION_TAG);
        return value == 0 ? PERCEPTION_INITIAL : value;
    }
    private void updatePerception(LivingEntity entity, float perception, boolean manaShortage, int searchAttempts) {
        float range = PERCEPTION_MAX - PERCEPTION_INITIAL;
        float offset = perception - PERCEPTION_INITIAL;
        // 基础增量：正（法力不足）或负（成功）
        float baseIncrement = manaShortage ? PERCEPTION_INCREASE : -PERCEPTION_DECAY;
        // 若 searchAttempts > 0，则根据搜索代价放大基数
        float costFactor = searchAttempts > 0 ? (1.0f + (searchAttempts / (float) COMBO_SEARCH_ATTEMPTS)) : 1.0f;
        // 非线性缩放：法力不足时缩放 (1 - offset/range)，成功时缩放 (offset/range)
        float scale = manaShortage ? (1.0f - offset / range) : (offset / range);
        float increment = baseIncrement * scale * costFactor;
        float newPerception = Math.min(PERCEPTION_MAX, Math.max(PERCEPTION_INITIAL, perception + increment));
        entity.getPersistentData().putFloat(PERCEPTION_TAG, newPerception);
    }
    private boolean shouldSkipDecision(LivingEntity entity, float manaRatio, float perception) {
        float threshold = LOW_MANA_DELAY_RATIO * perception;
        return manaRatio < threshold || entity.getRandom().nextFloat() < DELAY_CHANCE;
    }
    // 子方法：决策执行
    private boolean shouldAttemptCombo(float manaRatio, RandomSource random) {
        return manaRatio > COMBO_TRIGGER_MANA_RATIO && random.nextFloat() < COMBO_CHANCE;
    }
    private boolean tryExecuteCombo(LivingEntity entity, SpellCache cache, MagicData magicData) {
        if (!(entity instanceof Targeting targeting) || targeting.getTarget() == null) return false;
        if (cache.instantSortedByCost.isEmpty()) return false;
        float currentMana = magicData.getMana();
        SearchResult result = findOptimalCombo(cache, currentMana, COMBO_MAX_HITS, entity.getRandom());
        if (result.combo == null || result.combo.isEmpty()) {
            // 搜索失败：视为法力不足或组合困难，应用搜索代价放大增量
            float perception = getPerception(entity);
            updatePerception(entity, perception, true, result.attempts);
            return false;
        }
        // 搜索成功，但尝试次数较多时也轻微提升感知（表示寻找过程不易）
        if (result.attempts > COMBO_SEARCH_ATTEMPTS / 2) {
            float perception = getPerception(entity);
            // 此处使用 manaShortage=false，但 searchAttempts 传递实际值，使衰减略微放大（负增量幅度增大）
            updatePerception(entity, perception, false, result.attempts);
        }
        List<SpellSource> combo = result.combo;
        if (!validateSpells(entity, combo)) return false;
        ComboState state = new ComboState(combo, entity.level().getGameTime());
        COMBO_STATES.put(entity, state);
        SpellSource first = combo.get(0);
        AbstractSpell spell = first.spellData().getSpell();
        int level = first.spellData().getLevel();
        if (!magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            ItemStack stack = entity.getItemBySlot(first.slot());
            attemptInitiateEntityCast(stack, entity, spell, level,
                    first.castSource(), true, getSlotName(first.slot()));
        }
        state.index = 1;
        if (state.index < state.spells.size()) {
            state.nextCastTime = entity.level().getGameTime() + COMBO_INTERVAL_TICKS;
        } else {
            float perception = getPerception(entity);
            // 只有一段且成功，感知衰减（无搜索代价）
            updatePerception(entity, perception, false, 0);
            COMBO_STATES.remove(entity);
        }
        return true;
    }
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
            float perception = getPerception(entity);
            updatePerception(entity, perception, true, 0);
            return;
        }
        ItemStack stack = entity.getItemBySlot(selection.slot());
        boolean success = attemptInitiateEntityCast(stack, entity, spell, spellData.getLevel(),
                selection.castSource(), true, getSlotName(selection.slot()));
        if (success) {
            float perception = getPerception(entity);
            updatePerception(entity, perception, false, 0);
        }
    }
    // 子方法：组合搜索（下降搜索）返回 SearchResult
    private static class SearchResult {
        final List<SpellSource> combo;
        final int attempts;
        SearchResult(List<SpellSource> combo, int attempts) {
            this.combo = combo;
            this.attempts = attempts;
        }
    }
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
            List<SpellCache.SpellEntry> filteredTerm = termCandidates.stream()
                    .filter(e -> e != first)
                    .collect(Collectors.toList());
            if (filteredTerm.isEmpty()) continue;
            float termUpper = currentMana - first.cost;
            SpellCache.SpellEntry terminator = pickBelow.apply(filteredTerm, termUpper);
            if (terminator == null) continue;
            if (maxHits >= 3) {
                float remain = currentMana - first.cost - terminator.cost;
                if (remain >= instants.get(0).cost) {
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
    // 辅助
    private static String getSlotName(EquipmentSlot slot) {
        if (slot == MAINHAND) return "mainhand";
        if (slot == EquipmentSlot.OFFHAND) return "offhand";
        return "other";
    }
    @SuppressWarnings("unused")
    public static void clearCache(LivingEntity entity) {
        COMBO_STATES.remove(entity);
        SPELL_DATA.remove(entity);
    }
}