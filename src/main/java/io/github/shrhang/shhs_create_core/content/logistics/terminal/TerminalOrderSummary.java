package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public record TerminalOrderSummary(UUID id, boolean ready, int packages, List<Line> lines) {
    public record Line(ItemStack stack, UUID network, long requested, long remaining) {
        public long delivered() { return Math.max(0, requested - remaining); }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putBoolean("Ready", ready);
        tag.putInt("Packages", packages);
        ListTag encodedLines = new ListTag();
        for (Line line : lines) {
            CompoundTag encoded = new CompoundTag();
            encoded.put("Stack", line.stack.save(registries));
            encoded.putUUID("Network", line.network);
            encoded.putLong("Requested", line.requested);
            encoded.putLong("Remaining", line.remaining);
            encodedLines.add(encoded);
        }
        tag.put("Lines", encodedLines);
        return tag;
    }

    public static TerminalOrderSummary load(CompoundTag tag, HolderLookup.Provider registries) {
        List<Line> lines = new ArrayList<>();
        ListTag encodedLines = tag.getList("Lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < encodedLines.size(); i++) {
            CompoundTag encoded = encodedLines.getCompound(i);
            if (!encoded.hasUUID("Network")) continue;
            ItemStack stack = ItemStack.parseOptional(registries, encoded.getCompound("Stack"));
            if (!stack.isEmpty()) lines.add(new Line(stack, encoded.getUUID("Network"),
                    encoded.getLong("Requested"), encoded.getLong("Remaining")));
        }
        return new TerminalOrderSummary(tag.getUUID("Id"), tag.getBoolean("Ready"),
                tag.getInt("Packages"), List.copyOf(lines));
    }
}
