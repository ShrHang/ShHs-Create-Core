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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.*;
import static io.redspace.ironsspellbooks.api.magic.SpellSelectionManager.OFFHAND;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN;
import static net.minecraft.world.entity.EquipmentSlot.MAINHAND;

public class WizardTrait extends LegendaryTrait {

    // ========== 可调参数 ==========
    private static final float COMBO_TRIGGER_MANA_RATIO = 0.5f;
    private static final float COMBO_CHANCE = 0.3f;
    private static final int COMBO_MAX_HITS = 3;
    private static final float DELAY_CHANCE = 0.1f;
    private static final float LOW_MANA_DELAY_RATIO = 0.2f;

    // ========== 记忆机制参数（非线性） ==========
    private static final float PERCEPTION_INITIAL = 1.0f;
    private static final float PERCEPTION_MAX = 2.0f;
    private static final float PERCEPTION_INCREASE = 0.2f;
    private static final float PERCEPTION_DECAY = 0.01f;
    private static final String PERCEPTION_TAG = "wizard_mana_perception";

    // ========== 连招延迟 ==========
    private static final int COMBO_INTERVAL_TICKS = 5;

    // ========== 属性修饰符 ==========
    private static final ResourceLocation MANA_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_mana_bonus");
    private static final ResourceLocation REGEN_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_regen_bonus");
    private static final float MANA_BONUS_PER_LEVEL = 0.2f;
    private static final float REGEN_BONUS_PER_LEVEL = 0.1f;

    // ========== 连招状态跟踪 ==========
    private static final Map<LivingEntity, ComboState> COMBO_STATES = new WeakHashMap<>();

    private static class ComboState {
        final List<SpellSource> spells;
        int index;
        long nextCastTime;

        ComboState(List<SpellSource> spells, long firstCastTime) {
            this.spells = spells;
            this.index = 0;
            this.nextCastTime = firstCastTime;
        }
    }

    public WizardTrait(IntSupplier color) {
        super(color);
    }

    @Override
    public void postInit(LivingEntity entity, int traitLV) {
        // ---- 属性加成 ----
        if (traitLV > 0) {
            AttributeInstance manaAttr = entity.getAttribute(MAX_MANA);
            AttributeInstance regenAttr = entity.getAttribute(MANA_REGEN);
            if (manaAttr != null && regenAttr != null) {
                manaAttr.removeModifier(MANA_MODIFIER_ID);
                regenAttr.removeModifier(REGEN_MODIFIER_ID);

                AttributeModifier manaMod = new AttributeModifier(
                        MANA_MODIFIER_ID,
                        MANA_BONUS_PER_LEVEL * traitLV,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );
                AttributeModifier regenMod = new AttributeModifier(
                        REGEN_MODIFIER_ID,
                        REGEN_BONUS_PER_LEVEL * traitLV,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );
                manaAttr.addPermanentModifier(manaMod);
                regenAttr.addPermanentModifier(regenMod);
            }
        }

        // ---- 法术书赋予 ----
        if (traitLV == 0) {
            ItemStack offhandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (offhandItem.is(ItemRegistry.WIMPY_SPELL_BOOK.get()) && offhandItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(LHEnchantments.VANISH.holder()) != 0) {
                entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        } else {
            ItemStack itemstack = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
            ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(LHEnchantments.VANISH.holder(), 1);
            EnchantmentHelper.setEnchantments(itemstack, enchantments.toImmutable());

            ISpellContainerMutable spellContainer = ISpellContainer.create(Math.min(traitLV * 2, 20), true, false).mutableCopy();
            SpellFilter spellFilter = new SpellFilter();
            RandomSource random = entity.getRandom();
            for (int i = 0; i < traitLV * 2; i++) {
                AbstractSpell spell;
                do {
                    spell = spellFilter.getRandomSpell(random, _spell -> isSpellAllowed(entity, _spell));
                } while (!spellContainer.addSpell(spell, random.nextIntBetweenInclusive(1, Math.min(traitLV, spell.getMaxLevel())), false));
            }
            ISpellContainer.set(itemstack, spellContainer.toImmutable());
            entity.setItemInHand(InteractionHand.OFF_HAND, itemstack);
        }

        entity.setData(DataAttachmentRegistry.MAGIC_DATA, new MagicData(true));
    }

    @Override
    public boolean allow(LivingEntity entity, int difficulty, int maxModLv) {
        return super.allow(entity, difficulty, maxModLv) && entity instanceof Targeting && !(entity instanceof AbstractSpellCastingMob);
    }

    @Override
    public void tick(LivingEntity entity, int level) {
        MobMagicManager.tick(entity);

        // --- 处理连招队列 ---
        ComboState state = COMBO_STATES.get(entity);
        if (state != null) {
            long gameTime = entity.level().getGameTime();
            if (gameTime >= state.nextCastTime && state.index < state.spells.size()) {
                SpellSource source = state.spells.get(state.index);
                AbstractSpell spell = source.spellData().getSpell();
                int spellLevel = source.spellData().getLevel();
                MagicData magicData = MagicData.getPlayerMagicData(entity);

                if (!magicData.getPlayerCooldowns().isOnCooldown(spell) && magicData.getMana() >= spell.getManaCost(spellLevel)) {
                    ItemStack stack = entity.getItemBySlot(source.slot());
                    SpellCastHelper.attemptInitiateEntityCast(
                            stack,
                            entity,
                            spell,
                            spellLevel,
                            source.castSource(),
                            true,
                            getSlotName(source.slot())
                    );
                }

                state.index++;
                if (state.index < state.spells.size()) {
                    state.nextCastTime = gameTime + COMBO_INTERVAL_TICKS;
                } else {
                    COMBO_STATES.remove(entity);
                }
            }
            return; // 连招进行中，不再做新决策
        }

        // 每40刻决策一次
        if (entity.level().getGameTime() % 40 != 0) return;

        if (!(entity instanceof Targeting targeting)) return;
        LivingEntity target = targeting.getTarget();
        if (target == null) return;

        MagicData magicData = MagicData.getPlayerMagicData(entity);
        float maxMana = (float) entity.getAttributeValue(MAX_MANA);
        float currentMana = magicData.getMana();
        float manaRatio = maxMana > 0 ? currentMana / maxMana : 0;

        CompoundTag persistent = entity.getPersistentData();
        float perception = persistent.getFloat(PERCEPTION_TAG);
        if (perception == 0) perception = PERCEPTION_INITIAL;

        float effectiveDelayThreshold = LOW_MANA_DELAY_RATIO * perception;
        if (manaRatio < effectiveDelayThreshold || entity.getRandom().nextFloat() < DELAY_CHANCE) {
            return;
        }

        List<SpellSource> allSpells = SpellCastHelper.getEntitySpells(entity);
        if (allSpells.isEmpty()) return;

        // ---------- 尝试生成连招 ----------
        if (manaRatio > COMBO_TRIGGER_MANA_RATIO && entity.getRandom().nextFloat() < COMBO_CHANCE) {
            List<SpellSource> instantSources = allSpells.stream()
                    .filter(s -> {
                        AbstractSpell spell = s.spellData().getSpell();
                        return spell.getCastType() == CastType.INSTANT
                                && !magicData.getPlayerCooldowns().isOnCooldown(spell)
                                && isSpellAllowed(entity, spell);
                    })
                    .collect(Collectors.toList());

            if (!instantSources.isEmpty()) {
                int n = instantSources.size();
                int maxK = Math.min(COMBO_MAX_HITS, n);
                List<SpellSource> bestCombo = null;
                int bestTotalCost = -1;

                for (int k = maxK; k >= 1; k--) {
                    float avgCost = currentMana / k;
                    for (SpellSource firstCandidate : instantSources) {
                        int firstCost = firstCandidate.spellData().getSpell().getManaCost(firstCandidate.spellData().getLevel());
                        if (firstCost >= avgCost) continue;

                        List<SpellSource> remaining = instantSources.stream()
                                .filter(s -> s != firstCandidate)
                                .collect(Collectors.toList());
                        List<List<SpellSource>> combos = getCombinations(remaining, k - 1);
                        for (List<SpellSource> spellCombo : combos) {
                            int totalCost = firstCost;
                            for (SpellSource src : spellCombo) {
                                totalCost += src.spellData().getSpell().getManaCost(src.spellData().getLevel());
                            }
                            if (totalCost <= currentMana && totalCost > bestTotalCost) {
                                List<SpellSource> fullCombo = new ArrayList<>();
                                fullCombo.add(firstCandidate);
                                fullCombo.addAll(spellCombo);
                                bestCombo = fullCombo;
                                bestTotalCost = totalCost;
                            }
                        }
                    }
                    if (bestCombo != null) break;
                }

                if (bestCombo != null) {
                    // 初始化连招状态，立即执行第一段
                    ComboState newState = new ComboState(bestCombo, entity.level().getGameTime());
                    COMBO_STATES.put(entity, newState);

                    // 执行第一段
                    SpellSource first = bestCombo.get(0);
                    AbstractSpell firstSpell = first.spellData().getSpell();
                    int firstLevel = first.spellData().getLevel();
                    if (!magicData.getPlayerCooldowns().isOnCooldown(firstSpell) && magicData.getMana() >= firstSpell.getManaCost(firstLevel)) {
                        ItemStack stack = entity.getItemBySlot(first.slot());
                        SpellCastHelper.attemptInitiateEntityCast(
                                stack,
                                entity,
                                firstSpell,
                                firstLevel,
                                first.castSource(),
                                true,
                                getSlotName(first.slot())
                        );
                    }
                    newState.index = 1;
                    if (newState.index < newState.spells.size()) {
                        newState.nextCastTime = entity.level().getGameTime() + COMBO_INTERVAL_TICKS;
                    } else {
                        COMBO_STATES.remove(entity);
                    }
                    return;
                }
            }
        }

        // ---------- 普通单发 ----------
        var selection = allSpells.get(entity.getRandom().nextInt(allSpells.size()));
        SpellData spellData = selection.spellData();
        AbstractSpell spell = spellData.getSpell();
        if (!isSpellAllowed(entity, spell)) return;

        ItemStack stack = entity.getItemBySlot(selection.slot());
        boolean success = SpellCastHelper.attemptInitiateEntityCast(
                stack,
                entity,
                spell,
                spellData.getLevel(),
                selection.castSource(),
                true,
                getSlotName(selection.slot())
        );

        if (success) {
            float range = PERCEPTION_MAX - PERCEPTION_INITIAL;
            float offset = perception - PERCEPTION_INITIAL;
            float decrement = PERCEPTION_DECAY * (offset / range);
            float newPerception = Math.max(PERCEPTION_INITIAL, perception - decrement);
            persistent.putFloat(PERCEPTION_TAG, newPerception);
        }
    }

    // ---------- 辅助方法 ----------
    private static String getSlotName(EquipmentSlot slot) {
        if (slot == MAINHAND) return "mainhand";
        if (slot == EquipmentSlot.OFFHAND) return "offhand";
        return "other";
    }

    private static <T> List<List<T>> getCombinations(List<T> list, int k) {
        List<List<T>> result = new ArrayList<>();
        if (k == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        if (list.size() < k) return result;
        for (int i = 0; i <= list.size() - k; i++) {
            T first = list.get(i);
            List<T> rest = list.subList(i + 1, list.size());
            for (List<T> subCombo : getCombinations(rest, k - 1)) {
                List<T> combo = new ArrayList<>();
                combo.add(first);
                combo.addAll(subCombo);
                result.add(combo);
            }
        }
        return result;
    }
}