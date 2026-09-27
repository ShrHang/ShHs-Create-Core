package io.github.shrhang.shhs_create_core.content.registries;

import com.simibubi.create.api.packager.InventoryIdentifier;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryProvider;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ShHsInventoryIdentifiers {
    public static void register() {
        InventoryIdentifier.Finder virtualInventoryFinder = ShHsInventoryIdentifiers::findVirtualInventory;
        InventoryIdentifier.REGISTRY.register(ShHsBlocks.BRASS_ENDER_CHEST.get(), virtualInventoryFinder);
        InventoryIdentifier.REGISTRY.register(ShHsBlocks.DIMENSION_PARCEL_STATION.get(), virtualInventoryFinder);
    }

    private static InventoryIdentifier findVirtualInventory(Level level, BlockState ignoredState, BlockFace face) {
        return level.getBlockEntity(face.getPos()) instanceof VirtualInventoryProvider provider
                ? provider.getVirtualInventoryIdentifier()
                : null;
    }
}
