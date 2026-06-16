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

public record PortableStockResponsePacket(UUID networkId, boolean lastPacket, List<BigItemStack> items) implements CustomPacketPayload {
    public static final Type<PortableStockResponsePacket> TYPE = new Type<>(rl("portable_stock_response"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableStockResponsePacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, PortableStockResponsePacket::networkId,
            ByteBufCodecs.BOOL, PortableStockResponsePacket::lastPacket,
            CatnipStreamCodecBuilders.list(BigItemStack.STREAM_CODEC), PortableStockResponsePacket::items,
            PortableStockResponsePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortableStockResponsePacket packet, IPayloadContext context) {
        PortableStockTickerClientData.receive(packet.networkId(), packet.items(), packet.lastPacket());
    }
}
