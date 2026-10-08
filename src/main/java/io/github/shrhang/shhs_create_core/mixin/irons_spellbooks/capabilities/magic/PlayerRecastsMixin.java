package io.github.shrhang.shhs_create_core.mixin.irons_spellbooks.capabilities.magic;

import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellSupport;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(PlayerRecasts.class)
public abstract class PlayerRecastsMixin implements MobMagicManager.RecastOwner {
    @Shadow @Final private Map<String, RecastInstance> recastLookup;
    @Shadow @Final private ServerPlayer serverPlayer;
    @Unique private Mob shhs$owner;

    @Override
    public void shhs$bind(Mob mob) {
        shhs$owner = mob;
    }

    @Inject(method = "tick(I)V", at = @At("HEAD"), cancellable = true)
    private void shhs$tickMob(int ticks, CallbackInfo ci) {
        if (serverPlayer != null || shhs$owner == null || shhs$owner.level().isClientSide) return;
        if (ticks > 0 && shhs$owner.level().getGameTime() % ticks == 0) {
            for (var recast : recastLookup.values().stream().toList()) {
                int remaining = recast.getTicksRemaining() - ticks;
                ((RecastInstanceAccessor) recast).shhs$setRemainingTicks(remaining);
                if (remaining <= 0)
                    ((PlayerRecasts) (Object) this).removeRecast(recast, RecastResult.TIMEOUT);
            }
        }
        ci.cancel();
    }

    @Inject(method = "triggerRecastComplete(Lio/redspace/ironsspellbooks/capabilities/magic/RecastInstance;Lio/redspace/ironsspellbooks/capabilities/magic/RecastResult;)V",
            at = @At("HEAD"), cancellable = true)
    private void shhs$finishMob(RecastInstance recast, RecastResult result, CallbackInfo ci) {
        if (serverPlayer != null || shhs$owner == null) return;
        try {
            MobSpellSupport.finishRecast(shhs$owner, recast, result);
        } catch (RuntimeException exception) {
            MobMagicManager.fail(shhs$owner, recast.getSpellId(), exception);
        }
        ci.cancel();
    }
}
