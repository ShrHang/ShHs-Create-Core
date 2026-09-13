package io.github.shrhang.shhs_create_core.mixin.minecraft.world.entity.monster;

import io.github.shrhang.shhs_create_core.content.registries.ShHsSkullTypes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Drowned.class)
public abstract class DrownedMixin {
    @Inject(method = "getSkull", at = @At("HEAD"), cancellable = true)
    private void shhsc_c$getSkull(CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(new ItemStack(ShHsSkullTypes.DROWNED.getItem()));
    }
}
