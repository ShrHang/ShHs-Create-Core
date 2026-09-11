package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public final class ClientDrillEffectContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    public static void run(ClientLevel level, BlockPos pos, boolean suppressParticles, boolean playSound, Runnable action) {
        Scope previous = CURRENT.get();
        CURRENT.set(new Scope(level, pos.immutable(), suppressParticles, playSound));
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static boolean suppressSound(ClientLevel level, BlockPos pos) {
        Scope scope = matching(level, pos);
        if (scope == null || scope.soundClaimed) {
            return false;
        }
        scope.soundClaimed = true;
        return !scope.playSound;
    }

    public static boolean suppressParticles(ClientLevel level, BlockPos pos) {
        Scope scope = matching(level, pos);
        if (scope == null || scope.particlesClaimed) {
            return false;
        }
        scope.particlesClaimed = true;
        return scope.suppressParticles;
    }

    private static Scope matching(ClientLevel level, BlockPos pos) {
        Scope scope = CURRENT.get();
        return scope != null && scope.level == level && scope.pos.equals(pos) ? scope : null;
    }

    private static final class Scope {
        private final ClientLevel level;
        private final BlockPos pos;
        private final boolean suppressParticles;
        private final boolean playSound;
        private boolean soundClaimed;
        private boolean particlesClaimed;

        private Scope(ClientLevel level, BlockPos pos, boolean suppressParticles, boolean playSound) {
            this.level = level;
            this.pos = pos;
            this.suppressParticles = suppressParticles;
            this.playSound = playSound;
        }
    }
}
