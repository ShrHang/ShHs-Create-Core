package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.logistics.LogisticsPackets.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.UUID;

public final class DimensionParcelStationPackets {
    private static final int ADDRESS = 6;
    private DimensionParcelStationPackets() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Toggle.TYPE, Toggle.STREAM_CODEC, Toggle::handle);
    }

    public static void requestAddress(DimensionParcelStationMenu menu, UUID network, String address) {
        CompoundTag data = new CompoundTag();
        data.putUUID("Network", network);
        data.putString("Address", address);
        PacketDistributor.sendToServer(new Action(menu.containerId, menu.session, ADDRESS, data));
    }

    public static void handleAddress(Action packet, ServerPlayer player, DimensionParcelStationMenu menu) {
        if (packet.operation() != ADDRESS || !packet.data().hasUUID("Network"))
            return;
        if (player.level().getBlockEntity(menu.getPos()) instanceof DimensionParcelStationBlockEntity station) {
            station.setReceiveAddress(packet.data().getUUID("Network"), packet.data().getString("Address"), player);
            menu.sendRoutes(player);
        }
    }

    public record Toggle(BlockPos pos, int channel) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE =
                new Type<>(ShHsCreateCore.rl("dimension_parcel_station_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toggle> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Toggle::pos,
                        ByteBufCodecs.VAR_INT, Toggle::channel,
                        Toggle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Toggle packet, IPayloadContext context) {
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
}
