package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import io.github.shrhang.shhs_create_core.content.util.SprayHelper;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 喷洒器在移动结构中的行为。
 * 从 Contraption 的整体流体存储中抽取流体，按固定转角喷洒。
 * 转角在移动前已固定，读取自 blockEntityData 中的 "Angle"。
 */
public class SprayerMovementBehaviour implements MovementBehaviour {
    private static final int MAX_CONSUMPTION = 32;

    @Override
    public void tick(MovementContext context) {
        Level level = context.world;
        if (level == null || level.isClientSide()) return;
        // 每5 tick喷洒一次，与静态行为一致
        if (level.getGameTime() % 5 != 0) return;

        if (!isActive(context)) return;

        BlockState state = context.state;
        if (!(state.getBlock() instanceof SprayerBlock)) return;

        Direction facing = state.getValue(SprayerBlock.FACING);

        // 读取转角，默认为180（全开）
        float angle = 180f;
        if (context.blockEntityData != null && context.blockEntityData.contains("Angle")) {
            angle = context.blockEntityData.getFloat("Angle");
        }
        if (angle <= 0) return; // 完全关闭则不喷洒

        float ratio = Math.min(angle / 180f, 1.0f);
        int maxAllowed = (int) (MAX_CONSUMPTION * ratio);
        if (maxAllowed <= 0) return;

        // 获取 Contraption 的整体流体存储管理器（汇集所有流体容器）
        IFluidHandler fluidManager = context.contraption.getStorage().getFluids();
        if (fluidManager == null) return;

        // 从整体存储中抽取流体
        FluidStack drained = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        // 实际抽取量可能少于请求量，重新计算比例
        float actualRatio = (float) drained.getAmount() / MAX_CONSUMPTION;

        // 构建喷洒区域中心：使用 context.position 作为世界坐标中心
        Vec3 center = context.position;
        // 喷洒起点：中心沿方向偏移0.5格
        Vec3 origin = center.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));

        AABB aabb = SprayHelper.buildAABB(center, facing, actualRatio);
        SprayHelper.applyEffect(level, aabb, drained);

        if (level instanceof ServerLevel serverLevel) {
            SprayHelper.spawnParticles(serverLevel, origin, facing, actualRatio, drained);
        }
    }

    @Override
    public void startMoving(MovementContext context) {
        // 无需特殊处理
    }

    @Override
    public void stopMoving(MovementContext context) {
        // 无需特殊处理
    }

}