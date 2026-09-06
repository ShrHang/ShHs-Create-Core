package io.github.shrhang.shhs_create_core.content.hostility.traits;

import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellQueue;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellTactics;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSummonManager;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper;
import dev.xkmc.l2hostility.content.logic.TraitManager;
import dev.xkmc.l2hostility.content.traits.legendary.LegendaryTrait;
import dev.xkmc.l2hostility.init.registrate.LHEnchantments;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.loot.SpellFilter;
import io.redspace.ironsspellbooks.registries.DataAttachmentRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

import static io.github.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;

/**
 * 巫师词条，赋予生物施法能力。
 * 负责初始化法术书、增加法力/恢复属性，以及每 tick 推进施法状态机和法术决策。
 */
public class WizardTrait extends LegendaryTrait {
    private static final int MAX_SPELLBOOK_SLOTS = 20;
    private static final int MAX_RANDOM_ATTEMPTS = 30;

    public WizardTrait(IntSupplier color) {
        super(color);
    }

    @Override
    public void initialize(@NotNull LivingEntity entity, int traitLV) {
        TraitManager.addAttribute(entity, MAX_MANA, "wizard_max_mana", SERVER.wizardMaxManaPerLev.get() * traitLV, AttributeModifier.Operation.ADD_VALUE);
        TraitManager.addAttribute(entity, MANA_REGEN, "wizard_mana_regen", SERVER.wizardManaRegenPerLev.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @Override
    public void postInit(@NotNull LivingEntity entity, int traitLV) {
        grantSpellbook(entity, traitLV);
        MagicData magicData = new MagicData(false);
        // 注册映射：MagicData -> 施法者
        MobSummonManager.registerCaster(magicData, entity);
        // 强制初始化 PlayerRecasts 并注册映射
        MobSummonManager.registerRecasts(magicData.getPlayerRecasts(), magicData);
        entity.setData(DataAttachmentRegistry.MAGIC_DATA, magicData);
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

        ItemStack spellbook = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());

        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(LHEnchantments.VANISH.holder(), 1);
        EnchantmentHelper.setEnchantments(spellbook, enchants.toImmutable());

        int spellbookSize = Math.min(traitLV * 2, MAX_SPELLBOOK_SLOTS);
        ISpellContainerMutable container = ISpellContainer.create(spellbookSize, true, false).mutableCopy();
        SpellFilter filter = new SpellFilter();
        RandomSource random = entity.getRandom();
        for (int i = 0; i < spellbookSize; i++) {
            int attempts = 0;
            while (attempts++ < MAX_RANDOM_ATTEMPTS) {
                AbstractSpell spell = filter.getRandomSpell(random, s -> SpellCastHelper.isSpellAllowed(entity, s));
                int level = random.nextIntBetweenInclusive(1, Math.min(traitLV, spell.getMaxLevel()));
                if (container.addSpell(spell, level, false)) break;
            }
        }
        ISpellContainer.set(spellbook, container.toImmutable());
        // TODO 可能以后会换一个位置
        entity.setItemInHand(InteractionHand.OFF_HAND, spellbook);
    }

    @Override
    public boolean allow(@NotNull LivingEntity entity, int difficulty, int maxModLv) {
        return super.allow(entity, difficulty, maxModLv) &&
                entity instanceof Targeting &&
                !(entity instanceof AbstractSpellCastingMob);
    }

    /**
     * 尝试清除实体的法术队列（仅在实体死亡时执行清理，避免内存泄漏）。
     */
    public static void tryClearCache(LivingEntity entity) {
        if (!entity.isAlive()) {
            MobSpellQueue.clearQueue(entity);
        }
    }

    @Override
    public void tick(@NotNull LivingEntity entity, int level) {
        tryClearCache(entity);
        MobMagicManager.tick(entity);
        MobSpellQueue.tickQueue(entity);
        MobSpellTactics.tick(entity);
    }

    @SuppressWarnings("unused")
    public static void clearCache(LivingEntity entity) {
        MobSpellQueue.clearQueue(entity);
    }
}