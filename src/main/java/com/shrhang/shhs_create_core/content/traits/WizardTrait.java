package com.shrhang.shhs_create_core.content.traits;

import com.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import com.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellQueue;
import com.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellTactics;
import com.shrhang.shhs_create_core.content.util.SpellCastHelper;
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

import static com.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;

/**
 * 巫师词条，赋予生物施法能力。
 * 负责初始化法术书、增加法力/恢复属性，以及每 tick 推进施法状态机和法术决策。
 */
public class WizardTrait extends LegendaryTrait {
    // 法术书配置
    private static final int MAX_SPELLBOOK_SLOTS = 20;
    private static final int MAX_RANDOM_ATTEMPTS = 30;

    public WizardTrait(IntSupplier color) {
        super(color);
    }
    /**
     * 根据词条等级增加最大法力和法力恢复属性。
     * <p>{@link dev.xkmc.l2hostility.content.traits.base.AttributeTrait#initialize(LivingEntity, int)}</p>
     */
    @Override
    public void initialize(@NotNull LivingEntity entity, int traitLV) {
        TraitManager.addAttribute(entity, MAX_MANA, "wizard_max_mana", SERVER.wizardMaxManaPerLev.get() * traitLV, AttributeModifier.Operation.ADD_VALUE);
        TraitManager.addAttribute(entity, MANA_REGEN, "wizard_mana_regen", SERVER.wizardManaRegenPerLev.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }
    /**
     * 词条后初始化：应用属性加成、生成法术书、初始化魔力数据。
     */
    @Override
    public void postInit(@NotNull LivingEntity entity, int traitLV) {
        grantSpellbook(entity, traitLV);
        // 设置为false以使怪物支持多段法术
        entity.setData(DataAttachmentRegistry.MAGIC_DATA, new MagicData(false));
    }
    /**
     * 生成法术书并填充随机法术，等级与词条等级相关。
     * 若等级为0，则清除带有消失附魔的法术书。
     */
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
        // 添加消失附魔
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(LHEnchantments.VANISH.holder(), 1);
        EnchantmentHelper.setEnchantments(spellbook, enchants.toImmutable());
        // 生成法术书内容
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
        // 法术书设置到实体的副手 // TODO 可能以后会换一个位置
        entity.setItemInHand(InteractionHand.OFF_HAND, spellbook);
    }
    /**
     * 允许该词条的条件：实体是 Targeting 且不是 AbstractSpellCastingMob。
     */
    @Override
    public boolean allow(@NotNull LivingEntity entity, int difficulty, int maxModLv) {
        return super.allow(entity, difficulty, maxModLv) &&
                entity instanceof Targeting &&
                !(entity instanceof AbstractSpellCastingMob);
    }
    /**
     * 尝试清除实体的法术队列（在实体死亡时执行）。
     */
    public static void tryClearCache(LivingEntity entity) {
        if (!entity.isAlive()) {
            clearCache(entity);
        }
    }
    /**
     * 每 tick 调用：按顺序执行清理、状态机更新、队列推进和法术决策。
     */
    @Override
    public void tick(@NotNull LivingEntity entity, int level) {
        tryClearCache(entity);
        MobMagicManager.tick(entity);
        MobSpellQueue.tickQueue(entity);
        MobSpellTactics.tick(entity);
    }
    /**
     * 如需手动清除缓存（例如卸载时），可调用此方法。
     * 由 tick 自动处理，外部调用需自行判断时机。
     */
    public static void clearCache(LivingEntity entity) {
        MobSpellQueue.clearQueue(entity);
    }
}