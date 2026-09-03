package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class SprayerMovementBehaviour implements MovementBehaviour {
    private static final int MAX_CONSUMPTION = 32;
    private static final float FIXED_COUNT_SCALE = 0.2f;

    @Override
    public void tick(MovementContext context) {
        Level level = context.world;
        if (level == null) return;
        if (!isActive(context)) return;
        BlockState state = context.state;
        if (!(state.getBlock() instanceof SprayerBlock)) return;
        Direction facing = state.getValue(SprayerBlock.FACING);

        float openness = getOpenness(context);
        if (openness <= 0) return;
        int maxAllowed = (int) (openness * MAX_CONSUMPTION);
        if (maxAllowed <= 0) maxAllowed = 1;
        IFluidHandler fluidManager = context.contraption.getStorage().getFluids();
        if (fluidManager == null) return;
        var position = context.position;
        if (position == null) return;
        // 服务端：每5 tick执行消耗和效果
        if (!level.isClientSide) {
            performServerSpray(level, position, facing, maxAllowed, fluidManager);
            return;
        }
        // 客户端：每帧生成粒子，数量固定为最小值
        spawnClientParticles(level, position, facing, maxAllowed, fluidManager);
    }

    private static float getOpenness(MovementContext context) {
        CompoundTag blockEntityData = context.blockEntityData;
        if (blockEntityData == null || !blockEntityData.contains("Openness", Tag.TAG_COMPOUND)) {
            return 0.0f;
        }
        CompoundTag opennessTag = blockEntityData.getCompound("Openness");
        return Mth.clamp(opennessTag.getFloat("Value"), 0f, 1f);
    }

    private static void performServerSpray(Level level, Vec3 position, Direction facing, int maxAllowed, IFluidHandler fluidManager) {
        if (level.getGameTime() % 5 != 0) return;
        FluidStack simulated = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
        OpenPipeEffectHandler effectHandler = SprayerHelper.getEffectHandler(simulated);
        if (effectHandler == null) return;
        FluidStack drained = fluidManager.drain(simulated, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;
        float ratio = SprayerHelper.getFluidRatio(drained.getAmount(), MAX_CONSUMPTION);
        var center = SprayerHelper.getSprayCenter(position, facing);
        AABB aabb = SprayerHelper.buildAABB(center, facing, ratio);
        SprayerHelper.applyEffect(effectHandler, level, aabb, drained);
    }

    private static void spawnClientParticles(Level level, Vec3 position, Direction facing, int maxAllowed, IFluidHandler fluidManager) {
        FluidStack simulated = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) return;
        float ratio = SprayerHelper.getFluidRatio(simulated.getAmount(), MAX_CONSUMPTION);
        var center = SprayerHelper.getSprayCenter(position, facing);
        SprayerHelper.spawnParticles(level, center, facing, ratio, simulated, FIXED_COUNT_SCALE);
    }
}