package io.github.shrhang.shhs_create_core.mixin.minecraft.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.DrillBreakEffectContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapOperation(method = "levelEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", ordinal = 0))
    private void shhsc_c$filterDrillParticles(ClientLevel level, BlockPos pos, BlockState state,
                                            Operation<Void> original) {
        if (!DrillBreakEffectContext.consume(level, pos)) {
            original.call(level, pos, state);
        }
    }
}
