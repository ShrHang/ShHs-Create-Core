package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ServerDrillSoundLimiter {
    public enum Kind { BREAK, HIT }
    private static final Map<ServerLevel, Map<Key, Long>> COOLDOWNS = new WeakHashMap<>();

    public static boolean allow(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source, Kind kind) {
        if (!enabled(kind)) {
            return true;
        }
        Map<Key, Long> cooldowns = COOLDOWNS.computeIfAbsent(level, ignored -> new HashMap<>());
        Key key = new Key(Math.floorDiv(pos.getX(), 4), Math.floorDiv(pos.getY(), 4),
                Math.floorDiv(pos.getZ(), 4), sound.getLocation(), source, kind);
        long now = level.getGameTime();
        if (now < cooldowns.getOrDefault(key, Long.MIN_VALUE)) {
            return false;
        }
        int interval = kind == Kind.BREAK ? ShHsConfig.SERVER.contraptionDrillBreakSoundIntervalTicks.get()
                : ShHsConfig.SERVER.contraptionDrillHitSoundIntervalTicks.get();
        cooldowns.put(key, now + interval);
        return true;
    }

    private static boolean enabled(Kind kind) {
        return kind == Kind.BREAK ? ShHsConfig.SERVER.limitContraptionDrillBreakSounds.get()
                : ShHsConfig.SERVER.limitContraptionDrillHitSounds.get();
    }

    public static void tick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<Key, Long> cooldowns = COOLDOWNS.get(level);
        if (cooldowns != null) {
            long now = level.getGameTime();
            boolean breaks = enabled(Kind.BREAK);
            boolean hits = enabled(Kind.HIT);
            cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now
                    || !(entry.getKey().kind == Kind.BREAK ? breaks : hits));
            if (cooldowns.isEmpty()) {
                COOLDOWNS.remove(level);
            }
        }
    }

    public static void unload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            COOLDOWNS.remove(level);
        }
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerDrillSoundLimiter::tick);
        NeoForge.EVENT_BUS.addListener(ServerDrillSoundLimiter::unload);
    }

    private record Key(int x, int y, int z, ResourceLocation sound, SoundSource source, Kind kind) {}
}
