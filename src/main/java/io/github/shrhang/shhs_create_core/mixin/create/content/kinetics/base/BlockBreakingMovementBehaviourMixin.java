package io.github.shrhang.shhs_create_core.mixin.create.content.kinetics.base;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.DrillBreakEffectContext;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.DrillHitSoundContext;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("ConstantValue")
@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class BlockBreakingMovementBehaviourMixin {
    @WrapOperation(method = "tickBreaker", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", remap = true))
    private void shhsc_c$markDrillHit(Level level, Player player, BlockPos pos, SoundEvent sound,
                                     SoundSource source, float volume, float pitch, Operation<Void> original) {
        DrillHitSoundContext.run(level, pos,
                (Object) this instanceof DrillMovementBehaviour && !level.isClientSide(),
                () -> original.call(level, player, pos, sound, source, volume, pitch));
    }

    @WrapMethod(method = "destroyBlock", remap = false)
    private void shhsc_c$markDrillEffect(MovementContext context, BlockPos pos, Operation<Void> original) {
        DrillBreakEffectContext.run(context.world, pos,
                (Object) this instanceof DrillMovementBehaviour && !context.world.isClientSide(),
                () -> original.call(context, pos));
    }
}
