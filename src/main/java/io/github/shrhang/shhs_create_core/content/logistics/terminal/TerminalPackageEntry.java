package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A package payload expressed as a logical key and amount. */
public record TerminalPackageEntry(ItemStack key, int amount, @Nullable ResourceLocation resourceType) {
    public TerminalPackageEntry {
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
