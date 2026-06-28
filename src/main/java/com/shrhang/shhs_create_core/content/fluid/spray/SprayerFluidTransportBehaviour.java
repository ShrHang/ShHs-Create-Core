package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public class SprayerFluidTransportBehaviour extends FluidTransportBehaviour {

    public static final BehaviourType<SprayerFluidTransportBehaviour> TYPE = new BehaviourType<>();

    public SprayerFluidTransportBehaviour(SmartBlockEntity be) {
        super(be);
    }

    @Override
    public boolean canHaveFlowToward(BlockState state, Direction direction) {
        Direction facing = state.getValue(SprayerBlock.FACING);
        return direction.getAxis() == facing.getAxis();
    }

    @Override
    public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
        // 喷洒模式（ENABLED=false）下禁止抽取
        if (state.hasProperty(SprayerBlock.ENABLED) && !state.getValue(SprayerBlock.ENABLED)) {
            return false;
        }
        return super.canPullFluidFrom(fluid, state, direction);
    }
}