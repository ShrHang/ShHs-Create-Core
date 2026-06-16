package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public record PortableStockStatusRequestPacket(UUID networkId) implements CustomPacketPayload {
    public static final Type<PortableStockStatusRequestPacket> TYPE = new Type<>(rl("portable_stock_status_request"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableStockStatusRequestPacket> STREAM_CODEC =
            UUID_CODEC.map(PortableStockStatusRequestPacket::new, PortableStockStatusRequestPacket::networkId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortableStockStatusRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;

        UUID networkId = packet.networkId();
        LogisticsNetwork network = Create.LOGISTICS.logisticsNetworks.get(networkId);
        PortableStockTickerClientData.NetworkStatus status = PortableStockTickerClientData.NetworkStatus.AVAILABLE;
        if (network == null) {
            status = PortableStockTickerClientData.NetworkStatus.NO_NETWORK;
        } else if (!Create.LOGISTICS.mayInteract(networkId, player)) {
            return;
        } else if (LogisticallyLinkedBehaviour.getAllPresent(networkId, false).isEmpty()) {
            status = PortableStockTickerClientData.NetworkStatus.UNLOADED;
        }

        PacketDistributor.sendToPlayer(player, new PortableStockStatusPacket(networkId, status));
    }
}
