package io.github.shrhang.shhs_create_core.mixin.l2hostility.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.xkmc.l2damagetracker.contents.attack.DamageData;
import dev.xkmc.l2damagetracker.contents.attack.DamageModifier;
import dev.xkmc.l2hostility.events.LHAttackListener;
import dev.xkmc.l2hostility.init.registrate.LHItems;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.CuriosApi;

import static io.github.shrhang.shhs_create_core.content.util.hostility.RealityIndexHelper.getDamageReduce;
import static io.github.shrhang.shhs_create_core.content.util.hostility.RealityIndexHelper.getRealityIndex;

@Mixin(LHAttackListener.class)
public abstract class LHAttackListenerMixin {
    @WrapOperation(method = "onHurt(Ldev/xkmc/l2damagetracker/contents/attack/DamageData$Offence;)V",
            remap = false, require = 1,
            at = @At(value = "INVOKE", target = "Ldev/xkmc/l2damagetracker/contents/attack/DamageModifier;multTotal(FLnet/minecraft/resources/ResourceLocation;)Ldev/xkmc/l2damagetracker/contents/attack/DamageModifier;"))
    private DamageModifier shhsc_c$scaleLevelBonus(float multiplier, ResourceLocation id,
                                                   Operation<DamageModifier> original,
                                                   @Local(argsOnly = true) DamageData.Offence data,
                                                   @SuppressWarnings("UnresolvedLocalCapture") @Local(name = "old") double levelBonus,
                                                   @Local(name = "factor") double finalBonus) {
        var attacker = data.getAttacker();
        var target = data.getTarget();
        if (levelBonus > 0 && finalBonus > 0 && attacker != null && attacker != target
                && !CuriosApi.getCuriosInventory(target)
                .map(handler -> handler.isEquipped(LHItems.CURSE_PRIDE.get()))
                .orElse(false)) {
            double retained = getDamageReduce(getRealityIndex(target), getRealityIndex(attacker));
            if (retained < 1) {
                double removed = Math.min(finalBonus, levelBonus * (1 - retained));
                multiplier = 1.0f + (float) (finalBonus - removed);
            }
        }
        return original.call(multiplier, id);
    }
}
