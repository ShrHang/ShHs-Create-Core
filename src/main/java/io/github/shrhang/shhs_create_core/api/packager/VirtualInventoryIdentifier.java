package io.github.shrhang.shhs_create_core.api.packager;

import com.simibubi.create.api.packager.InventoryIdentifier;
import net.createmod.catnip.math.BlockFace;

/**
 * Identifies an inventory by a logical key instead of one or more physical block faces.
 * Implementations must base {@link # equals(Object)} and {@link # hashCode()} on that key.
 */
public interface VirtualInventoryIdentifier extends InventoryIdentifier {
    @Override
    default boolean contains(BlockFace face) {
        return false;
    }
}
