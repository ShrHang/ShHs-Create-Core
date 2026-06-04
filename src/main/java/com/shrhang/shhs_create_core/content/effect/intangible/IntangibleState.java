package com.shrhang.shhs_create_core.content.effect.intangible;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

public class IntangibleState {
    private static final ResourceLocation FLIGHT_MODIFIER = ShHsCreateCore.rl("intangible_flight");

    private boolean active;
    private boolean flying;
    private float flyingSpeed;

    public boolean isActive() {
        return active;
    }

    /**
     * 捕获玩家当前的飞行状态和飞行速度。
     */
    public void capture(Player player) {
        var abilities = player.getAbilities();
        active = true;
        flying = abilities.flying;
        flyingSpeed = abilities.getFlyingSpeed();
    }

    /**
     * 复原玩家的飞行状态和飞行速度，并撤销任何由无实体状态授予的飞行能力。
     */
    public void restore(Player player) {
        if (!active) return;

        var abilities = player.getAbilities();
        player.noPhysics = player.isSpectator();
        if (!player.isSpectator()) {
            player.setNoGravity(false);
        }
        revokeFlight(player);
        abilities.flying = flying;
        abilities.setFlyingSpeed(flyingSpeed);
        active = false;
    }

    /**
     * 通过添属性加修饰符授予玩家飞行能力，如果玩家已经拥有该能力则不进行任何操作。
     * @return 是否成功添加属性修饰符
     */
    public static boolean grantFlight(Player player) {
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute == null || attribute.hasModifier(FLIGHT_MODIFIER)) {
            return false;
        }
        attribute.addTransientModifier(new AttributeModifier(
                FLIGHT_MODIFIER,
                1.0,
                AttributeModifier.Operation.ADD_VALUE
        ));
        return true;
    }

    public static void revokeFlight(Player player) {
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute != null) {
            attribute.removeModifier(FLIGHT_MODIFIER);
        }
    }
}
