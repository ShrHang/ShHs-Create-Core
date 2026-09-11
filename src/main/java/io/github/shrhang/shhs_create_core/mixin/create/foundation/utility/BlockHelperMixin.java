package io.github.shrhang.shhs_create_core.mixin.create.foundation.utility;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.utility.BlockHelper;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.ContraptionDrillBreakEffectPacket;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.DrillBreakEffectContext;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.ServerDrillSoundLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockHelper.class)
public abstract class BlockHelperMixin {
    @WrapOperation(method = "destroyBlockAs", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;levelEvent(ILnet/minecraft/core/BlockPos;I)V", remap = true))
    private static void shhsc_c$sendDrillEffect(Level level, int event, BlockPos pos, int data,
                                              Operation<Void> original) {
        if (event == LevelEvent.PARTICLES_DESTROY_BLOCK && level instanceof ServerLevel serverLevel
                && DrillBreakEffectContext.consume(level, pos)) {
            var state = Block.stateById(data);
            boolean playSound = !state.isAir() && ServerDrillSoundLimiter.allow(serverLevel, pos,
                    state.getSoundType(level, pos, null).getBreakSound(), SoundSource.BLOCKS,
                    ServerDrillSoundLimiter.Kind.BREAK);
            // Match ServerLevel.levelEvent: same dimension and a 64-block radius from the block position.
            PacketDistributor.sendToPlayersNear(serverLevel, null, pos.getX(), pos.getY(), pos.getZ(), 64,
                    new ContraptionDrillBreakEffectPacket(pos.immutable(), data, playSound));
        } else {
            original.call(level, event, pos, data);
        }
    }
}
