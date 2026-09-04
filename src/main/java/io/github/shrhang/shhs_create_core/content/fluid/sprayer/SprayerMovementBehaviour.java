package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
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

/**
 * 移动喷洒器的行为实现。
 * 处理移动结构（如起重机、火车）上安装的喷洒器的每帧逻辑，
 * 包括根据旋转计算实际朝向、粒子生成和流体效果应用。
 */
public class SprayerMovementBehaviour implements MovementBehaviour {

    private static final int MAX_CONSUMPTION = 32;
    private static final float FIXED_COUNT_SCALE = 0.5f;

    /**
     * 每帧调用，驱动喷洒器的行为。
     * 首先进行前置条件检查（有效性、开启度、流体容器等），
     * 然后计算实际喷洒方向，根据客户端/服务端分别派发粒子或效果逻辑。
     *
     * @param context 移动上下文，包含位置、旋转、状态等
     */
    @Override
    public void tick(MovementContext context) {
        Level level = context.world;
        if (level == null) return;
        // 继承父类默认检查（是否禁用等）
        if (!isActive(context)) return;
        BlockState state = context.state;
        if (!(state.getBlock() instanceof SprayerBlock)) return;
        Direction facing = state.getValue(SprayerBlock.FACING);
        float openness = getOpenness(context);
        if (openness <= 0) return;
        int maxAllowed = calculateMaxAllowed(openness);
        if (maxAllowed <= 0) maxAllowed = 1;
        IFluidHandler fluidManager = context.contraption.getStorage().getFluids();
        if (fluidManager == null) return;
        Vec3 position = context.position;
        if (position == null) return;
        // 计算实际喷洒方向（考虑移动体旋转）
        Vec3 actualDir = getActualDirection(context, facing);
        if (actualDir.lengthSqr() < 1e-8) {
            // 若旋转后向量退化，则退回到轴向方向
            actualDir = Vec3.atLowerCornerOf(facing.getNormal());
        }
        // 映射到轴向方向，用于 AABB 延伸
        Direction axialDir = SprayerHelper.getAxialDirection(actualDir);
        // 服务端逻辑（每 5 tick 消耗并应用效果）
        if (!level.isClientSide) {
            performServerSpray(level, position, axialDir, maxAllowed, fluidManager);
            return;
        }
        // 客户端逻辑（每帧生成粒子）
        spawnClientParticles(level, position, actualDir, axialDir, maxAllowed, fluidManager);
    }

    /**
     * 从上下文中读取开启度（0~1），由方块实体 NBT 中的 Openness 值决定。
     *
     * @param context 移动上下文
     * @return 开启度，无数据时返回 0
     */
    private static float getOpenness(MovementContext context) {
        CompoundTag blockEntityData = context.blockEntityData;
        if (blockEntityData == null || !blockEntityData.contains("Openness", Tag.TAG_COMPOUND)) {
            return 0.0f;
        }
        CompoundTag opennessTag = blockEntityData.getCompound("Openness");
        return Mth.clamp(opennessTag.getFloat("Value"), 0f, 1f);
    }

    /**
     * 根据开启度计算最大可消耗流体量（线性映射）。
     *
     * @param openness 开启度 [0,1]
     * @return 最大消耗量（mb）
     */
    private static int calculateMaxAllowed(float openness) {
        return (int) (openness * MAX_CONSUMPTION);
    }

    /**
     * 计算实际喷洒方向向量。
     * 将方块固定朝向向量通过移动体的旋转算子进行变换。
     *
     * @param context 移动上下文
     * @param facing  方块固定朝向（轴向）
     * @return 旋转后的实际方向向量
     */
    private static Vec3 getActualDirection(MovementContext context, Direction facing) {
        Vec3 axialVec = Vec3.atLowerCornerOf(facing.getNormal());
        return context.rotation.apply(axialVec);
    }

    // ----- 服务端逻辑（每 5 tick 执行） -----

    /**
     * 服务端喷洒处理（每 5 tick 执行一次）。
     * 从流体容器中消耗流体，并根据实际方向（轴向映射）构建作用范围 AABB，
     * 然后调用对应的流体效果处理器。
     *
     * @param level        服务端世界
     * @param position     喷洒器位置
     * @param axialDir     实际方向映射后的轴向方向（用于 AABB 延伸）
     * @param maxAllowed   本次最多可消耗的流体量
     * @param fluidManager 流体容器接口
     */
    private static void performServerSpray(Level level, Vec3 position, Direction axialDir,
                                           int maxAllowed, IFluidHandler fluidManager) {
        if (level.getGameTime() % 5 != 0) return;
        // 模拟抽取，获取流体类型
        FluidStack simulated = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) return;
        OpenPipeEffectHandler effectHandler = SprayerHelper.getEffectHandler(simulated);
        if (effectHandler == null) return;
        // 实际执行抽取
        FluidStack drained = fluidManager.drain(simulated, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;
        float ratio = SprayerHelper.getFluidRatio(drained.getAmount(), MAX_CONSUMPTION);
        Vec3 center = SprayerHelper.getSprayCenter(position, axialDir);
        AABB aabb = SprayerHelper.buildAABB(center, axialDir, ratio);
        SprayerHelper.applyEffect(effectHandler, level, aabb, drained);
    }

    // ----- 客户端逻辑（每帧执行） -----
    /**
     * 客户端粒子生成（每帧调用）。
     * 模拟抽取（仅用于获取流体类型和数量），然后生成喷洒粒子，
     * 粒子方向使用实际方向向量，数量受开启度和玩家距离缩放。
     *
     * @param level        客户端世界
     * @param position     喷洒器位置
     * @param actualDir    实际喷洒方向向量（用于粒子速度方向）
     * @param axialDir     轴向方向（仅用于计算喷射中心）
     * @param maxAllowed   最大消耗量（用于模拟）
     * @param fluidManager 流体容器接口
     */
    private static void spawnClientParticles(Level level, Vec3 position, Vec3 actualDir, Direction axialDir,
                                             int maxAllowed, IFluidHandler fluidManager) {
        FluidStack simulated = fluidManager.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) return;

        float ratio = SprayerHelper.getFluidRatio(simulated.getAmount(), MAX_CONSUMPTION);
        Vec3 center = SprayerHelper.getSprayCenter(position, axialDir);

        // 使用固定缩放因子（移动体上无需玩家距离缩放，保留原设计）
        SprayerHelper.spawnParticles(level, center, actualDir, ratio, simulated, FIXED_COUNT_SCALE);
    }
}