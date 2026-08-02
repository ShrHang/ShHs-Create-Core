package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.OpenPortableStockTickerPacket;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemotePackageOrderPacket;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.StockInventoryPacket;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.StockStatusPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ShHsPackets {
    private static final String VERSION = "0.0.3";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(OpenPortableStockTickerPacket.TYPE, OpenPortableStockTickerPacket.STREAM_CODEC, OpenPortableStockTickerPacket::handle);
        registrar.playToServer(StockInventoryPacket.StockRequestPacket.TYPE, StockInventoryPacket.StockRequestPacket.STREAM_CODEC, StockInventoryPacket.StockRequestPacket::handle);
        registrar.playToServer(StockStatusPacket.StockStatusRequestPacket.TYPE, StockStatusPacket.StockStatusRequestPacket.STREAM_CODEC, StockStatusPacket.StockStatusRequestPacket::handle);
        registrar.playToServer(RemotePackageOrderPacket.TYPE, RemotePackageOrderPacket.STREAM_CODEC, RemotePackageOrderPacket::handle);
        registrar.playToClient(StockInventoryPacket.StockResponsePacket.TYPE, StockInventoryPacket.StockResponsePacket.STREAM_CODEC, StockInventoryPacket.StockResponsePacket::handle);
        registrar.playToClient(StockStatusPacket.StockStatusResponsePacket.TYPE, StockStatusPacket.StockStatusResponsePacket.STREAM_CODEC, StockStatusPacket.StockStatusResponsePacket::handle);
    }
}
