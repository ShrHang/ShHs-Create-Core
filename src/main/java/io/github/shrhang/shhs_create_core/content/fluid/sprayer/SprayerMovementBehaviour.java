package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
        // 从 blockEntityData 读取开度（直接从 Openness 复合标签中取 value）
        float openness = 0.0f;
        if (context.blockEntityData != null && context.blockEntityData.contains("Openness")) {
            CompoundTag opennessTag = context.blockEntityData.getCompound("Openness");
            openness = opennessTag.getFloat("Target");
        }
        openness = Mth.clamp(openness, 0f, 1f);
        if (openness <= 0) return;
        int maxAllowed = (int)(openness * MAX_CONSUMPTION);
        if (maxAllowed <= 0) maxAllowed = 1;
        IFluidHandler fluidManager = context.contraption.getStorage().getFluids();
        if (fluidManager == null) return;
        // 服务端：每5 tick执行消耗和效果
        if (!level.isClientSide) {
            if (level.getGameTime() % 5 != 0) return;
            FluidStack simulated = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
            OpenPipeEffectHandler effectHandler = SprayerHelper.getEffectHandler(simulated);
            if (effectHandler == null) return;
            FluidStack drained = fluidManager.drain(simulated, IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) return;
            float ratio = (float) drained.getAmount() / maxAllowed;
            Vec3 center = SprayerHelper.getSprayCenter(context.position, facing);
            AABB aabb = SprayerHelper.buildAABB(center, facing, ratio);
            SprayerHelper.applyEffect(effectHandler, level, aabb, drained);
            return;
        }
        // 客户端：每帧生成粒子，数量固定为最小值
        FluidStack simulated = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) return;

        float ratio = (float) simulated.getAmount() / maxAllowed;
        Vec3 center = SprayerHelper.getSprayCenter(context.position, facing);
        SprayerHelper.spawnParticles(level, center, facing, ratio, simulated, FIXED_COUNT_SCALE);
    }
}