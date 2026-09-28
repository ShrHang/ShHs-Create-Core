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
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "unwrapBox", remap = false,
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/packager/unpacking/UnpackingHandler;unpack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Ljava/util/List;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Z)Z"))
    private boolean shhs$receiveTerminalOrder(com.simibubi.create.api.packager.unpacking.UnpackingHandler handler,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.Direction side,
            java.util.List<net.minecraft.world.item.ItemStack> items,
            com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts context, boolean simulate,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original,
            net.minecraft.world.item.ItemStack box, boolean outerSimulate) {
        // The unpacking API loses the box's order/fragment IDs; capture the enclosing method argument.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Boolean received = io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalOrders
                    .get(serverLevel.getServer()).receive(serverLevel, pos, box, simulate);
            if (received != null) return received;
        }
        return original.call(handler, level, pos, state, side, items, context, simulate);
    }

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
