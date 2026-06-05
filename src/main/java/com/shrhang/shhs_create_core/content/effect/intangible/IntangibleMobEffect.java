package com.shrhang.shhs_create_core.content.effect.intangible;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class IntangibleMobEffect extends MobEffect {
    public IntangibleMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9AE9B6);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            player.noPhysics = true;
            player.resetFallDistance();

            if (IntangibleState.applyFlight(player) && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.onUpdateAbilities();
            }
        }
        return true;
    }
}
