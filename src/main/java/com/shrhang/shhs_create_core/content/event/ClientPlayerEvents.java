package com.shrhang.shhs_create_core.content.event;

import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerClientData;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public class ClientPlayerEvents {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerEvents::onClientLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientPlayerEvents::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientPlayerEvents::onClientTickPost);
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
    }
}
