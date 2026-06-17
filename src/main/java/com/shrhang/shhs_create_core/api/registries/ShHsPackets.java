package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ShHsPackets {
    private static final String VERSION = "0.0.3";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(StockInventoryPacket.StockRequestPacket.TYPE, StockInventoryPacket.StockRequestPacket.STREAM_CODEC, StockInventoryPacket.StockRequestPacket::handle);
        registrar.playToServer(StockStatusPacket.StockStatusRequestPacket.TYPE, StockStatusPacket.StockStatusRequestPacket.STREAM_CODEC, StockStatusPacket.StockStatusRequestPacket::handle);
        registrar.playToServer(PackageOrderPacket.RemotePackageOrderPacket.TYPE, PackageOrderPacket.RemotePackageOrderPacket.STREAM_CODEC, PackageOrderPacket.RemotePackageOrderPacket::handle);
        registrar.playToClient(StockInventoryPacket.StockResponsePacket.TYPE, StockInventoryPacket.StockResponsePacket.STREAM_CODEC, StockInventoryPacket.StockResponsePacket::handle);
        registrar.playToClient(StockStatusPacket.StockStatusResponsePacket.TYPE, StockStatusPacket.StockStatusResponsePacket.STREAM_CODEC, StockStatusPacket.StockStatusResponsePacket::handle);
    }
}
