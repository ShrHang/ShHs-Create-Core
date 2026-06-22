package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.shrhang.shhs_create_core.ShHsCreateCore.rl;

public class StockInventoryPacket {

    /**
     * C2S，请求网络库存。
     */
    public record StockRequestPacket(UUID networkId) implements CustomPacketPayload {
        public static final Type<StockRequestPacket> TYPE = new Type<>(rl("portable_stock_request"));
        private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
        public static final StreamCodec<RegistryFriendlyByteBuf, StockRequestPacket> STREAM_CODEC =
                UUID_CODEC.map(StockRequestPacket::new, StockRequestPacket::networkId);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StockRequestPacket packet, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            UUID networkId = packet.networkId();
            if (!Create.LOGISTICS.mayInteract(networkId, player))
                return;

            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(networkId, false);
            divideAndSendTo(player, networkId, summary);
        }

        private static void divideAndSendTo(ServerPlayer player, UUID networkId, InventorySummary summary) {
            List<BigItemStack> stacks = summary.getStacksByCount();
            int remaining = stacks.size();
            List<BigItemStack> currentList = null;

            if (stacks.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new StockResponsePacket(networkId, true, Collections.emptyList()));
                return;
            }

            for (BigItemStack entry : stacks) {
                if (currentList == null)
                    currentList = new ArrayList<>(Math.min(100, remaining));

                currentList.add(entry);
                remaining--;

                if (remaining == 0)
                    break;
                if (currentList.size() < 100)
                    continue;

                PacketDistributor.sendToPlayer(player, new StockResponsePacket(networkId, false, currentList));
                currentList = null;
            }

            if (currentList != null)
                PacketDistributor.sendToPlayer(player, new StockResponsePacket(networkId, true, currentList));
        }
    }

    /**
     * S2C，返回对应网络的库存信息。由于可能存在大量物品，因此分包发送，最后一个包会将lastPacket设置为true。
     */
    public record StockResponsePacket(UUID networkId, boolean lastPacket, List<BigItemStack> items) implements CustomPacketPayload {
        public static final Type<StockResponsePacket> TYPE = new Type<>(rl("portable_stock_response"));
        private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
        public static final StreamCodec<RegistryFriendlyByteBuf, StockResponsePacket> STREAM_CODEC = StreamCodec.composite(
                UUID_CODEC, StockResponsePacket::networkId,
                ByteBufCodecs.BOOL, StockResponsePacket::lastPacket,
                CatnipStreamCodecBuilders.list(BigItemStack.STREAM_CODEC), StockResponsePacket::items,
                StockResponsePacket::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StockResponsePacket packet, IPayloadContext context) {
            PortableStockTickerClientData.receive(packet.networkId(), packet.items(), packet.lastPacket());
        }
    }
}
