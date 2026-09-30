package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TerminalData {
    public static final int MAX_ORDER_LINES = 9;
    public static final int MAX_ITEMS = 32768;
    public static final int MAX_ACTIVE_ORDERS = 64;

    private TerminalData() {
    }

    public static Component text(String key, Object... args) {
        return Component.translatable("text.shhs_create_core.terminal." + key, args);
    }

    public record Selection(ItemStackKey key, UUID network) {}

    public static CompoundTag entry(TerminalStock.Entry entry, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Stack", entry.stack().save(registries));
        tag.putLong("Amount", entry.amount());
        if (entry.network() != null) tag.putUUID("Network", entry.network());
        tag.putBoolean("Available", entry.requestable());
        return tag;
    }

    public static TerminalStock.Entry entry(CompoundTag tag, HolderLookup.Provider registries) {
        return new TerminalStock.Entry(ItemStack.parseOptional(registries, tag.getCompound("Stack")),
                tag.getLong("Amount"), tag.hasUUID("Network") ? tag.getUUID("Network") : null,
                tag.getBoolean("Available"));
    }

    public static ListTag stacks(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        stacks.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> list.add(stack.save(registries)));
        return list;
    }

    public static List<ItemStack> stacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty())
                stacks.add(stack);
        }
        return stacks;
    }

    public static void append(List<ItemStack> destination, ItemStack template, long count) {
        for (ItemStack stack : destination) {
            if (!ItemStack.isSameItemSameComponents(stack, template))
                continue;
            int added = (int) Math.min(count, stack.getMaxStackSize() - stack.getCount());
            stack.grow(added);
            count -= added;
            if (count == 0)
                return;
        }
        while (count > 0) {
            int added = (int) Math.min(count, template.getMaxStackSize());
            destination.add(template.copyWithCount(added));
            count -= added;
        }
    }
    public record OrderSummary(UUID id, boolean ready, int packages, List<Line> lines) {
        public record Line(ItemStack stack, UUID network, long requested, long remaining) {
            public long delivered() {
                return Math.max(0, requested - remaining);
            }
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

        public static OrderSummary load(CompoundTag tag, HolderLookup.Provider registries) {
            List<Line> lines = new ArrayList<>();
            ListTag encodedLines = tag.getList("Lines", Tag.TAG_COMPOUND);
            for (int i = 0; i < encodedLines.size(); i++) {
                CompoundTag encoded = encodedLines.getCompound(i);
                if (!encoded.hasUUID("Network")) continue;
                ItemStack stack = ItemStack.parseOptional(registries, encoded.getCompound("Stack"));
                if (!stack.isEmpty())
                    lines.add(new Line(stack, encoded.getUUID("Network"),
                            encoded.getLong("Requested"), encoded.getLong("Remaining")));
            }
            return new OrderSummary(tag.getUUID("Id"), tag.getBoolean("Ready"),
                    tag.getInt("Packages"), List.copyOf(lines));
        }
    }

    public record PackageEntry(ItemStack key, int amount, @Nullable ResourceLocation resourceType) {
        public PackageEntry {
            key = key.copyWithCount(1);
            if (key.isEmpty() || amount <= 0)
                throw new IllegalArgumentException("Package entries require a key and positive amount");
        }

        @Override
        public ItemStack key() {
            return key.copy();
        }

        public boolean isResource() {
            return resourceType != null;
        }
    }
}
