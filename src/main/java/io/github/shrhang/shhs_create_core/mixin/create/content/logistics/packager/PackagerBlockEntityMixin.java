package io.github.shrhang.shhs_create_core.mixin.create.content.logistics.packager;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryIdentifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {
    @Inject(method = "isTargetingSameInventory", at = @At("HEAD"), cancellable = true, remap = false)
    private void shhsc_c$matchVirtualInventory(@Nullable IdentifiedInventory inventory,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (inventory == null || !(inventory.identifier() instanceof VirtualInventoryIdentifier virtualIdentifier))
            return;
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        if (self.getLevel() == null || self.targetInventory == null) {
            cir.setReturnValue(false);
            return;
        }
        InventoryIdentifier current = InventoryIdentifier.get(self.getLevel(),
                self.targetInventory.getTarget().getOpposite());
        cir.setReturnValue(virtualIdentifier.matches(current));
    }
}
