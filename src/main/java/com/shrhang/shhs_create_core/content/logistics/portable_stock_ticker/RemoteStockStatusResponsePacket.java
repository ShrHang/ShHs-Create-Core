package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public record RemoteStockStatusResponsePacket(UUID networkId,
                                              PortableStockTickerClientData.NetworkStatus status) implements CustomPacketPayload {
    public static final Type<RemoteStockStatusResponsePacket> TYPE = new Type<>(rl("portable_stock_status"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    private static final StreamCodec<RegistryFriendlyByteBuf, PortableStockTickerClientData.NetworkStatus> STATUS_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> PortableStockTickerClientData.NetworkStatus.values()[ordinal],
                    PortableStockTickerClientData.NetworkStatus::ordinal).cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteStockStatusResponsePacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, RemoteStockStatusResponsePacket::networkId,
            STATUS_CODEC, RemoteStockStatusResponsePacket::status,
            RemoteStockStatusResponsePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteStockStatusResponsePacket packet, IPayloadContext context) {
        PortableStockTickerClientData.receiveStatus(packet.networkId(), packet.status());
    }
}
