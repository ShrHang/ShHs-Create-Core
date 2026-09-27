package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DimensionParcelStationTogglePacket(BlockPos pos, int channel) implements CustomPacketPayload {
    public static final Type<DimensionParcelStationTogglePacket> TYPE =
            new Type<>(ShHsCreateCore.rl("dimension_parcel_station_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionParcelStationTogglePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DimensionParcelStationTogglePacket::pos,
                    ByteBufCodecs.VAR_INT, DimensionParcelStationTogglePacket::channel,
                    DimensionParcelStationTogglePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DimensionParcelStationTogglePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof DimensionParcelStationMenu menu)
                || !menu.getPos().equals(packet.pos())
                || player.distanceToSqr(packet.pos().getX() + 0.5D, packet.pos().getY() + 0.5D,
                packet.pos().getZ() + 0.5D) > 64.0D)
            return;
        DimensionParcelStationBlockEntity.Channel[] channels = DimensionParcelStationBlockEntity.Channel.values();
        if (packet.channel() < 0 || packet.channel() >= channels.length)
            return;
        if (player.level().getBlockEntity(packet.pos()) instanceof DimensionParcelStationBlockEntity station)
            station.toggle(channels[packet.channel()], player);
    }
}
