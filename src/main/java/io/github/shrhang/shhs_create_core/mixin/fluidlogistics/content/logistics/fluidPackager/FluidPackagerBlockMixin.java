package io.github.shrhang.shhs_create_core.mixin.fluidlogistics.content.logistics.fluidPackager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlock;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FluidPackagerBlock.class, remap = false)
public abstract class FluidPackagerBlockMixin {
    @WrapOperation(method = "getStateForPlacement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getCapability(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Ljava/lang/Object;",
                    remap = false))
    private Object shhsc_c$recognizeDimensionParcelStation(Level level, BlockCapability<?, ?> capability,
                                                            BlockPos pos, Object context,
                                                            Operation<Object> original) {
        Object handler = original.call(level, capability, pos, context);
        if (handler != null || capability != Capabilities.FluidHandler.BLOCK)
            return handler;
        if (level.getBlockEntity(pos) instanceof DimensionParcelStationBlockEntity station
                && station.isFluidPackagerPlacementTarget())
            return EmptyFluidHandler.INSTANCE;
        return null;
    }
}
