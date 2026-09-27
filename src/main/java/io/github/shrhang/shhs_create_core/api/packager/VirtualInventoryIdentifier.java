package io.github.shrhang.shhs_create_core.api.packager;

import com.simibubi.create.api.packager.InventoryIdentifier;
import net.createmod.catnip.math.BlockFace;
import org.jetbrains.annotations.Nullable;

public interface VirtualInventoryIdentifier extends InventoryIdentifier {
    /**
     * Tests whether another Create identifier represents the same logical inventory.
     */
    default boolean matches(@Nullable InventoryIdentifier other) {
        return equals(other);
    }

    @Override
    default boolean contains(BlockFace face) {
        return false;
    }
}
