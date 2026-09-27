package io.github.shrhang.shhs_create_core.api.packager;

import org.jetbrains.annotations.Nullable;

/**
 * Provides the logical identity of a block entity backed by a virtual inventory.
 */
@FunctionalInterface
public interface VirtualInventoryProvider {
    @Nullable
    VirtualInventoryIdentifier getVirtualInventoryIdentifier();
}
