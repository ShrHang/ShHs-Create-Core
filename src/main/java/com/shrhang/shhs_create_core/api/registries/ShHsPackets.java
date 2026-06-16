package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemoteStockRequestPacket;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemoteStockResponsePacket;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemotePackageOrderRequestPacket;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemoteStockStatusResponsePacket;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemoteStockStatusRequestPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ShHsPackets {
    private static final String VERSION = "0.0.3";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(RemoteStockRequestPacket.TYPE, RemoteStockRequestPacket.STREAM_CODEC, RemoteStockRequestPacket::handle);
        registrar.playToServer(RemoteStockStatusRequestPacket.TYPE, RemoteStockStatusRequestPacket.STREAM_CODEC, RemoteStockStatusRequestPacket::handle);
        registrar.playToServer(RemotePackageOrderRequestPacket.TYPE, RemotePackageOrderRequestPacket.STREAM_CODEC, RemotePackageOrderRequestPacket::handle);
        registrar.playToClient(RemoteStockResponsePacket.TYPE, RemoteStockResponsePacket.STREAM_CODEC, RemoteStockResponsePacket::handle);
        registrar.playToClient(RemoteStockStatusResponsePacket.TYPE, RemoteStockStatusResponsePacket.STREAM_CODEC, RemoteStockStatusResponsePacket::handle);
    }
}
