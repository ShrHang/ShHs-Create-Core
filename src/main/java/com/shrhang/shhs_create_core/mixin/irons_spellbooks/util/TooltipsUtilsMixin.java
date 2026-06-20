package com.shrhang.shhs_create_core.mixin.irons_spellbooks.util;

import com.shrhang.shhs_create_core.ShHsConfig;
import com.shrhang.shhs_create_core.content.util.SpellToleranceHelper;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static com.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;
import static com.shrhang.shhs_create_core.content.util.SpellToleranceHelper.getSpellTolerance;

@Mixin(TooltipsUtils.class)
public abstract class TooltipsUtilsMixin {
    @Inject( method = "formatActiveSpellTooltip", at = @At("RETURN"))
    private static void shhsc_c$formatActiveSpellTooltip (ItemStack stack, SpellData spellData, CastSource castSource, LocalPlayer player, CallbackInfoReturnable<List<MutableComponent>> cir) {
        if (!ShHsConfig.CLIENT.isToleranceTooltip.get()) return;
        List<MutableComponent> lines = cir.getReturnValue();
        if (lines != null && !lines.isEmpty() && player != null) {
            var spell = spellData.getSpell();
            if (spell != null) {
                double requiredTolerance = SpellToleranceHelper.getRequiredTolerance(spell.getLevelFor(spellData.getLevel(), player), spell, castSource);
                double spellTolerance = getSpellTolerance(player);
                lines.add(textComponent("no_enough_spell_tolerance", requiredTolerance)
                        .withStyle(spellTolerance < requiredTolerance ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }
    }

    @Inject(method = "formatScrollTooltip", at = @At("RETURN"))
    private static void shhsc_c$formatScrollTooltip(ItemStack stack, Player player, CallbackInfoReturnable<List<Component>> cir) {
        if (!ShHsConfig.CLIENT.isToleranceTooltip.get()) return;
        List<Component> lines = cir.getReturnValue();
        if (lines != null && !lines.isEmpty() && player != null) {
            var spellList = ISpellContainer.get(stack);
            var spellData = spellList != null ? spellList.getSpellAtIndex(0) : null;
            AbstractSpell spell = spellData != null ? spellData.getSpell() : null;
            if (spell != null) {
                double requiredTolerance = SpellToleranceHelper.getRequiredTolerance(spell.getLevelFor(spellData.getLevel(), player), spell, CastSource.SCROLL);
                double spellTolerance = getSpellTolerance(player);
                lines.add(textComponent("no_enough_spell_tolerance", requiredTolerance)
                        .withStyle(spellTolerance < requiredTolerance ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }
    }
}
