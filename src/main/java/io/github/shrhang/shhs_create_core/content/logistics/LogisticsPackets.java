package io.github.shrhang.shhs_create_core.content.logistics;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationMenu;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationPackets;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.DimensionLogisticsTerminalMenu;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.Objects;
import java.util.UUID;

/** Shared transport for the two logistics menus; retains the original wire format. */
public final class LogisticsPackets {
    private LogisticsPackets() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Action.TYPE, Action.CODEC, LogisticsPackets::handle);
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, LogisticsPackets::handleSnapshot);
    }

    public record Action(int menu, UUID session, int operation, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ShHsCreateCore.rl("terminal_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = new StreamCodec<>() {
            public Action decode(RegistryFriendlyByteBuf buffer) {
                return new Action(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(),
                        Objects.requireNonNull(buffer.readNbt()));
            }

            public void encode(RegistryFriendlyByteBuf buffer, Action value) {
                buffer.writeVarInt(value.menu);
                buffer.writeUUID(value.session);
                buffer.writeVarInt(value.operation);
                buffer.writeNbt(value.data);
            }
        };

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Snapshot(int menu, UUID session, long revision, int part, boolean last, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ShHsCreateCore.rl("terminal_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(RegistryFriendlyByteBuf buffer) {
                return new Snapshot(buffer.readVarInt(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt(),
                        buffer.readBoolean(), Objects.requireNonNull(buffer.readNbt()));
            }

            public void encode(RegistryFriendlyByteBuf buffer, Snapshot value) {
                buffer.writeVarInt(value.menu);
                buffer.writeUUID(value.session);
                buffer.writeVarLong(value.revision);
                buffer.writeVarInt(value.part);
                buffer.writeBoolean(value.last);
                buffer.writeNbt(value.data);
            }
        };

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void send(ServerPlayer player, int menu, UUID session, long revision, int part, boolean last, CompoundTag tag) {
        PacketDistributor.sendToPlayer(player, new Snapshot(menu, session, revision, part, last, tag));
    }

    private static void handle(Action packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || player.containerMenu.containerId != packet.menu())
            return;
        if (player.containerMenu instanceof DimensionParcelStationMenu menu) {
            if (menu.stillValid(player) && menu.session.equals(packet.session()))
                DimensionParcelStationPackets.handleAddress(packet, player, menu);
        } else if (player.containerMenu instanceof DimensionLogisticsTerminalMenu menu
                && menu.stillValid(player) && menu.session.equals(packet.session())) {
            TerminalPackets.handle(packet, player, menu);
        }
    }

    private static void handleSnapshot(Snapshot packet, IPayloadContext context) {
        var current = context.player().containerMenu;
        if (current.containerId != packet.menu())
            return;
        if (current instanceof DimensionLogisticsTerminalMenu menu && menu.session.equals(packet.session()))
            menu.receive(packet.revision(), packet.part(), packet.last(), packet.data());
        else if (current instanceof DimensionParcelStationMenu menu)
            menu.receiveRoutes(packet.session(), packet.data());
    }
}
