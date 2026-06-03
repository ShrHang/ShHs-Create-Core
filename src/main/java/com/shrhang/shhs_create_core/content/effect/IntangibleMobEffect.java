package com.shrhang.shhs_create_core.content.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class IntangibleMobEffect extends MobEffect {
    public IntangibleMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x6ed28e);
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

            var abilities = player.getAbilities();
            boolean changed = !abilities.mayfly || !abilities.flying;
            abilities.mayfly = true;
            abilities.flying = true;

            Vec3 movement = player.getDeltaMovement();
            if (movement.y() < 0) {
                player.setDeltaMovement(movement.x(), 0, movement.z());
            }

            if (changed && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.onUpdateAbilities();
            }
        }
        return true;
    }
}
