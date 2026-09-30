package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.github.shrhang.shhs_create_core.content.logistics.LogisticsPackets;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes a client snapshot only after all parts of a revision have arrived. */
final class TerminalSnapshotSync {
    record Snapshot(List<TerminalStock.Entry> stock, List<TerminalOrderSummary> orders,
                    Map<UUID, String> addresses, long revision, UUID acceptedSubmission) {}

    private Snapshot current = new Snapshot(List.of(), List.of(), Map.of(), -1, null);
    private long receivingRevision = -1;
    private int nextPart;
    private final List<TerminalStock.Entry> receiving = new ArrayList<>();
    private final List<TerminalOrderSummary> receivingOrders = new ArrayList<>();
    private final Map<UUID, String> receivingSourceAddresses = new HashMap<>();
    private long revision;

    Snapshot current() {
        return current;
    }

    void send(DimensionLogisticsTerminalMenu menu) {
        if (!(menu.player instanceof ServerPlayer serverPlayer))
            return;
        DimensionsNet net = menu.network();
        TerminalStock.Snapshot snapshot = TerminalStock.snapshot(serverPlayer, net);
        List<TerminalStock.Entry> stock = snapshot.entries();
        long version = ++revision;
        List<TerminalOrderSummary> summaries = TerminalOrders.get(serverPlayer.server).summaries(serverPlayer);
        int stockParts = Math.max(1, (stock.size() + 31) / 32);
        int parts = stockParts + summaries.size();
        for (int part = 0; part < parts; part++) {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (int i = part * 32; i < Math.min(stock.size(), (part + 1) * 32); i++)
                list.add(TerminalData.entry(stock.get(i), serverPlayer.registryAccess()));
            tag.put("Stock", list);
            if (part == 0) {
                ListTag sources = new ListTag();
                snapshot.addresses().forEach((network, address) -> {
                    CompoundTag source = new CompoundTag();
                    source.putUUID("Network", network);
                    source.putString("Address", address);
                    sources.add(source);
                });
                tag.put("Sources", sources);
            }
            if (part >= stockParts) {
                ListTag orderPart = new ListTag();
                orderPart.add(summaries.get(part - stockParts).save(serverPlayer.registryAccess()));
                tag.put("Orders", orderPart);
            }
            if (menu.acceptedSubmission != null)
                tag.putUUID("Accepted", menu.acceptedSubmission);
            LogisticsPackets.send(serverPlayer, menu.containerId, menu.session, version, part, part == parts - 1, tag);
        }
    }

    void receive(HolderLookup.Provider registries, long revision, int part, boolean last, CompoundTag tag) {
        if (revision <= current.revision())
            return;
        if (part == 0) {
            receiving.clear();
            receivingOrders.clear();
            receivingSourceAddresses.clear();
            receivingRevision = revision;
            nextPart = 0;
        }
        if (revision != receivingRevision || part != nextPart++)
            return;
        ListTag list = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
            receiving.add(TerminalData.entry(list.getCompound(i), registries));
        ListTag sources = tag.getList("Sources", Tag.TAG_COMPOUND);
        for (int i = 0; i < sources.size(); i++) {
            CompoundTag source = sources.getCompound(i);
            if (source.hasUUID("Network"))
                receivingSourceAddresses.put(source.getUUID("Network"), source.getString("Address"));
        }
        ListTag orderTags = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderTags.size(); i++)
            receivingOrders.add(TerminalOrderSummary.load(orderTags.getCompound(i), registries));
        if (last) {
            current = new Snapshot(List.copyOf(receiving), List.copyOf(receivingOrders),
                    Map.copyOf(receivingSourceAddresses), revision,
                    tag.hasUUID("Accepted") ? tag.getUUID("Accepted") : current.acceptedSubmission());
        }
    }
}
