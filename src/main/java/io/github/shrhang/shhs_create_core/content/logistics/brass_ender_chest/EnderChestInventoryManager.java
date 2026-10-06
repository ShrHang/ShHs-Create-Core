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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-scoped bridge between online and persisted ender chest inventories. */
public final class EnderChestInventoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAVE_DELAY_TICKS = 200;
    private static final int RETRY_DELAY_TICKS = 200;
    private static final int MAX_QUEUE_CHECKS_PER_TICK = 32;
    private static final int ERROR_LOG_INTERVAL_TICKS = 1200;
    private static final int CACHE_TTL = 12000;
    private static final int MAX_CLEANUP_CHECKS_PER_TICK = 32;
    private static final Map<MinecraftServer, EnderChestInventoryManager> INSTANCES = new HashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, Long> lastAccess = new HashMap<>();
    private final LinkedHashSet<UUID> cleanupQueue = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> loadQueue = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> saveQueue = new LinkedHashSet<>();
    private final Map<UUID, ServerPlayer> pendingLogins = new HashMap<>();
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
            touch(owner);
            return player.getEnderChestInventory();
        }
        Entry entry = entries.get(owner);
        if (entry != null && entry.ready) {
            touch(owner);
            return entry.inventory;
        }
        if (requestLoad && !stopping) {
            touch(owner);
            if (entry == null) {
                entry = new Entry();
                entries.put(owner, entry);
            }
            if (ticks >= entry.retryAt)
                loadQueue.add(owner);
        }
        return null;
    }

    public boolean isAvailable(UUID owner) {
        Entry entry = entries.get(owner);
        return server.getPlayerList().getPlayer(owner) != null || entry != null && entry.ready;
    }

    public boolean hasLoadFailed(UUID owner) {
        Entry entry = entries.get(owner);
        return entry != null && !entry.ready && entry.retryAt > 0;
    }

    public void markDirty(UUID owner) {
        Entry entry = entries.get(owner);
        if (entry == null || !entry.ready
                || server.getPlayerList().getPlayer(owner) != null && !pendingLogins.containsKey(owner))
            return;
        if (!entry.dirty) {
            entry.dirty = true;
            entry.saveAt = ticks + SAVE_DELAY_TICKS;
        }
        saveQueue.add(owner);
    }

    public void onPlayerLoading(ServerPlayer player) {
        UUID owner = player.getUUID();
        Entry entry = entries.get(owner);
        if (entry == null || !entry.ready)
            return;
        copy(entry.inventory, player.getEnderChestInventory());
        // Keep one live inventory and its pending save until login is confirmed.
        entry.inventory = player.getEnderChestInventory();
        pendingLogins.put(owner, player);
        loadQueue.remove(owner);
    }

    public void onPlayerLoggedIn(ServerPlayer player) {
        UUID owner = player.getUUID();
        Entry entry = entries.get(owner);
        if (entry != null && entry.ready && entry.inventory != player.getEnderChestInventory())
            copy(entry.inventory, player.getEnderChestInventory());
        releaseOffline(owner);
        if (lastAccess.containsKey(owner))
            touch(owner);
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        UUID owner = player.getUUID();
        pendingLogins.remove(owner);
        if (!lastAccess.containsKey(owner))
            return;
        Entry entry = entries.computeIfAbsent(owner, ignored -> new Entry());
        entry.inventory = player.getEnderChestInventory();
        entry.ready = true;
        touch(owner);
        loadQueue.remove(owner);
    }

    public void tick() {
        ticks++;
        finishPendingLogins();
        cleanCache();
        queueChecksRemaining = MAX_QUEUE_CHECKS_PER_TICK;
        if (preferLoad)
            runLoadOrSave();
        else
            runSaveOrLoad();
        preferLoad = !preferLoad;
    }

    public void stop() {
        stopping = true;
        finishPendingLogins();
        loadQueue.clear();
        for (UUID owner : Set.copyOf(saveQueue)) {
            Entry entry = entries.get(owner);
            if (entry != null && entry.ready && entry.dirty && server.getPlayerList().getPlayer(owner) == null
                    && !save(owner, entry))
                LOGGER.error("Unsaved offline ender chest at shutdown: {}", owner);
        }
    }

    private void releaseOffline(UUID owner) {
        entries.remove(owner);
        loadQueue.remove(owner);
        saveQueue.remove(owner);
        pendingLogins.remove(owner);
    }

    private void touch(UUID owner) {
        lastAccess.put(owner, ticks);
        cleanupQueue.add(owner);
    }

    private void finishPendingLogins() {
        // Vanilla loads and joins synchronously. At tick end an unfinished join has failed.
        Iterator<Map.Entry<UUID, ServerPlayer>> iterator = pendingLogins.entrySet().iterator();
        for (int checks = 0; iterator.hasNext() && checks < MAX_CLEANUP_CHECKS_PER_TICK; checks++) {
            Map.Entry<UUID, ServerPlayer> pending = iterator.next();
            UUID owner = pending.getKey();
            if (server.getPlayerList().getPlayer(owner) == pending.getValue()) {
                entries.remove(owner);
                saveQueue.remove(owner);
                loadQueue.remove(owner);
            }
            // A failed join leaves the inventory and dirty flag in the offline cache.
            iterator.remove();
        }
    }

    private void cleanCache() {
        int checks = Math.min(MAX_CLEANUP_CHECKS_PER_TICK, cleanupQueue.size());
        while (checks-- > 0) {
            UUID owner = poll(cleanupQueue);
            Entry entry = entries.get(owner);
            if (ticks - lastAccess.get(owner) >= CACHE_TTL
                    && (entry == null || !entry.dirty) && !pendingLogins.containsKey(owner)) {
                releaseOffline(owner);
                lastAccess.remove(owner);
            } else {
                cleanupQueue.add(owner);
            }
        }
    }

    private boolean runLoad() {
        int candidates = loadQueue.size();
        while (candidates-- > 0 && queueChecksRemaining-- > 0) {
            UUID owner = poll(loadQueue);
            if (owner == null)
                return false;
            Entry entry = entries.get(owner);
            if (entry == null || entry.ready)
                continue;
            if (server.getPlayerList().getPlayer(owner) != null)
                continue;
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
            if (server.getPlayerList().getPlayer(owner) != null || pendingLogins.containsKey(owner)) {
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
            PlayerEnderChestContainer inventory = new PlayerEnderChestContainer();
            inventory.fromTag(loaded.tag.getList("EnderItems", Tag.TAG_COMPOUND), server.registryAccess());
            entry.inventory = inventory;
            entry.ready = true;
            entry.retryAt = 0;
        } catch (Exception exception) {
            entry.retryAt = ticks + RETRY_DELAY_TICKS;
            loadQueue.add(owner);
            logFailure(owner, entry, "load", exception);
        }
    }

    private boolean save(UUID owner, Entry entry) {
        try {
            LoadedPlayerData loaded = readPlayerData(owner);
            CompoundTag tag = loaded.tag;
            tag.put("EnderItems", entry.inventory.createTag(server.registryAccess()));
            tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            Path directory = playerDataDirectory();
            Path target = directory.resolve(owner + ".dat");
            Path backup = directory.resolve(owner + ".dat_old");
            Path temporary = Files.createTempFile(directory, owner + "-", ".dat");
            try {
                NbtIo.writeCompressed(tag, temporary);
                if (loaded.fromBackup && Files.isRegularFile(target)) {
                    // Preserve both the rejected main file and the known-good backup.
                    Path corrupt = Files.createTempFile(directory, owner + "_corrupted_", ".dat");
                    Files.move(target, corrupt, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!Util.safeReplaceOrMoveFile(target, temporary, backup, false))
                    throw new IOException("Could not safely replace " + target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            syncIntegratedPlayerData(owner, tag);
            entry.dirty = false;
            entry.retryAt = 0;
            return true;
        } catch (Exception exception) {
            entry.retryAt = ticks + RETRY_DELAY_TICKS;
            saveQueue.add(owner);
            logFailure(owner, entry, "save", exception);
            return false;
        }
    }

    private LoadedPlayerData readPlayerData(UUID owner) throws Exception {
        Path directory = playerDataDirectory();
        Path main = directory.resolve(owner + ".dat");
        Path backup = directory.resolve(owner + ".dat_old");
        Exception mainFailure = null;
        if (Files.isRegularFile(main)) {
            try {
                return new LoadedPlayerData(readValidated(main), false);
            } catch (Exception exception) {
                mainFailure = exception;
            }
        }
        if (Files.isRegularFile(backup)) {
            try {
                return new LoadedPlayerData(readValidated(backup), true);
            } catch (Exception backupFailure) {
                if (mainFailure != null)
                    backupFailure.addSuppressed(mainFailure);
                throw backupFailure;
            }
        }
        if (mainFailure != null)
            throw mainFailure;
        throw new IllegalStateException("No player data exists for " + owner);
    }

    private CompoundTag readValidated(Path path) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        // Reject malformed lists before data fixing can normalize away invalid entries.
        validateSlots(tag);
        int version = NbtUtils.getDataVersion(tag, -1);
        tag = DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), tag, version);
        validateEnderItems(tag);
        return tag;
    }

    private void validateEnderItems(CompoundTag tag) throws IOException {
        ListTag items = validateSlots(tag);
        for (int i = 0; i < items.size(); i++) {
            if (ItemStack.parse(server.registryAccess(), items.getCompound(i)).filter(stack -> !stack.isEmpty()).isEmpty())
                throw new IOException("Invalid EnderItems stack at index " + i);
        }
    }

    private static ListTag validateSlots(CompoundTag tag) throws IOException {
        if (!tag.contains("EnderItems"))
            return new ListTag();
        if (!(tag.get("EnderItems") instanceof ListTag items)
                || !items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND)
            throw new IOException("EnderItems is not a compound list");
        boolean[] occupied = new boolean[27];
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getByte("Slot") & 255;
            if (!item.contains("Slot", Tag.TAG_BYTE) || slot >= occupied.length || occupied[slot])
                throw new IOException("Invalid or duplicate EnderItems entry at index " + i);
            occupied[slot] = true;
        }
        return items;
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

    private static final class Entry {
        private PlayerEnderChestContainer inventory;
        private boolean ready;
        private boolean dirty;
        private long saveAt;
        private long retryAt;
        private long nextErrorLog;
    }

    private record LoadedPlayerData(CompoundTag tag, boolean fromBackup) {
    }
}
