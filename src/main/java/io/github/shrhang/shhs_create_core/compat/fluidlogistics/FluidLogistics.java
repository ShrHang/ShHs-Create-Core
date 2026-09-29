package io.github.shrhang.shhs_create_core.compat.fluidlogistics;

import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.api.packager.ResourcePackagers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FluidLogistics {
    private FluidLogistics() {}

    public static boolean isFluidPackager(BlockEntity blockEntity) {
        return ResourcePackagers.of(blockEntity)
                .map(packager -> PackageResourceTypes.FLUID.equals(packager.resourceTypeId()))
                .orElse(false);
    }

    public static ItemStack fluidDisplayKey(FluidStackKey key) {
        return PackageResourceTypes.createFluidKey(key.copyStack());
    }
}
