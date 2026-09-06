package io.github.shrhang.shhs_create_core.mixin.irons_spellbooks.gui.overlays;

import io.github.shrhang.shhs_create_core.ShHsConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;
import static io.github.shrhang.shhs_create_core.content.util.magic.SpellToleranceHelper.getRequiredTolerance;

@Mixin(InscriptionTableScreen.class)
public class InscriptionTableScreenMixin {
    @Redirect(
            method = "renderLorePage",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getUniqueInfo(ILnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"
            )
    )
    private List<MutableComponent> shhsc_c$addInscriptionInfo(AbstractSpell spell, int spellLevel, LivingEntity caster) {
        List<MutableComponent> lines = new ArrayList<>(spell.getUniqueInfo(spellLevel, caster));
        if (ShHsConfig.CLIENT.isToleranceTooltip.get()) {
            int effectiveLevel = spell.getLevelFor(spellLevel, caster);
            double requiredTolerance = getRequiredTolerance(effectiveLevel, spell, CastSource.SPELLBOOK);
            lines.add(textComponent("no_enough_spell_tolerance", Component.literal(String.valueOf(requiredTolerance)).withStyle(ChatFormatting.DARK_PURPLE)));
        }
        return lines;
    }
}
