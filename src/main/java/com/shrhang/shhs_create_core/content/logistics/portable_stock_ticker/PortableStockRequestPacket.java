package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

public record PortableStockRequestPacket(UUID networkId) implements CustomPacketPayload {
    public static final Type<PortableStockRequestPacket> TYPE = new Type<>(rl("portable_stock_request"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = UUIDUtil.STREAM_CODEC.cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableStockRequestPacket> STREAM_CODEC =
            UUID_CODEC.map(PortableStockRequestPacket::new, PortableStockRequestPacket::networkId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortableStockRequestPacket packet, IPayloadContext context) {
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
            PacketDistributor.sendToPlayer(player, new PortableStockResponsePacket(networkId, true, Collections.emptyList()));
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

            PacketDistributor.sendToPlayer(player, new PortableStockResponsePacket(networkId, false, currentList));
            currentList = null;
        }

        if (currentList != null)
            PacketDistributor.sendToPlayer(player, new PortableStockResponsePacket(networkId, true, currentList));
    }
}
