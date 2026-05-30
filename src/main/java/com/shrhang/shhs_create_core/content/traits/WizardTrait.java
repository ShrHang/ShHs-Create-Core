package com.shrhang.shhs_create_core.content.traits;

import com.shrhang.shhs_create_core.content.magic.MobMagicManager;
import com.shrhang.shhs_create_core.content.util.SpellCastHelper;
import dev.xkmc.l2hostility.content.traits.legendary.LegendaryTrait;
import dev.xkmc.l2hostility.init.registrate.LHEnchantments;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.loot.SpellFilter;
import io.redspace.ironsspellbooks.registries.DataAttachmentRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntSupplier;

import static com.shrhang.shhs_create_core.content.data.ShHsTagKey.ENTITY_SPELL_BLACKLIST;
import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.*;
import static io.redspace.ironsspellbooks.api.magic.SpellSelectionManager.OFFHAND;

public class WizardTrait extends LegendaryTrait {
    public WizardTrait(IntSupplier color) {
        super(color);
    }

    @Override
    public void postInit(LivingEntity entity, int traitLV) {
        if (traitLV == 0) {
            ItemStack offhandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (offhandItem.is(ItemRegistry.WIMPY_SPELL_BOOK.get()) && offhandItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(LHEnchantments.VANISH.holder()) != 0) {
                entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        } else {
            ItemStack itemstack = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());

            // 添加毁灭附魔
            ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(LHEnchantments.VANISH.holder(), 1);
            EnchantmentHelper.setEnchantments(itemstack, enchantments.toImmutable());

            // 生成法术书内容
            ISpellContainerMutable spellContainer = ISpellContainer.create(Math.min(traitLV * 2, 20), true, false).mutableCopy();
            SpellFilter spellFilter = new SpellFilter();
            RandomSource random = entity.getRandom();
            for (int i = 0; i < traitLV * 2; i++) {
                AbstractSpell spell;
                do {
                    spell = spellFilter.getRandomSpell(random, _spell ->
                        _spell.getCastType() == CastType.INSTANT && _spell.getRecastCount(_spell.getMaxLevel(), entity) <= 0
                        && !ENTITY_SPELL_BLACKLIST.contains(_spell)
                    );
                } while (!spellContainer.addSpell(spell, random.nextIntBetweenInclusive(1, Math.min(traitLV, spell.getMaxLevel())), false));
            }
            ISpellContainer.set(itemstack, spellContainer.toImmutable());
            // 装备到副手
            entity.setItemInHand(InteractionHand.OFF_HAND, itemstack);
        }
        // 绑定魔法数据
        entity.setData(DataAttachmentRegistry.MAGIC_DATA, new MagicData(true));
    }

    @Override
    public void tick(@NotNull LivingEntity entity, int level) {
        MobMagicManager.tick(entity);
        if (entity.level().getGameTime() % 40 == 0 && entity instanceof Targeting targetingMob) {
            LivingEntity target = targetingMob.getTarget();
            if (target != null) { // TODO 这里可以加一些条件，比如距离、视线等
                List<SpellSource> selections = getEntitySpells(entity);
                if (!selections.isEmpty()) {
                    var selection = selections.get(entity.getRandom().nextInt(selections.size())); // 随机选择一个法术
                    SpellData spellData = selection.spellData();
                    AbstractSpell spell = spellData.getSpell();
                    if (spell.getCastType() != CastType.INSTANT || spell.getRecastCount(spellData.getLevel(), entity) > 0) return;
                    if (ENTITY_SPELL_BLACKLIST.contains(spell)) return;
                    if (SpellCastHelper.attemptInitiateEntityCast(entity.getOffhandItem(), entity, spellData.getSpell(), spellData.getLevel(), selection.castSource(), true, OFFHAND)) {
                        forceLookAtTarget((LivingEntity) targetingMob, target);
                    }
//                    if (entity.getServer() != null) {
//                        MagicData magicData = MagicData.getPlayerMagicData(entity);
//                        var cooldownMap = magicData.getPlayerCooldowns().getSpellCooldowns();
//                        String cooldownStr = cooldownMap.containsKey(spell.getSpellId())
//                                ? String.valueOf(cooldownMap.get(spell.getSpellId()).getSpellCooldown())
//                                : "0";
//                        entity.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Entity: " + entity.getName().getString() + " | Mana: " + magicData.getMana() + " | Spell: " + spell.getSpellId() + " | Cooldown: " + cooldownStr), false);
//                    }
                }
            }
        }
    }
}
