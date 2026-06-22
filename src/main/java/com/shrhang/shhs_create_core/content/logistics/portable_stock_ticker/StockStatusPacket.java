package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public class StockStatusPacket {

    /**
     * C2S，请求网络状态。
     */
    public record StockStatusRequestPacket(UUID networkId) implements CustomPacketPayload {
        public static final Type<StockStatusRequestPacket> TYPE = new Type<>(rl("portable_stock_status_request"));
        private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
        public static final StreamCodec<RegistryFriendlyByteBuf, StockStatusRequestPacket> STREAM_CODEC =
                UUID_CODEC.map(StockStatusRequestPacket::new, StockStatusRequestPacket::networkId);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StockStatusRequestPacket packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            UUID networkId = packet.networkId();
            LogisticsNetworkStatus status = LogisticsNetworkStatus.resolve(player, networkId);
            if (status == LogisticsNetworkStatus.INACCESSIBLE) {
                return;
            }

            PacketDistributor.sendToPlayer(player, new StockStatusResponsePacket(networkId, status));
        }
    }

    /**
     * S2C，返回对应网络的状态。
     */
    public record StockStatusResponsePacket(UUID networkId,
                                            LogisticsNetworkStatus status) implements CustomPacketPayload {
        public static final Type<StockStatusResponsePacket> TYPE = new Type<>(rl("portable_stock_status"));
        private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
        private static final StreamCodec<RegistryFriendlyByteBuf, LogisticsNetworkStatus> STATUS_CODEC =
                ByteBufCodecs.VAR_INT.map(ordinal -> LogisticsNetworkStatus.values()[ordinal],
                        LogisticsNetworkStatus::ordinal).cast();
        public static final StreamCodec<RegistryFriendlyByteBuf, StockStatusResponsePacket> STREAM_CODEC = StreamCodec.composite(
                UUID_CODEC, StockStatusResponsePacket::networkId,
                STATUS_CODEC, StockStatusResponsePacket::status,
                StockStatusResponsePacket::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StockStatusResponsePacket packet, IPayloadContext context) {
            PortableStockTickerClientData.receiveStatus(packet.networkId(), packet.status());
        }
    }
}
