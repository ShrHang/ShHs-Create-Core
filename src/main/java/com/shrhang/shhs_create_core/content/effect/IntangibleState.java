package com.shrhang.shhs_create_core.content.effect;

import net.minecraft.world.entity.player.Player;

public class IntangibleState {
    private boolean active;
    private boolean mayfly;
    private boolean flying;
    private float flyingSpeed;

    public boolean isActive() {
        return active;
    }

    public void capture(Player player) {
        var abilities = player.getAbilities();
        active = true;
        mayfly = abilities.mayfly;
        flying = abilities.flying;
        flyingSpeed = abilities.getFlyingSpeed();
    }

    public void restore(Player player) {
        if (!active) return;

        var abilities = player.getAbilities();
        player.noPhysics = player.isSpectator();
        if (!player.isSpectator()) {
            player.setNoGravity(false);
        }
        abilities.mayfly = mayfly;
        abilities.flying = flying;
        abilities.setFlyingSpeed(flyingSpeed);
        active = false;
    }
}
