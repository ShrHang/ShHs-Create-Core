package io.github.shrhang.shhs_create_core.content.util.hostility;

import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

import static dev.xkmc.curseofpandora.init.registrate.CoPAttrs.REALITY;

public class RealityIndexHelper {

    public static double getDamageReduce(double selfIndex, double attackerIndex) {
        return Math.clamp(1 - Math.log(Math.max(selfIndex - attackerIndex, 0) + 1) / Math.log(16), 0, 1);
    }

    public static double getRealityIndex(LivingEntity entity) {
        return Objects.requireNonNull(entity.getAttribute(REALITY)).getValue();
    }
}
