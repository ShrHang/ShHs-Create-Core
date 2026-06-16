package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public record RemoteStockResponsePacket(UUID networkId, boolean lastPacket, List<BigItemStack> items) implements CustomPacketPayload {
    public static final Type<RemoteStockResponsePacket> TYPE = new Type<>(rl("portable_stock_response"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteStockResponsePacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, RemoteStockResponsePacket::networkId,
            ByteBufCodecs.BOOL, RemoteStockResponsePacket::lastPacket,
            CatnipStreamCodecBuilders.list(BigItemStack.STREAM_CODEC), RemoteStockResponsePacket::items,
            RemoteStockResponsePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteStockResponsePacket packet, IPayloadContext context) {
        PortableStockTickerClientData.receive(packet.networkId(), packet.items(), packet.lastPacket());
    }
}
