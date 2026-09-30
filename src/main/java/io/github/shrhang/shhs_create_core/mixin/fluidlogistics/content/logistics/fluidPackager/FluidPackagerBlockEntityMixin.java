package io.github.shrhang.shhs_create_core.mixin.fluidlogistics.content.logistics.fluidPackager;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.shrhang.shhs_create_core.compat.fluidlogistics.terminal.FluidLogisticsTerminalOrderCompat;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity",
        remap = false)
public abstract class FluidPackagerBlockEntityMixin {
    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void shhsc_c$receiveTerminalFluidOrder(ItemStack box, boolean simulate,
                                                   CallbackInfoReturnable<Boolean> cir) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        if (!FluidLogisticsTerminalOrderCompat.isFluidPackage(box)
                || !(self.getLevel() instanceof ServerLevel level))
            return;
        if (self.animationTicks > 0) {
            cir.setReturnValue(false);
            return;
        }
        Direction facing = self.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        BlockPos target = self.getBlockPos().relative(facing.getOpposite());
        Boolean received = TerminalOrders.get(level.getServer()).receive(level, target, box, simulate);
        if (received == null)
            return;
        if (received && !simulate) {
            self.previouslyUnwrapped = box.copyWithCount(1);
            self.animationInward = true;
            self.animationTicks = PackagerBlockEntity.CYCLE;
            self.notifyUpdate();
        }
        cir.setReturnValue(received);
    }
}
