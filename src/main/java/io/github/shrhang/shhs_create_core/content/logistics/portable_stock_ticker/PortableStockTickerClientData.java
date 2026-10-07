package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import java.util.*;

public class PortableStockTickerClientData {
    public static final int SNAPSHOT_TIMEOUT_TICKS = 20 * 60 * 5;
    public static final int SNAPSHOT_PRUNE_INTERVAL_TICKS = 20 * 10;
    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();
    private static long lastPruneTick = Long.MIN_VALUE;
    private static long nextRevision; // 重建的快照必须与当前打开屏幕的修订版本不同，因此不能放入clear()中

    public static void clear() {
        SNAPSHOTS.clear();
        lastPruneTick = Long.MIN_VALUE;
    }

    public static void receiveStatus(UUID networkId, LogisticsNetworkStatus status) {
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

        InventorySummary summary = new InventorySummary();
        for (BigItemStack item : snapshot.pendingItems)
            summary.add(item);
        snapshot.summary = summary;
        snapshot.revision = ++nextRevision;
        snapshot.pendingItems.clear();
        snapshot.ticksSinceLastUpdate = 0;
        snapshot.stockRequestPending = false;
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
        SNAPSHOTS.entrySet().removeIf(entry -> entry.getValue().isExpired(gameTime));
    }

    public static class Snapshot {
        private static final int STOCK_REFRESH_INTERVAL = 16;
        private static final int STOCK_REQUEST_TIMEOUT = 100;
        private final List<BigItemStack> pendingItems = new ArrayList<>();
        private long revision = ++nextRevision;
        private InventorySummary summary = new InventorySummary();
        private int ticksSinceLastUpdate;
        private LogisticsNetworkStatus status = LogisticsNetworkStatus.INACCESSIBLE;
        private long lastStatusUpdateTick = Long.MIN_VALUE;
        private long lastStatusRequestTick = Long.MIN_VALUE;
        private long lastAccessTick = Long.MIN_VALUE;
        private long lastStockRequestTick = Long.MIN_VALUE;
        private boolean stockRequestPending;
        private boolean forcedStockRefresh;

        public long revision() {
            return revision;
        }

        public InventorySummary summary() {
            return summary;
        }

        public boolean requestStock(long gameTime, boolean force) {
            touch(gameTime);
            forcedStockRefresh |= force;
            long elapsed = gameTime - lastStockRequestTick;
            boolean neverRequested = lastStockRequestTick == Long.MIN_VALUE;
            boolean clockReset = !neverRequested && gameTime < lastStockRequestTick;
            if (stockRequestPending && !clockReset && elapsed < STOCK_REQUEST_TIMEOUT) {
                return false;
            }
            // Without request IDs a late response can finish an older request; retain a send interval.
            if (!neverRequested && !clockReset && !forcedStockRefresh
                    && (ticksSinceLastUpdate < STOCK_REFRESH_INTERVAL || elapsed < STOCK_REFRESH_INTERVAL)) {
                return false;
            }
            stockRequestPending = true;
            forcedStockRefresh = false;
            lastStockRequestTick = gameTime;
            return true;
        }

        public LogisticsNetworkStatus status() {
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

        private boolean isExpired(long gameTime) {
            return lastAccessTick != Long.MIN_VALUE && gameTime - lastAccessTick > SNAPSHOT_TIMEOUT_TICKS;
        }
    }

}
