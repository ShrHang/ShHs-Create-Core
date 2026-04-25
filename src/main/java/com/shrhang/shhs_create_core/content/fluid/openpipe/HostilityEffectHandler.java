package com.shrhang.shhs_create_core.content.fluid.openpipe;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class HostilityEffectHandler implements OpenPipeEffectHandler {
        @Override
        public void apply(Level level, AABB area, FluidStack fluid) {
            if (level.getGameTime() % 5 != 0)
                return;
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAffectedByPotions);
            for (LivingEntity entity : entities) {
                if (LHMiscs.MOB.type().isProper(entity)) {
                    MobTraitCap cap = LHMiscs.MOB.type().getOrCreate(entity);
                    cap.setLevel(entity, cap.lv + 5);
                    cap.syncToClient(entity);
                }
            }
        }
}
