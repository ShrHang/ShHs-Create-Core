package io.github.shrhang.shhs_create_core.content.fluid.openpipe;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

public class LiquidFertilizerEffectHandler implements OpenPipeEffectHandler {
    @Override
    public void apply(Level level, AABB area, FluidStack fluid) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 20 != 0) {
            return;
        }

        BlockPos.betweenClosedStream(area).forEach(pos -> grow(serverLevel, pos));
    }

    private static void grow(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(level, pos, state)) {
            bonemealable.performBonemeal(level, level.random, pos, state);
        }
    }
}
