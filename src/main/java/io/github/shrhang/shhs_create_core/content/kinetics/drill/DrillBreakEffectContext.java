package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class DrillBreakEffectContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private DrillBreakEffectContext() {}

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

    // Only the matching effect can consume the marker, not nested unrelated effects.
    public static boolean consume(Level level, BlockPos pos) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.consumed || scope.level != level || !scope.pos.equals(pos)) {
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
