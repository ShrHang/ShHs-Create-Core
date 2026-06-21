package com.shrhang.shhs_create_core.content.traits;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.magic.MobMagicManager;
import com.shrhang.shhs_create_core.content.util.SpellCastHelper;
import com.shrhang.shhs_create_core.content.util.SpellDecisionHelper;
import com.shrhang.shhs_create_core.content.util.SpellQueueHelper;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

/**
 * 巫师词条，赋予生物施法能力。
 * 负责初始化法术书、增加法力/恢复属性，以及每 tick 推进施法状态机和法术决策。
 */
public class WizardTrait extends LegendaryTrait {
    // 法术书配置
    private static final int MAX_SPELLBOOK_SLOTS = 20;
    private static final int MAX_RANDOM_ATTEMPTS = 100;
    // 属性修饰符
    private static final ResourceLocation MANA_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_mana_bonus");
    private static final ResourceLocation REGEN_MODIFIER_ID = ResourceLocation.parse(ShHsCreateCore.MODID + ":wizard_regen_bonus");
    private static final float MANA_BONUS_PER_LEVEL = 0.2f;
    private static final float REGEN_BONUS_PER_LEVEL = 0.1f;

    public WizardTrait(IntSupplier color) {
        super(color);
    }
    /**
     * 词条后初始化：应用属性加成、生成法术书、初始化魔力数据。
     */
    @Override
    public void postInit(@NotNull LivingEntity entity, int traitLV) {
        applyAttributeBonuses(entity, traitLV);
        grantSpellbook(entity, traitLV);
        entity.setData(DataAttachmentRegistry.MAGIC_DATA, new MagicData(true));
    }
    /**
     * 根据词条等级增加最大法力和法力恢复速度。
     */
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
                AbstractSpell candidate = filter.getRandomSpell(random, s -> SpellCastHelper.isSpellAllowed(entity, s));
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
     * 每 tick 调用：推进施法状态机（MobMagicManager）、法术队列（SpellQueueHelper），
     * 然后委托法术决策（SpellDecisionHelper）。
     */
    @Override
    public void tick(@NotNull LivingEntity entity, int level) {
        MobMagicManager.tick(entity);
        SpellQueueHelper.tickQueue(entity);
        SpellDecisionHelper.tick(entity);
    }
    /**
     * 清除实体的法术队列（用于卸载或重置）。
     */
    @SuppressWarnings("unused")
    public static void clearCache(LivingEntity entity) {
        SpellQueueHelper.clearQueue(entity);
    }
}