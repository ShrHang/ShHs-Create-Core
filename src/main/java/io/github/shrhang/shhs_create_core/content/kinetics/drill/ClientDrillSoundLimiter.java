package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

// Accessed only on the client main thread; cooldowns use client ticks, not world time.
public final class ClientDrillSoundLimiter {
    public enum Kind { BREAK, HIT }
    private static final int CELL_SIZE = 4;
    private static final Map<Key, Long> NEXT_PLAY_TICKS = new HashMap<>();
    private static ClientLevel currentLevel;
    private static long clientTick;

    private ClientDrillSoundLimiter() {}

    public static boolean allow(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source, Kind kind) {
        if (currentLevel != level) {
            reset();
            currentLevel = level;
        }
        Key key = new Key(Math.floorDiv(pos.getX(), CELL_SIZE), Math.floorDiv(pos.getY(), CELL_SIZE),
                Math.floorDiv(pos.getZ(), CELL_SIZE), sound.getLocation(), source, kind);
        if (clientTick < NEXT_PLAY_TICKS.getOrDefault(key, 0L)) {
            return false;
        }
        int interval = kind == Kind.BREAK ? ShHsConfig.CLIENT.contraptionDrillBreakSoundIntervalTicks.get()
                : ShHsConfig.CLIENT.contraptionDrillHitSoundIntervalTicks.get();
        NEXT_PLAY_TICKS.put(key, clientTick + interval);
        return true;
    }

    public static void tick(ClientLevel level, boolean paused) {
        if (currentLevel != level) {
            reset();
            currentLevel = level;
        }
        if (level == null || paused) {
            return;
        }
        clientTick++;
        NEXT_PLAY_TICKS.entrySet().removeIf(entry -> entry.getValue() <= clientTick
                || (entry.getKey().kind == Kind.BREAK ? !ShHsConfig.CLIENT.limitContraptionDrillBreakSounds.get()
                : !ShHsConfig.CLIENT.limitContraptionDrillHitSounds.get()));
    }

    public static void reset() {
        NEXT_PLAY_TICKS.clear();
        currentLevel = null;
        clientTick = 0;
    }

    private record Key(int x, int y, int z, ResourceLocation sound, SoundSource source, Kind kind) {}
}
