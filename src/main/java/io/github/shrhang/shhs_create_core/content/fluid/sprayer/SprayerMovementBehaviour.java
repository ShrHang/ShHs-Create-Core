package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 喷洒器在移动结构中的行为。
 * 从 Contraption 整体流体存储中抽取流体，按固定转角（0~270°）线性消耗。
 */
public class SprayerMovementBehaviour implements MovementBehaviour {
    private static final int MAX_CONSUMPTION = 32;
    private static final float MAX_ANGLE = 270.0f;

    @Override
    public void tick(MovementContext context) {
        Level level = context.world;
        if (level == null || level.isClientSide()) return;
        if (level.getGameTime() % 5 != 0) return;

        if (!isActive(context)) return;

        BlockState state = context.state;
        if (!(state.getBlock() instanceof SprayerBlock)) return;

        Direction facing = state.getValue(SprayerBlock.FACING);

        // 读取转角，默认为270（全开）
        float angle = MAX_ANGLE;
        if (context.blockEntityData != null && context.blockEntityData.contains("Angle")) {
            angle = context.blockEntityData.getFloat("Angle");
        }
        angle = Mth.clamp(angle, 0f, MAX_ANGLE);
        if (angle <= 0) return;

        int maxAllowed = SprayerHelper.getMaxConsumption(angle, MAX_ANGLE, MAX_CONSUMPTION);
        if (maxAllowed <= 0) return;

        IFluidHandler fluidManager = context.contraption.getStorage().getFluids();
        if (fluidManager == null) return;

        FluidStack drained = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        float actualRatio = SprayerHelper.getFluidRatio(drained.getAmount(), MAX_CONSUMPTION);

        Vec3 center = SprayerHelper.getSprayCenter(context.position, facing);

        AABB aabb = SprayerHelper.buildAABB(center, facing, actualRatio);
        SprayerHelper.applyEffect(level, aabb, drained);

        if (level instanceof ServerLevel serverLevel) {
            SprayerHelper.spawnParticles(serverLevel, center, facing, actualRatio, drained);
        }
    }

    @Override
    public void startMoving(MovementContext context) {
        // 无需处理
    }

    @Override
    public void stopMoving(MovementContext context) {
        // 无需处理
    }
}