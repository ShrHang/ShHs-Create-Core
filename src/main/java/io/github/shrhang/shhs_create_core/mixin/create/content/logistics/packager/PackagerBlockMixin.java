package io.github.shrhang.shhs_create_core.mixin.create.content.logistics.packager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PackagerBlock.class)
public abstract class PackagerBlockMixin {
    @WrapOperation(method = "getStateForPlacement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getCapability(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Ljava/lang/Object;",
                    remap = false))
    private Object shhsc_c$recognizeDimensionParcelStation(Level level, BlockCapability<?, ?> capability,
                                                            BlockPos pos, Object context,
                                                            Operation<Object> original) {
        Object handler = original.call(level, capability, pos, context);
        if (handler != null || capability != Capabilities.ItemHandler.BLOCK)
            return handler;
        if (level.getBlockEntity(pos) instanceof DimensionParcelStationBlockEntity station
                && station.isItemPackagerPlacementTarget())
            return EmptyItemHandler.INSTANCE;
        return null;
    }
}
