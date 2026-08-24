package io.github.shrhang.shhs_create_core.content.event;

import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.OpenPortableStockTickerPacket;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerClientData;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerOutlineHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import static io.github.shrhang.shhs_create_core.content.registries.ShHsKeys.OPEN_PORTABLE_STOCK_TICKER;

public class ClientEvents {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTickPost);
    }

    public static void onClientLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event) {
        PortableStockTickerClientData.clear();
    }

    public static void onClientLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        PortableStockTickerClientData.clear();
    }

    public static void onClientTickPost(final ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        PortableStockTickerClientData.pruneExpired(minecraft.level.getGameTime());
        SprayerOutlineHandler.tick();

        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        while (OPEN_PORTABLE_STOCK_TICKER.getKeybind().consumeClick()) {
            PacketDistributor.sendToServer(OpenPortableStockTickerPacket.INSTANCE);
        }
    }
}