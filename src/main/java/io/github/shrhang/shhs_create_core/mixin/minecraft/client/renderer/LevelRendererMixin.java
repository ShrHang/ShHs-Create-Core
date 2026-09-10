package io.github.shrhang.shhs_create_core.mixin.minecraft.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.shrhang.shhs_create_core.ShHsConfig;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.ClientDrillEffectContext;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.ClientDrillSoundLimiter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    // Match the event explicitly: this overload also appears in unrelated level-event branches.
    @WrapOperation(method = "levelEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void shhsc_c$filterDrillSound(ClientLevel level, BlockPos pos, SoundEvent sound,
                                         SoundSource source, float volume, float pitch, boolean distanceDelay,
                                         Operation<Void> original, int event, BlockPos eventPos, int data) {
        if (event == LevelEvent.PARTICLES_DESTROY_BLOCK
                && ClientDrillEffectContext.claimSound(level, eventPos)
                && ShHsConfig.CLIENT.limitContraptionDrillBreakSounds.get()) {
            if (!ClientDrillSoundLimiter.allow(level, pos, sound, source, ClientDrillSoundLimiter.Kind.BREAK)) {
                return;
            }
        }
        original.call(level, pos, sound, source, volume, pitch, distanceDelay);
    }

    @WrapOperation(method = "levelEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", ordinal = 0))
    private void shhsc_c$filterDrillParticles(ClientLevel level, BlockPos pos, BlockState state,
                                            Operation<Void> original) {
        if (!ClientDrillEffectContext.suppressParticles(level, pos)) {
            original.call(level, pos, state);
        }
    }
}
