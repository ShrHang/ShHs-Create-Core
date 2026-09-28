package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;

public final class TerminalPackets {
    public static final int DEPOSIT = 0, SUBMIT = 1, CLAIM = 2, END = 3, CLEAR_CRAFT = 4, FILL = 5,
            ADDRESS = 6, LOCAL_CLICK = 7, CLAIM_READY = 8, END_INCOMPLETE = 9;
    private TerminalPackets() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Action.TYPE, Action.CODEC, TerminalPackets::handle);
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, TerminalPackets::handleSnapshot);
    }

    public record Action(int menu, UUID session, int operation, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ShHsCreateCore.rl("terminal_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = new StreamCodec<>() {
            public Action decode(RegistryFriendlyByteBuf buffer) {
                return new Action(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), Objects.requireNonNull(buffer.readNbt()));
            }
            public void encode(RegistryFriendlyByteBuf buffer, Action value) {
                buffer.writeVarInt(value.menu); buffer.writeUUID(value.session); buffer.writeVarInt(value.operation); buffer.writeNbt(value.data);
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(int menu, UUID session, long revision, int part, boolean last, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ShHsCreateCore.rl("terminal_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(RegistryFriendlyByteBuf buffer) {
                return new Snapshot(buffer.readVarInt(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt(), buffer.readBoolean(), Objects.requireNonNull(buffer.readNbt()));
            }
            public void encode(RegistryFriendlyByteBuf buffer, Snapshot value) {
                buffer.writeVarInt(value.menu); buffer.writeUUID(value.session); buffer.writeVarLong(value.revision);
                buffer.writeVarInt(value.part); buffer.writeBoolean(value.last); buffer.writeNbt(value.data);
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void send(ServerPlayer player, int menu, UUID session, long revision, int part, boolean last, CompoundTag tag) {
        PacketDistributor.sendToPlayer(player, new Snapshot(menu, session, revision, part, last, tag));
    }

    public static void request(DimensionLogisticsTerminalMenu menu, int action, CompoundTag data) {
        PacketDistributor.sendToServer(new Action(menu.containerId, menu.session, action, data));
    }

    private static void handle(Action packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.containerMenu.containerId != packet.menu()) return;
        if (packet.operation() == ADDRESS && player.containerMenu instanceof DimensionParcelStationMenu menu) {
            if (!menu.stillValid(player) || !menu.session.equals(packet.session()) || !packet.data().hasUUID("Network")) return;
            if (player.level().getBlockEntity(menu.getPos()) instanceof DimensionParcelStationBlockEntity station) {
                station.setReceiveAddress(packet.data().getUUID("Network"), packet.data().getString("Address"), player);
                menu.sendRoutes(player);
            }
            return;
        }
        if (!(player.containerMenu instanceof DimensionLogisticsTerminalMenu menu) || !menu.session.equals(packet.session()) || !menu.stillValid(player)) return;
        CompoundTag data = packet.data();
        switch (packet.operation()) {
            case DEPOSIT -> menu.depositCursor();
            case LOCAL_CLICK -> menu.clickLocal(ItemStack.parseOptional(player.registryAccess(), data.getCompound("Stack")),
                    data.getInt("Button"), data.getBoolean("QuickMove"));
            case CLEAR_CRAFT -> menu.clearCrafting();
            case FILL -> {
                ResourceLocation id = ResourceLocation.tryParse(data.getString("Recipe"));
                if (id != null) menu.fillRecipe(id, data.getBoolean("Max"));
            }
            case SUBMIT -> {
                var net = menu.network();
                if (net == null || !data.hasUUID("Submission")) return;
                ListTag list = data.getList("Items", Tag.TAG_COMPOUND);
                if (list.size() > TerminalData.MAX_ORDER_LINES) return;
                List<TerminalStock.Entry> entries = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) entries.add(TerminalData.entry(list.getCompound(i), player.registryAccess()));
                if (TerminalOrders.get(player.server).submit(player, net, data.getUUID("Submission"), entries)) {
                    menu.acceptedSubmission = data.getUUID("Submission");
                } else player.displayClientMessage(TerminalData.text("submit_failed"), true);
            }
            case CLAIM, END -> {
                if (data.hasUUID("Order")) TerminalOrders.get(player.server).claim(player, data.getUUID("Order"), packet.operation() == END);
            }
            case CLAIM_READY -> TerminalOrders.get(player.server).claimReady(player);
            case END_INCOMPLETE -> TerminalOrders.get(player.server).endIncomplete(player);
            default -> { return; }
        }
        menu.sendSnapshot();
    }

    private static void handleSnapshot(Snapshot packet, IPayloadContext context) {
        var current = context.player().containerMenu;
        if (current.containerId != packet.menu()) return;
        if (current instanceof DimensionLogisticsTerminalMenu menu && menu.session.equals(packet.session()))
            menu.receive(packet.revision(), packet.part(), packet.last(), packet.data());
        else if (current instanceof DimensionParcelStationMenu menu) menu.receiveRoutes(packet.session(), packet.data());
    }
}
