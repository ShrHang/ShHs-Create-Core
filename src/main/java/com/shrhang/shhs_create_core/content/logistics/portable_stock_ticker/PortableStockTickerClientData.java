package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import java.util.*;

public class PortableStockTickerClientData {
    public static final int SNAPSHOT_TIMEOUT_TICKS = 20 * 60 * 5;
    public static final int SNAPSHOT_PRUNE_INTERVAL_TICKS = 20 * 10;
    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();
    private static long lastPruneTick = Long.MIN_VALUE;

    public static void clear() {
        SNAPSHOTS.clear();
        lastPruneTick = Long.MIN_VALUE;
    }

    public static void receiveStatus(UUID networkId, NetworkStatus status) {
        Snapshot snapshot = SNAPSHOTS.computeIfAbsent(networkId, id -> new Snapshot());
        snapshot.touch(snapshot.lastStatusRequestTick);
        snapshot.status = status;
        snapshot.lastStatusUpdateTick = snapshot.lastStatusRequestTick;
    }

    public static void receive(UUID networkId, List<BigItemStack> items, boolean endOfTransmission) {
        Snapshot snapshot = SNAPSHOTS.computeIfAbsent(networkId, id -> new Snapshot());
        snapshot.touch(snapshot.lastStatusRequestTick);
        snapshot.pendingItems.addAll(items);

        if (!endOfTransmission)
            return;

        snapshot.summary = new InventorySummary();
        snapshot.stockSnapshot = new ArrayList<>();
        for (BigItemStack item : snapshot.pendingItems)
            snapshot.summary.add(item);
        snapshot.stockSnapshot.add(new ArrayList<>(snapshot.pendingItems));
        snapshot.pendingItems.clear();
        snapshot.ticksSinceLastUpdate = 0;
    }

    public static Snapshot get(UUID networkId) {
        Snapshot snapshot = SNAPSHOTS.get(networkId);
        if (snapshot != null) {
            snapshot.touch(snapshot.lastStatusRequestTick);
        }
        return snapshot;
    }

    public static Snapshot getOrCreate(UUID networkId) {
        Snapshot snapshot = SNAPSHOTS.computeIfAbsent(networkId, id -> new Snapshot());
        snapshot.touch(snapshot.lastStatusRequestTick);
        return snapshot;
    }

    public static void pruneExpired(long gameTime) {
        if (lastPruneTick != Long.MIN_VALUE && gameTime - lastPruneTick < SNAPSHOT_PRUNE_INTERVAL_TICKS) {
            return;
        }
        lastPruneTick = gameTime;
        SNAPSHOTS.entrySet().removeIf(entry -> entry.getValue().isExpired(gameTime, SNAPSHOT_TIMEOUT_TICKS));
    }

    public static class Snapshot {
        private final List<BigItemStack> pendingItems = new ArrayList<>();
        private List<List<BigItemStack>> stockSnapshot = List.of();
        private InventorySummary summary = new InventorySummary();
        private int ticksSinceLastUpdate;
        private NetworkStatus status = NetworkStatus.UNKNOWN;
        private long lastStatusUpdateTick = Long.MIN_VALUE;
        private long lastStatusRequestTick = Long.MIN_VALUE;
        private long lastAccessTick = Long.MIN_VALUE;

        public List<List<BigItemStack>> stockSnapshot() {
            return stockSnapshot;
        }

        public InventorySummary summary() {
            return summary;
        }

        public int ticksSinceLastUpdate() {
            return ticksSinceLastUpdate;
        }

        public NetworkStatus status() {
            return status;
        }

        public boolean shouldRequestStatus(long gameTime, int interval) {
            if (lastStatusRequestTick == Long.MIN_VALUE) {
                return true;
            }
            return gameTime - lastStatusRequestTick >= interval;
        }

        public void markStatusRequest(long gameTime) {
            lastStatusRequestTick = gameTime;
            touch(gameTime);
        }

        public boolean isStatusStale(long gameTime, int interval) {
            if (lastStatusUpdateTick == Long.MIN_VALUE) {
                return true;
            }
            return gameTime - lastStatusUpdateTick > interval;
        }

        public void tick() {
            if (ticksSinceLastUpdate < 100)
                ticksSinceLastUpdate++;
        }

        private void touch(long gameTime) {
            if (gameTime != Long.MIN_VALUE) {
                lastAccessTick = gameTime;
            }
        }

        private boolean isExpired(long gameTime, int timeout) {
            return lastAccessTick != Long.MIN_VALUE && gameTime - lastAccessTick > timeout;
        }
    }

    public enum NetworkStatus {
        UNKNOWN,
        AVAILABLE,
        NO_NETWORK,
        UNLOADED
    }
}
