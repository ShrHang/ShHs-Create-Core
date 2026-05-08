package com.shrhang.shhs_create_core.content.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

import static dev.xkmc.curseofpandora.init.registrate.CoPAttrs.REALITY;

public class RealityIndexHelper {

    public static double getDamageReduce(double selfIndex, double attackerIndex) {
        return 1 / (1 + Math.max(selfIndex - attackerIndex, 0));
    }

    public static double getRealityIndex(LivingEntity entity) {
        return Objects.requireNonNull(entity.getAttribute(REALITY)).getValue();
    }
}
