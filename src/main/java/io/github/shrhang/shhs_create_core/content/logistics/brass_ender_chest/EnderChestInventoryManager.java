package io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest;

import com.mojang.logging.LogUtils;
import io.github.shrhang.shhs_create_core.mixin.minecraft.server.players.IntegratedPlayerDataAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-scoped bridge between online and persisted ender chest inventories. */
public final class EnderChestInventoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAVE_DELAY_TICKS = 200;
    private static final int RETRY_DELAY_TICKS = 200;
    private static final int MAX_QUEUE_CHECKS_PER_TICK = 32;
    private static final int ERROR_LOG_INTERVAL_TICKS = 1200;
    private static final int FAILED_ENTRY_TTL = 12000;
    private static final DateTimeFormatter CORRUPT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final Map<MinecraftServer, EnderChestInventoryManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Set<UUID> tracked = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> loadQueue = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> saveQueue = new LinkedHashSet<>();
    private final Set<UUID> transitioning = new LinkedHashSet<>();
    private long ticks;
    private boolean preferLoad;
    private boolean stopping;
    private int queueChecksRemaining;

    private EnderChestInventoryManager(MinecraftServer server) {
        this.server = server;
    }

    public static EnderChestInventoryManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, EnderChestInventoryManager::new);
    }

    public static void remove(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public @Nullable PlayerEnderChestContainer getInventory(UUID owner, boolean requestLoad) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            tracked.add(owner);
            return player.getEnderChestInventory();
        }
        Entry entry = entries.get(owner);
        if (entry != null && entry.ready)
            return entry.inventory;
        if (requestLoad && !stopping && !transitioning.contains(owner)) {
            tracked.add(owner);
            if (entry == null) {
                entry = new Entry();
                entries.put(owner, entry);
            }
            entry.lastRequest = ticks;
            if (ticks >= entry.retryAt)
                loadQueue.add(owner);
        }
        return null;
    }

    public boolean isAvailable(UUID owner) {
        return getInventory(owner, false) != null;
    }

    public void markDirty(UUID owner) {
        Entry entry = entries.get(owner);
        if (entry == null || !entry.ready || server.getPlayerList().getPlayer(owner) != null)
            return;
        if (!entry.dirty) {
            entry.dirty = true;
            entry.saveAt = ticks + SAVE_DELAY_TICKS;
        }
        saveQueue.add(owner);
    }

    public void onPlayerLoading(ServerPlayer player) {
        UUID owner = player.getUUID();
        transitioning.add(owner);
        transferToPlayer(owner, player);
    }

    public void onPlayerLoggedIn(ServerPlayer player) {
        UUID owner = player.getUUID();
        transferToPlayer(owner, player);
        transitioning.remove(owner);
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        UUID owner = player.getUUID();
        transitioning.remove(owner);
        if (!tracked.contains(owner))
            return;
        Entry entry = new Entry();
        entry.inventory = player.getEnderChestInventory();
        entry.ready = true;
        entry.lastRequest = ticks;
        entries.put(owner, entry);
        loadQueue.remove(owner);
        saveQueue.remove(owner);
    }

    public void tick() {
        ticks++;
        queueChecksRemaining = MAX_QUEUE_CHECKS_PER_TICK;
        if (preferLoad)
            runLoadOrSave();
        else
            runSaveOrLoad();
        preferLoad = !preferLoad;
    }

    public void stop() {
        stopping = true;
        loadQueue.clear();
        for (UUID owner : Set.copyOf(saveQueue)) {
            Entry entry = entries.get(owner);
            if (entry != null && entry.ready && entry.dirty && server.getPlayerList().getPlayer(owner) == null)
                save(owner, entry);
        }
    }

    private void transferToPlayer(UUID owner, ServerPlayer player) {
        Entry entry = entries.remove(owner);
        loadQueue.remove(owner);
        saveQueue.remove(owner);
        if (entry == null || !entry.ready)
            return;
        copy(entry.inventory, player.getEnderChestInventory());
        tracked.add(owner);
    }

    private boolean runLoad() {
        int candidates = loadQueue.size();
        while (candidates-- > 0 && queueChecksRemaining-- > 0) {
            UUID owner = poll(loadQueue);
            if (owner == null)
                return false;
            Entry entry = entries.get(owner);
            if (entry == null || entry.ready || transitioning.contains(owner))
                continue;
            if (server.getPlayerList().getPlayer(owner) != null)
                continue;
            if (ticks - entry.lastRequest > FAILED_ENTRY_TTL) {
                entries.remove(owner);
                continue;
            }
            if (ticks < entry.retryAt) {
                loadQueue.add(owner);
                continue;
            }
            load(owner, entry);
            return true;
        }
        return false;
    }

    private boolean runSave() {
        int candidates = saveQueue.size();
        while (candidates-- > 0 && queueChecksRemaining-- > 0) {
            UUID owner = poll(saveQueue);
            if (owner == null)
                return false;
            Entry entry = entries.get(owner);
            if (entry == null || !entry.ready || !entry.dirty)
                continue;
            if (server.getPlayerList().getPlayer(owner) != null || transitioning.contains(owner)) {
                saveQueue.add(owner);
                continue;
            }
            if (ticks < Math.max(entry.saveAt, entry.retryAt)) {
                saveQueue.add(owner);
                continue;
            }
            save(owner, entry);
            return true;
        }
        return false;
    }

    private void load(UUID owner, Entry entry) {
        try {
            LoadedPlayerData loaded = readPlayerData(owner);
            if (loaded.tag.contains("EnderItems") && !loaded.tag.contains("EnderItems", Tag.TAG_LIST))
                throw new IllegalStateException("EnderItems is not a list");
            validateEnderItems(loaded.tag.getList("EnderItems", Tag.TAG_COMPOUND));
            OfflineInventory inventory = new OfflineInventory(owner);
            inventory.loading = true;
            inventory.fromTag(loaded.tag.getList("EnderItems", Tag.TAG_COMPOUND), server.registryAccess());
            inventory.loading = false;
            entry.inventory = inventory;
            entry.ready = true;
            entry.loadedFromBackup = loaded.fromBackup;
            entry.retryAt = 0;
        } catch (Exception exception) {
            entry.retryAt = ticks + RETRY_DELAY_TICKS;
            loadQueue.add(owner);
            logFailure(owner, entry, "load", exception);
        }
    }

    private void save(UUID owner, Entry entry) {
        try {
            LoadedPlayerData loaded = readPlayerData(owner);
            CompoundTag tag = loaded.tag;
            tag.put("EnderItems", entry.inventory.createTag(server.registryAccess()));
            tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            Path directory = playerDataDirectory();
            Path target = directory.resolve(owner + ".dat");
            Path backup = directory.resolve(owner + ".dat_old");
            if ((entry.loadedFromBackup || loaded.fromBackup) && Files.isRegularFile(target)) {
                Path corrupt = directory.resolve(owner + "_corrupted_" + LocalDateTime.now().format(CORRUPT_TIME) + ".dat");
                Files.move(target, corrupt, StandardCopyOption.REPLACE_EXISTING);
            }
            Path temporary = Files.createTempFile(directory, owner + "-", ".dat");
            try {
                NbtIo.writeCompressed(tag, temporary);
                if (!Util.safeReplaceOrMoveFile(target, temporary, backup, false))
                    throw new IOException("Could not safely replace " + target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            syncIntegratedPlayerData(owner, tag);
            entry.dirty = false;
            entry.loadedFromBackup = false;
            entry.retryAt = 0;
        } catch (Exception exception) {
            entry.retryAt = ticks + RETRY_DELAY_TICKS;
            saveQueue.add(owner);
            logFailure(owner, entry, "save", exception);
        }
    }

    private LoadedPlayerData readPlayerData(UUID owner) throws Exception {
        Path directory = playerDataDirectory();
        Path main = directory.resolve(owner + ".dat");
        Path backup = directory.resolve(owner + ".dat_old");
        Exception mainFailure = null;
        if (Files.isRegularFile(main)) {
            try {
                return new LoadedPlayerData(fix(NbtIo.readCompressed(main, NbtAccounter.unlimitedHeap())), false);
            } catch (Exception exception) {
                mainFailure = exception;
            }
        }
        if (Files.isRegularFile(backup))
            return new LoadedPlayerData(fix(NbtIo.readCompressed(backup, NbtAccounter.unlimitedHeap())), true);
        if (mainFailure != null)
            throw mainFailure;
        throw new IllegalStateException("No player data exists for " + owner);
    }

    private CompoundTag fix(CompoundTag tag) {
        int version = NbtUtils.getDataVersion(tag, -1);
        return DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), tag, version);
    }

    private void validateEnderItems(ListTag items) {
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getByte("Slot") & 255;
            if (slot >= 27 || ItemStack.parse(server.registryAccess(), item).isEmpty())
                throw new IllegalStateException("Invalid EnderItems entry at index " + i);
        }
    }

    private Path playerDataDirectory() throws Exception {
        Path path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Files.createDirectories(path);
        return path;
    }

    private void syncIntegratedPlayerData(UUID owner, CompoundTag tag) {
        CompoundTag loaded = server.getWorldData().getLoadedPlayerTag();
        if (loaded == null || !loaded.hasUUID("UUID") || !owner.equals(loaded.getUUID("UUID")))
            return;
        if (server.getPlayerList() instanceof IntegratedPlayerDataAccessor accessor)
            accessor.shhs$setPlayerData(tag.copy());
    }

    private void logFailure(UUID owner, Entry entry, String operation, Exception exception) {
        if (ticks < entry.nextErrorLog)
            return;
        entry.nextErrorLog = ticks + ERROR_LOG_INTERVAL_TICKS;
        LOGGER.warn("Failed to {} offline ender chest for {}", operation, owner, exception);
    }

    private void runLoadOrSave() {
        if (!runLoad())
            runSave();
    }

    private void runSaveOrLoad() {
        if (!runSave())
            runLoad();
    }

    private static @Nullable UUID poll(LinkedHashSet<UUID> queue) {
        Iterator<UUID> iterator = queue.iterator();
        if (!iterator.hasNext())
            return null;
        UUID result = iterator.next();
        iterator.remove();
        return result;
    }

    private static void copy(PlayerEnderChestContainer source, PlayerEnderChestContainer target) {
        for (int slot = 0; slot < source.getContainerSize(); slot++)
            target.setItem(slot, source.getItem(slot).copy());
        target.setChanged();
    }

    private final class OfflineInventory extends PlayerEnderChestContainer {
        private final UUID owner;
        private boolean loading;

        private OfflineInventory(UUID owner) {
            this.owner = owner;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading)
                markDirty(owner);
        }
    }

    private static final class Entry {
        private PlayerEnderChestContainer inventory;
        private boolean ready;
        private boolean dirty;
        private boolean loadedFromBackup;
        private long saveAt;
        private long retryAt;
        private long lastRequest;
        private long nextErrorLog;
    }

    private record LoadedPlayerData(CompoundTag tag, boolean fromBackup) {
    }
}
