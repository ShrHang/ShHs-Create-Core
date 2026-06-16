package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public record RemotePackageOrderRequestPacket(UUID networkId, PackageOrderWithCrafts order, String address) implements CustomPacketPayload {
    public static final Type<RemotePackageOrderRequestPacket> TYPE = new Type<>(rl("portable_package_order_request"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, RemotePackageOrderRequestPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, RemotePackageOrderRequestPacket::networkId,
            PackageOrderWithCrafts.STREAM_CODEC, RemotePackageOrderRequestPacket::order,
            ByteBufCodecs.STRING_UTF8, RemotePackageOrderRequestPacket::address,
            RemotePackageOrderRequestPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemotePackageOrderRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;
        UUID networkId = packet.networkId();
        if (!Create.LOGISTICS.mayInteract(networkId, player))
            return;
        if (packet.order().isEmpty())
            return;

        boolean success = LogisticsManager.broadcastPackageRequest(networkId, RequestType.PLAYER, packet.order(), null, packet.address());
        if (!success)
            return;

        AllSoundEvents.STOCK_TICKER_REQUEST.playOnServer(player.level(), player.blockPosition());
        AllAdvancements.STOCK_TICKER.awardTo(player);
    }
}
