package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PortableStockTickerClientData {
    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();

    public static void receiveStatus(UUID networkId, NetworkStatus status) {
        Snapshot snapshot = SNAPSHOTS.computeIfAbsent(networkId, id -> new Snapshot());
        snapshot.status = status;
        snapshot.lastStatusUpdateTick = snapshot.lastStatusRequestTick;
    }

    public static void receive(UUID networkId, List<BigItemStack> items, boolean endOfTransmission) {
        Snapshot snapshot = SNAPSHOTS.computeIfAbsent(networkId, id -> new Snapshot());
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
        return SNAPSHOTS.get(networkId);
    }

    public static Snapshot getOrCreate(UUID networkId) {
        return SNAPSHOTS.computeIfAbsent(networkId, id -> new Snapshot());
    }

    public static class Snapshot {
        private final List<BigItemStack> pendingItems = new ArrayList<>();
        private List<List<BigItemStack>> stockSnapshot = List.of();
        private InventorySummary summary = new InventorySummary();
        private int ticksSinceLastUpdate;
        private NetworkStatus status = NetworkStatus.UNKNOWN;
        private long lastStatusUpdateTick = Long.MIN_VALUE;
        private long lastStatusRequestTick = Long.MIN_VALUE;

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
    }

    public enum NetworkStatus {
        UNKNOWN,
        AVAILABLE,
        NO_NETWORK,
        UNLOADED
    }
}
