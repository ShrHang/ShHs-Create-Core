package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class OpenDimensionLogisticsTerminalPacket implements CustomPacketPayload {
    public static final OpenDimensionLogisticsTerminalPacket INSTANCE =
            new OpenDimensionLogisticsTerminalPacket();
    public static final Type<OpenDimensionLogisticsTerminalPacket> TYPE =
            new Type<>(ShHsCreateCore.rl("open_dimension_logistics_terminal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDimensionLogisticsTerminalPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    private OpenDimensionLogisticsTerminalPacket() {
    }

    public static void handle(OpenDimensionLogisticsTerminalPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null)
            DimensionLogisticsTerminalMenu.open(player, net);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
