package com.shrhang.shhs_create_core.mixin.irons_spellbooks.capabilities.magic;

import com.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSummonManager;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(PlayerRecasts.class)
public abstract class PlayerRecastsMixin {
    @Shadow
    @Final
    private Map<String, RecastInstance> recastLookup;

    @Shadow
    private ServerPlayer serverPlayer;

    /**
     * 当非玩家施法者的重施法次数归零时，清理该法术产生的召唤物。
     */
    @Inject(method = "decrementRecastCount", at = @At("HEAD"), cancellable = true)
    private void shhsc_c$onDecrementRecastCount(String spellId, CallbackInfo ci) {
        if (this.serverPlayer == null) {
            RecastInstance instance = this.recastLookup.get(spellId);
            if (instance != null) {
                // 通过映射获取 MagicData 和施法者
                MagicData magicData = MobSummonManager.getMagicData((PlayerRecasts)(Object)this);
                if (magicData != null) {
                    LivingEntity caster = MobSummonManager.getCaster(magicData);
                    if (caster != null) {
                        MobSummonManager.onRecastFinished(caster, spellId, instance);
                    }
                }
                this.recastLookup.remove(spellId);
            }
            ci.cancel();
        }
    }
}