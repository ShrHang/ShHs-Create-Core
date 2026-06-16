package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockRequestPacket;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockResponsePacket;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortablePackageOrderRequestPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ShHsPackets {
    private static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(PortableStockRequestPacket.TYPE, PortableStockRequestPacket.STREAM_CODEC,
                PortableStockRequestPacket::handle);
        registrar.playToServer(PortablePackageOrderRequestPacket.TYPE, PortablePackageOrderRequestPacket.STREAM_CODEC,
                PortablePackageOrderRequestPacket::handle);
        registrar.playToClient(PortableStockResponsePacket.TYPE, PortableStockResponsePacket.STREAM_CODEC,
                PortableStockResponsePacket::handle);
    }
}
