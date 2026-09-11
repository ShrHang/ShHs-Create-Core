package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class DrillHitSoundContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private DrillHitSoundContext() {}

    public static void run(Level level, BlockPos pos, boolean active, Runnable action) {
        Scope previous = CURRENT.get();
        CURRENT.set(active ? new Scope(level, pos.immutable()) : null);
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

    public static boolean consume(Level level, double x, double y, double z) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.consumed || scope.level != level
                || x != scope.pos.getX() + 0.5 || y != scope.pos.getY() + 0.5 || z != scope.pos.getZ() + 0.5) {
            return false;
        }
        scope.consumed = true;
        return true;
    }

    private static final class Scope {
        private final Level level;
        private final BlockPos pos;
        private boolean consumed;

        private Scope(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }
}
