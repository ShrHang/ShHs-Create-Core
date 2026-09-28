package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent;
import io.github.shrhang.shhs_create_core.ShHsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DimensionParcelStationBindingIndex extends SavedData {
    private static final String DATA_NAME = "ShHsDimensionParcelStations";
    private static final Factory<DimensionParcelStationBindingIndex> FACTORY =
            new Factory<>(DimensionParcelStationBindingIndex::new, DimensionParcelStationBindingIndex::load);

    private final Map<GlobalPos, Entry> entries = new HashMap<>();
    private long nextSequence;

    public static DimensionParcelStationBindingIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(DimensionParcelStationBindingIndex::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DimensionParcelStationBindingIndex::onNetworkDestroyed);
    }

    private static DimensionParcelStationBindingIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        DimensionParcelStationBindingIndex index = new DimensionParcelStationBindingIndex();
        index.nextSequence = Math.max(0L, tag.getLong("NextSequence"));
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entryTag.getString("Dimension"));
            if (dimension == null || !entryTag.contains("Pos") || !entryTag.contains("NetId"))
                continue;
            GlobalPos pos = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                    BlockPos.of(entryTag.getLong("Pos")));
            long sequence = Math.max(0L, entryTag.getLong("Sequence"));
            index.entries.put(pos, new Entry(entryTag.getInt("NetId"), sequence,
                    entryTag.getBoolean("Revoked")));
            index.nextSequence = Math.max(index.nextSequence, sequence + 1L);
        }
        return index;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextSequence", nextSequence);
        ListTag list = new ListTag();
        entries.forEach((pos, entry) -> {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Dimension", pos.dimension().location().toString());
            entryTag.putLong("Pos", pos.pos().asLong());
            entryTag.putInt("NetId", entry.netId());
            entryTag.putLong("Sequence", entry.sequence());
            entryTag.putBoolean("Revoked", entry.revoked());
            list.add(entryTag);
        });
        tag.put("Entries", list);
        return tag;
    }

    public synchronized boolean tryReserve(MinecraftServer server, GlobalPos pos, int netId) {
        Entry existing = entries.get(pos);
        if (existing != null && existing.revoked()) {
            entries.remove(pos);
            setDirty();
        }

        cleanLoadedStaleEntries(server);
        reconcile(server);
        existing = entries.get(pos);
        if (existing != null && !existing.revoked() && existing.netId() == netId)
            return true;
        int limit = ShHsConfig.SERVER.dimensionParcelStationMaxPerNetwork.get();
        if (limit == 0)
            return false;
        if (limit > 0 && activeCount(netId) >= limit)
            return false;

        entries.put(pos, new Entry(netId, nextSequence++, false));
        setDirty();
        return true;
    }

    public synchronized void release(GlobalPos pos) {
        if (entries.remove(pos) != null)
            setDirty();
    }

    public synchronized void onStationLoaded(DimensionParcelStationBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level))
            return;
        GlobalPos pos = GlobalPos.of(level.dimension(), station.getBlockPos());
        if (station.getNetId() < 0) {
            release(pos);
            return;
        }
        Entry entry = entries.get(pos);
        if (entry != null && entry.revoked()) {
            entries.remove(pos);
            setDirty();
            station.clearNetId();
            return;
        }
        if (station.getNet() == null || !tryReserve(level.getServer(), pos, station.getNetId()))
            station.clearNetId();
    }

    public synchronized int activeCount(int netId) {
        int count = 0;
        for (Entry entry : entries.values())
            if (!entry.revoked() && entry.netId() == netId)
                count++;
        return count;
    }

    public synchronized List<GlobalPos> stations(int netId) {
        return entries.entrySet().stream()
                .filter(entry -> !entry.getValue().revoked() && entry.getValue().netId() == netId)
                .sorted(Comparator.comparingLong(entry -> entry.getValue().sequence()))
                .map(Map.Entry::getKey).toList();
    }

    public synchronized void reconcile(MinecraftServer server) {
        int limit = ShHsConfig.SERVER.dimensionParcelStationMaxPerNetwork.get();
        if (limit < 0)
            return;

        Map<Integer, List<Map.Entry<GlobalPos, Entry>>> byNetwork = new HashMap<>();
        for (Map.Entry<GlobalPos, Entry> entry : entries.entrySet())
            if (!entry.getValue().revoked())
                byNetwork.computeIfAbsent(entry.getValue().netId(), ignored -> new ArrayList<>()).add(entry);

        boolean changed = false;
        for (List<Map.Entry<GlobalPos, Entry>> networkEntries : byNetwork.values()) {
            networkEntries.sort(Comparator.comparingLong(value -> value.getValue().sequence()));
            for (int i = limit; i < networkEntries.size(); i++) {
                Map.Entry<GlobalPos, Entry> indexed = networkEntries.get(i);
                Entry old = indexed.getValue();
                entries.put(indexed.getKey(), new Entry(old.netId(), old.sequence(), true));
                changed = true;
            }
        }
        if (changed)
            setDirty();

        List<GlobalPos> revoked = entries.entrySet().stream()
                .filter(entry -> entry.getValue().revoked())
                .map(Map.Entry::getKey)
                .toList();
        for (GlobalPos pos : revoked)
            clearLoadedStation(server, pos, entries.get(pos).netId());
    }

    private void cleanLoadedStaleEntries(MinecraftServer server) {
        List<GlobalPos> stale = new ArrayList<>();
        for (Map.Entry<GlobalPos, Entry> indexed : entries.entrySet()) {
            ServerLevel level = server.getLevel(indexed.getKey().dimension());
            if (level == null || !level.isLoaded(indexed.getKey().pos()))
                continue;
            if (!(level.getBlockEntity(indexed.getKey().pos()) instanceof DimensionParcelStationBlockEntity station)
                    || station.getNetId() != indexed.getValue().netId())
                stale.add(indexed.getKey());
        }
        if (!stale.isEmpty()) {
            stale.forEach(entries::remove);
            setDirty();
        }
    }

    private void clearLoadedStation(MinecraftServer server, GlobalPos pos, int netId) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !level.isLoaded(pos.pos()))
            return;
        if (level.getBlockEntity(pos.pos()) instanceof DimensionParcelStationBlockEntity station
                && station.getNetId() == netId) {
            station.clearNetId();
        } else {
            entries.remove(pos);
            setDirty();
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        DimensionParcelStationBindingIndex index = get(event.getServer());
        index.cleanLoadedStaleEntries(event.getServer());
        index.reconcile(event.getServer());
    }

    private static void onNetworkDestroyed(DimensionsNetEvent.Destroyed event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        DimensionParcelStationBindingIndex index = get(server);
        List<GlobalPos> affected;
        synchronized (index) {
            affected = index.entries.entrySet().stream()
                    .filter(entry -> entry.getValue().netId() == event.getDestroyedId())
                    .map(Map.Entry::getKey)
                    .toList();
            if (!affected.isEmpty()) {
                affected.forEach(index.entries::remove);
                index.setDirty();
            }
        }
        for (GlobalPos pos : affected)
            index.clearLoadedStation(server, pos, event.getDestroyedId());
    }

    public static void reconcileCurrentServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                DimensionParcelStationBindingIndex index = get(server);
                index.cleanLoadedStaleEntries(server);
                index.reconcile(server);
            });
        }
    }

    private record Entry(int netId, long sequence, boolean revoked) {
    }
}
