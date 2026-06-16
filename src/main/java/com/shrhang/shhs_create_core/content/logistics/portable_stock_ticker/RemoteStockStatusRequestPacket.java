package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public record RemoteStockStatusRequestPacket(UUID networkId) implements CustomPacketPayload {
    public static final Type<RemoteStockStatusRequestPacket> TYPE = new Type<>(rl("portable_stock_status_request"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteStockStatusRequestPacket> STREAM_CODEC =
            UUID_CODEC.map(RemoteStockStatusRequestPacket::new, RemoteStockStatusRequestPacket::networkId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteStockStatusRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;

        UUID networkId = packet.networkId();
        PortableStockTickerClientData.NetworkStatus status = PortableStockTickerItem.getNetworkStatus(player, networkId);
        if (status == PortableStockTickerClientData.NetworkStatus.UNKNOWN) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new RemoteStockStatusResponsePacket(networkId, status));
    }
}
