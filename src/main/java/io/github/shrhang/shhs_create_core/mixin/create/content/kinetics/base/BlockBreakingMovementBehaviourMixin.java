package io.github.shrhang.shhs_create_core.mixin.create.content.kinetics.base;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.DrillBreakEffectContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class BlockBreakingMovementBehaviourMixin {
    @WrapMethod(method = "destroyBlock", remap = false)
    private void shhsc_c$markDrillEffect(MovementContext context, BlockPos pos, Operation<Void> original) {
        DrillBreakEffectContext.run(context.world, pos,
                (Object) this instanceof DrillMovementBehaviour && !context.world.isClientSide(),
                () -> original.call(context, pos));
    }
}
