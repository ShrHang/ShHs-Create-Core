package io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BrassEnderChestEvents {
    private BrassEnderChestEvents() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BrassEnderChestEvents::onPlayerLoading);
        NeoForge.EVENT_BUS.addListener(BrassEnderChestEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(BrassEnderChestEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(BrassEnderChestEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(BrassEnderChestEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(BrassEnderChestEvents::onServerStopped);
    }

    private static void onPlayerLoading(PlayerEvent.LoadFromFile event) {
        if (event.getEntity() instanceof ServerPlayer player)
            EnderChestInventoryManager.get(player.server).onPlayerLoading(player);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            EnderChestInventoryManager.get(player.server).onPlayerLoggedIn(player);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            EnderChestInventoryManager.get(player.server).onPlayerLoggedOut(player);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        EnderChestInventoryManager.get(event.getServer()).tick();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        EnderChestInventoryManager.get(event.getServer()).stop();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        EnderChestInventoryManager.remove(event.getServer());
    }
}
