package com.shrhang.shhs_create_core.content.traits;

import dev.xkmc.l2hostility.content.traits.legendary.LegendaryTrait;
import dev.xkmc.l2hostility.init.data.LHConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.loot.SpellFilter;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.function.IntSupplier;

public class WizardTrait extends LegendaryTrait {
    public WizardTrait(IntSupplier color) {
        super(color);
    }

    @Override
    public void initialize(@NotNull LivingEntity entity, int traitLV) {
        if (traitLV == 0) {
            CuriosApi.getCuriosInventory(entity).ifPresent(inv -> {
                var curios = inv.getCurios();
                if (curios.containsKey("spellbook")) {
                    curios.get("spellbook").getStacks().setStackInSlot(0, ItemStack.EMPTY);
                }
            });
        } else  {
            ItemStack itemstack = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
            var spellContainer = ISpellContainer.create(Math.min(traitLV * 2, 20), true, false).mutableCopy();
            for (int i = 0; i < traitLV * 2; i++) {
                AbstractSpell spell;
                do {
                    spell = new SpellFilter().getRandomSpell(RandomSource.create());
                } while (!spellContainer.addSpell(spell, RandomSource.create().nextIntBetweenInclusive(1, Math.min(traitLV, spell.getMaxLevel())), false));
            }
            ISpellContainer.set(itemstack, spellContainer.toImmutable());
            CuriosApi.getCuriosInventory(entity).ifPresent(inv -> {
                var curios = inv.getCurios();
                if (curios.containsKey("spellbook")) {
                    curios.get("spellbook").getStacks().setStackInSlot(0, itemstack);

                }
            });
        }
    }

    @Override
    public void addDetail(RegistryAccess access, List<Component> list) {
        list.add(Component.translatable(getDescriptionId() + ".desc",
                        Component.literal(2 + "").withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
    }
}
