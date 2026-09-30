package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.logistics.LogisticsPackets.Action;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TerminalPackets {
    public static final int DEPOSIT = 0;
    public static final int SUBMIT = 1;
    public static final int END = 3;
    public static final int FILL = 5;
    public static final int LOCAL_CLICK = 7;
    public static final int CLAIM_READY = 8;
    public static final int END_INCOMPLETE = 9;

    private TerminalPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Open.TYPE,
                Open.STREAM_CODEC,
                Open::handle);
    }

    private static void request(DimensionLogisticsTerminalMenu menu, int action, CompoundTag data) {
        PacketDistributor.sendToServer(new Action(menu.containerId, menu.session, action, data));
    }

    public static void requestFill(DimensionLogisticsTerminalMenu menu, ResourceLocation recipe, boolean maximum) {
        CompoundTag data = new CompoundTag();
        data.putString("Recipe", recipe.toString());
        data.putBoolean("Max", maximum);
        request(menu, FILL, data);
    }

    static void requestLocalClick(DimensionLogisticsTerminalMenu menu, ItemStack stack, int button, boolean quickMove) {
        CompoundTag data = new CompoundTag();
        data.put("Stack", stack.save(menu.player.registryAccess()));
        data.putInt("Button", button);
        data.putBoolean("QuickMove", quickMove);
        request(menu, LOCAL_CLICK, data);
    }

    static void requestSubmit(DimensionLogisticsTerminalMenu menu, UUID submission,
                              List<TerminalStock.Entry> entries) {
        CompoundTag data = new CompoundTag();
        data.putUUID("Submission", submission);
        ListTag items = new ListTag();
        entries.forEach(entry -> items.add(TerminalData.entry(entry, menu.player.registryAccess())));
        data.put("Items", items);
        request(menu, SUBMIT, data);
    }

    static void requestOrder(DimensionLogisticsTerminalMenu menu, int operation, UUID order) {
        CompoundTag data = new CompoundTag();
        if (order != null)
            data.putUUID("Order", order);
        request(menu, operation, data);
    }

    static void requestDeposit(DimensionLogisticsTerminalMenu menu) {
        request(menu, DEPOSIT, new CompoundTag());
    }

    public static void handle(Action packet, ServerPlayer player, DimensionLogisticsTerminalMenu menu) {
        CompoundTag data = packet.data();
        switch (packet.operation()) {
            case DEPOSIT -> menu.depositCursor();
            case LOCAL_CLICK -> menu.clickLocal(ItemStack.parseOptional(player.registryAccess(), data.getCompound("Stack")),
                    data.getInt("Button"), data.getBoolean("QuickMove"));
            case FILL -> {
                ResourceLocation id = ResourceLocation.tryParse(data.getString("Recipe"));
                if (id != null)
                    menu.fillRecipe(id, data.getBoolean("Max"));
            }
            case SUBMIT -> {
                var net = menu.network();
                if (net == null || !data.hasUUID("Submission"))
                    return;
                ListTag list = data.getList("Items", Tag.TAG_COMPOUND);
                if (list.size() > TerminalData.MAX_ORDER_LINES)
                    return;
                List<TerminalStock.Entry> entries = new ArrayList<>();
                for (int i = 0; i < list.size(); i++)
                    entries.add(TerminalData.entry(list.getCompound(i), player.registryAccess()));
                if (TerminalOrders.get(player.server).submit(player, net, data.getUUID("Submission"), entries)) {
                    menu.acceptedSubmission = data.getUUID("Submission");
                } else {
                    player.displayClientMessage(TerminalData.text("submit_failed"), true);
                }
            }
            case END -> {
                if (data.hasUUID("Order"))
                    TerminalOrders.get(player.server).claim(player, data.getUUID("Order"), true);
            }
            case CLAIM_READY -> TerminalOrders.get(player.server).claimReady(player);
            case END_INCOMPLETE -> TerminalOrders.get(player.server).endIncomplete(player);
            default -> {
                return;
            }
        }
        menu.sendSnapshot();
    }

    public static final class Open implements CustomPacketPayload {
        public static final Open INSTANCE =
                new Open();
        public static final Type<Open> TYPE =
                new Type<>(ShHsCreateCore.rl("open_dimension_logistics_terminal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        private Open() {
        }

        public static void handle(Open packet, IPayloadContext context) {
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
}
