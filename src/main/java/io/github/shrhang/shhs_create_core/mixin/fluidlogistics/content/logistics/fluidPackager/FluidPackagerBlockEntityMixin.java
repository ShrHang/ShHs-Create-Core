package io.github.shrhang.shhs_create_core.mixin.fluidlogistics.content.logistics.fluidPackager;

import com.yision.fluidlogistics.api.packager.ResourcePackager;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidPackagerBlockEntity.class, remap = false)
public abstract class FluidPackagerBlockEntityMixin {
    @Inject(method = "scan", at = @At("RETURN"), cancellable = true)
    private void shhs_create_core$identifyDimensionNetworkStorage(
            CallbackInfoReturnable<ResourcePackager.Snapshot> cir) {
        FluidPackagerBlockEntity self = (FluidPackagerBlockEntity) (Object) this;
        if (self.getLevel() == null || self.fluidTarget == null)
            return;
        BlockPos targetPos = self.fluidTarget.getTarget().getOpposite().getPos();
        if (!(self.getLevel().getBlockEntity(targetPos) instanceof DimensionParcelStationBlockEntity station)
                || station.getNet() == null)
            return;
        ResourcePackager.Snapshot original = cir.getReturnValue();
        if (original.storageIdentity() == null)
            return;
        cir.setReturnValue(new ResourcePackager.Snapshot(station.getNet(), original.resources()));
    }
}
