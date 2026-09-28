package io.github.shrhang.shhs_create_core.mixin.create.content.logistics.packager;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalOrders;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PackageRepackageHelper.class)
public abstract class TerminalRepackageMixin {
    @Inject(method = "repack", at = @At("RETURN"), remap = false)
    private void shhs$numberTerminalFragments(int orderId, RandomSource random, CallbackInfoReturnable<List<BigItemStack>> cir) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !TerminalOrders.get(server).ownsLogisticsId(orderId)) return;
        List<BigItemStack> packages = cir.getReturnValue();
        // Create assigns 0:0 to every repacked box. Orders need distinct fragments for receipt deduplication.
        for (int i = 0; i < packages.size(); i++) {
            var box = packages.get(i).stack;
            PackageItem.setOrder(box, orderId, 0, true, i, i == packages.size() - 1, PackageItem.getOrderContext(box));
        }
    }
}
