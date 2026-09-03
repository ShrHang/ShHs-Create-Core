package io.github.shrhang.shhs_create_core.mixin.create.content.fluids.tank.storage.creative;

import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.storage.creative.CreativeFluidTankMountedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeFluidTankMountedStorage.class)
public abstract class CreativeFluidTankMountedStorageMixin implements SyncedMountedStorage {

    @Unique
    private boolean shhsc_c$needsInitialSync = false;

    @Unique
    public void shhsc_c$setNeedsInitialSync(boolean value) {
        this.shhsc_c$needsInitialSync = value;
    }

    @Override
    public boolean isDirty() {
        return shhsc_c$needsInitialSync;
    }

    @Override
    public void markClean() {
        shhsc_c$needsInitialSync = false;
    }

    @Override
    public void afterSync(Contraption contraption, BlockPos localPos) {
        shhsc_c$needsInitialSync = false;
    }

    @Inject(
            method = "fromTank",
            at = @At("RETURN"),
            remap = false
    )
    private static void shhsc_c$onFromTank(CreativeFluidTankBlockEntity tank, CallbackInfoReturnable<CreativeFluidTankMountedStorage> cir) {
        CreativeFluidTankMountedStorage storage = cir.getReturnValue();
        if (storage != null) {
            Level level = tank.getLevel();
            if (level != null && !level.isClientSide()) {
                ((CreativeFluidTankMountedStorageMixin) (Object) storage).shhsc_c$setNeedsInitialSync(true);
            }
        }
    }
}