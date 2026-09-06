package io.github.shrhang.shhs_create_core.content.event;

import dev.xkmc.l2hostility.init.registrate.LHItems;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import dev.xkmc.l2damagetracker.contents.attack.AttackEventHandler;
import dev.xkmc.l2damagetracker.contents.attack.AttackListener;
import dev.xkmc.l2damagetracker.contents.attack.DamageData;
import dev.xkmc.l2damagetracker.contents.attack.DamageModifier;
import dev.xkmc.l2damagetracker.init.data.L2DamageTypes;
import dev.xkmc.l2hostility.init.data.LHConfig;
import dev.xkmc.l2hostility.init.data.LHTagGen;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;

import static io.github.shrhang.shhs_create_core.content.util.hostility.RealityIndexHelper.getDamageReduce;
import static io.github.shrhang.shhs_create_core.content.util.hostility.RealityIndexHelper.getRealityIndex;

public class ShHsAttackListener implements AttackListener {
    private static final ResourceLocation REALITY_SCALING = ShHsCreateCore.rl("reality_scaling");

    @Override
    public void onDamage(DamageData.Defence data) {
        applyRealityScaling(data);
    }

    private static void applyRealityScaling(DamageData.Defence data) {
        var source = data.getSource();
        if (source.is(L2DamageTypes.NO_SCALE)) return;

        var target = data.getTarget();
        if (hasCurseOfPride(target)) return;

        var attacker = data.getAttacker();
        if (attacker == null || attacker == target) return;

        double reduce = getDamageReduce(getRealityIndex(target), getRealityIndex(attacker));
        if (reduce == 1) return;

        var attOpt = LHMiscs.MOB.type().getExisting(attacker);
        if (attOpt.isPresent() && !attacker.getType().is(LHTagGen.NO_SCALING)) {
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

    private static boolean hasCurseOfPride(LivingEntity target) {
        return CuriosApi.getCuriosInventory(target)
                .map(handler -> handler.isEquipped(LHItems.CURSE_PRIDE.get()))
                .orElse(false);
    }

    public static void init() {
        AttackEventHandler.register(823, new ShHsAttackListener());
    }
}
