package io.github.shrhang.shhs_create_core.mixin.irons_spellbooks.api.util;

import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Utils.class)
public class UtilsMixin {
    // Counterspell's raycast otherwise never reaches ordinary mobs or posts its event.
    @Inject(method = "validAntiMagicTarget(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
    private static void shhs$includeEnabledCaster(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && !entity.isSpectator() && entity instanceof Mob mob
                && MobMagicManager.isEnabled(mob)) cir.setReturnValue(true);
    }
}
