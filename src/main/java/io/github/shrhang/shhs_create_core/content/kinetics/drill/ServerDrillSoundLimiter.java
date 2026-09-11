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

import java.util.*;

public final class ServerDrillSoundLimiter {
    public enum Kind { BREAK, HIT }
    private static final Map<ServerLevel, Cooldowns> COOLDOWNS = new WeakHashMap<>();

    private ServerDrillSoundLimiter() {}

    public static boolean allow(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source, Kind kind) {
        if (!enabled(kind)) {
            return true;
        }
        Cooldowns cooldowns = COOLDOWNS.computeIfAbsent(level, ignored -> new Cooldowns());
        Key key = new Key(Math.floorDiv(pos.getX(), 4), Math.floorDiv(pos.getY(), 4),
                Math.floorDiv(pos.getZ(), 4), sound.getLocation(), source, kind);
        long now = level.getGameTime();
        Long expiresAt = cooldowns.entries.get(key);
        if (expiresAt != null && now < expiresAt) {
            return false;
        }
        if (expiresAt != null) {
            cooldowns.removeFromBucket(key, expiresAt);
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
        Cooldowns cooldowns = COOLDOWNS.get(level);
        if (cooldowns != null) {
            long now = level.getGameTime();
            boolean breaks = enabled(Kind.BREAK);
            boolean hits = enabled(Kind.HIT);
            cooldowns.removeExpired(now);
            cooldowns.updateEnabled(breaks, hits);
            if (cooldowns.entries.isEmpty()) {
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

    private static final class Cooldowns {
        private final Map<Key, Long> entries = new HashMap<>();
        private final NavigableMap<Long, Set<Key>> expirationBuckets = new TreeMap<>();
        private boolean breaksEnabled = enabled(Kind.BREAK);
        private boolean hitsEnabled = enabled(Kind.HIT);

        private void put(Key key, long expiresAt) {
            entries.put(key, expiresAt);
            expirationBuckets.computeIfAbsent(expiresAt, ignored -> new HashSet<>()).add(key);
        }

        private void removeExpired(long now) {
            Iterator<Map.Entry<Long, Set<Key>>> buckets = expirationBuckets.entrySet().iterator();
            while (buckets.hasNext()) {
                Map.Entry<Long, Set<Key>> bucket = buckets.next();
                if (bucket.getKey() > now) {
                    break;
                }
                for (Key key : bucket.getValue()) {
                    entries.remove(key, bucket.getKey());
                }
                buckets.remove();
            }
        }

        private void updateEnabled(boolean breaks, boolean hits) {
            if (breaksEnabled && !breaks) {
                removeKind(Kind.BREAK);
            }
            if (hitsEnabled && !hits) {
                removeKind(Kind.HIT);
            }
            breaksEnabled = breaks;
            hitsEnabled = hits;
        }

        private void removeKind(Kind kind) {
            Iterator<Map.Entry<Key, Long>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Key, Long> entry = iterator.next();
                if (entry.getKey().kind() == kind) {
                    removeFromBucket(entry.getKey(), entry.getValue());
                    iterator.remove();
                }
            }
        }

        private void removeFromBucket(Key key, long expiresAt) {
            Set<Key> bucket = expirationBuckets.get(expiresAt);
            if (bucket != null) {
                bucket.remove(key);
                if (bucket.isEmpty()) {
                    expirationBuckets.remove(expiresAt);
                }
            }
        }
    }
}
