package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.rl;

public class OpenPortableStockTickerPacket implements CustomPacketPayload {
    public static final OpenPortableStockTickerPacket INSTANCE = new OpenPortableStockTickerPacket();
    public static final Type<OpenPortableStockTickerPacket> TYPE = new Type<>(rl("open_portable_stock_ticker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPortableStockTickerPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPortableStockTickerPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PortableStockTickerItem.tryToOpenFromInventory(player);
        }
    }
}
