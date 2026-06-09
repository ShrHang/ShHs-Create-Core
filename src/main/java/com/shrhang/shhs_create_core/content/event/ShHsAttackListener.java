package com.shrhang.shhs_create_core.content.event;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.api.registries.ShHsEffects;
import dev.xkmc.l2damagetracker.contents.attack.AttackEventHandler;
import dev.xkmc.l2damagetracker.contents.attack.AttackListener;
import dev.xkmc.l2damagetracker.contents.attack.DamageData;
import dev.xkmc.l2damagetracker.contents.attack.DamageModifier;
import dev.xkmc.l2damagetracker.init.data.L2DamageTypes;
import dev.xkmc.l2hostility.init.data.LHConfig;
import dev.xkmc.l2hostility.init.data.LHTagGen;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import static com.shrhang.shhs_create_core.content.util.RealityIndexHelper.getDamageReduce;
import static com.shrhang.shhs_create_core.content.util.RealityIndexHelper.getRealityIndex;

public class ShHsAttackListener implements AttackListener {
    private static final ResourceLocation REALITY_SCALING = ShHsCreateCore.rl("reality_scaling");
    private static final ResourceLocation INTANGIBLE_DAMAGE_CAP = ShHsCreateCore.rl("intangible_damage_cap");

    @Override
    public void onDamage(DamageData.Defence data) {
        applyRealityScaling(data);
    }

    private static void applyRealityScaling(DamageData.Defence data) {
        var source = data.getSource();
        LivingEntity target = data.getTarget();
        if (source.is(L2DamageTypes.NO_SCALE)) return;

        var attacker = data.getAttacker();
        if (attacker == null || attacker == target) return;

        var attOpt = LHMiscs.MOB.type().getExisting(attacker);
        double reduce = getDamageReduce(getRealityIndex(target), getRealityIndex(attacker));
        if (attOpt.isPresent() && !attacker.getType().is(LHTagGen.NO_SCALING) && reduce != 1) {
            var cap = attOpt.get();
            int lv = cap.getLevel();
            double factor;

            if (LHConfig.SERVER.exponentialDamage.get()) {
                factor = Math.pow(1 + LHConfig.SERVER.damageFactor.get(), lv) - 1;
            } else {
                factor = lv * LHConfig.SERVER.damageFactor.get();
            }

            var config = cap.getConfigCache(attacker);
            if (config != null) {
                factor *= config.attackScale;
            }

            float originalMultiplier = 1.0f + (float) factor;
            float targetMultiplier = 1.0f + (float) (factor * reduce);
            float compensation = Math.round((targetMultiplier / originalMultiplier) * 100.0f) / 100.0f;
            data.addDealtModifier(DamageModifier.multTotal(compensation, REALITY_SCALING));
        }
    }

    public static void init() {
        AttackEventHandler.register(823, new ShHsAttackListener());
    }
}
