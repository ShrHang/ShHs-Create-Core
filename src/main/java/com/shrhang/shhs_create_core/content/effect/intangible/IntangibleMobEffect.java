package com.shrhang.shhs_create_core.content.effect.intangible;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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

            boolean changed = IntangibleState.grantFlight(player); // 判断是否成功由无实体添加了飞行能力
            var abilities = player.getAbilities();
            changed |= !abilities.flying; // 判断飞行能力是否发生变化
            abilities.flying = true;

            if (changed && player instanceof ServerPlayer serverPlayer) { // 如果发生了变化则进行更新
                serverPlayer.onUpdateAbilities();
            }
        }
        return true;
    }
}
