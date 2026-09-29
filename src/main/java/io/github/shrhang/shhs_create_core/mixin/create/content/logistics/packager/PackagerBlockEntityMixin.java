package io.github.shrhang.shhs_create_core.mixin.create.content.logistics.packager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryIdentifier;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {
    @WrapOperation(method = "unwrapBox", remap = false,
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/packager/unpacking/UnpackingHandler;unpack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Ljava/util/List;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Z)Z"))
    private boolean shhs$receiveTerminalOrder(UnpackingHandler handler, Level level, BlockPos pos, BlockState state, Direction side,
                                              List<ItemStack> items,
                                              PackageOrderWithCrafts context, boolean simulate,
                                              Operation<Boolean> original,
                                              ItemStack box, boolean outerSimulate) {
        // The unpacking API loses the box's order/fragment IDs; capture the enclosing method argument.
        if (level instanceof ServerLevel serverLevel) {
            Boolean received = TerminalOrders.get(serverLevel.getServer())
                    .receive(serverLevel, pos, box, simulate);
            if (received != null)
                return received;
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
