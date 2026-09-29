package io.github.shrhang.shhs_create_core.mixin.create.content.logistics.packagerLink;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LogisticsManager.class, priority = 900)
public abstract class LogisticsManagerMixin {
    @Inject(method = "getInventoryIdentifierFromLink", at = @At("RETURN"), cancellable = true, remap = false)
    private static void shhs_create_core$identifyDimensionNetworkInventory(
            LogisticallyLinkedBehaviour link, CallbackInfoReturnable<InventoryIdentifier> cir) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity linkBlockEntity))
            return;
        PackagerBlockEntity packager = linkBlockEntity.getPackager();
        if (packager == null || packager.getLevel() == null || packager.targetInventory == null)
            return;
        BlockPos targetPos = packager.targetInventory.getTarget().getConnectedPos();
        if (!(packager.getLevel().getBlockEntity(targetPos) instanceof DimensionParcelStationBlockEntity station))
            return;
        if (!station.allowsOutputFor(packager)) {
            cir.setReturnValue(null);
            return;
        }
        // Keep FluidLogistics' native scan identity for its cache, but group every usable
        // endpoint of the same dimension network as one logical warehouse inventory.
        if (cir.getReturnValue() != null)
            cir.setReturnValue(station.getVirtualInventoryIdentifier());
    }
}
