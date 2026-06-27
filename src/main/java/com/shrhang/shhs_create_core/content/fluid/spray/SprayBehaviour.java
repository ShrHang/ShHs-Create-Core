package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.IntSupplier;

/**
 * 喷洒行为：每 tick 消耗由 maxConsumptionSupplier 提供的最大量（mB），
 * 实际消耗量取该值与当前存量较小者，按实际消耗比例线性缩放喷洒范围，
 * 调用 OpenPipeEffectHandler 应用效果，并发射 Create 的 FLUID_PARTICLE 粒子。
 */
public class SprayBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<SprayBehaviour> TYPE = new BehaviourType<>();

    // 基准最大消耗（用于归一化比例，实际上限由 supplier 决定）
    private static final int MAX_CONSUMPTION = 4;

    // 各方向最大范围（格数），乘以实际消耗比例得真实范围
    private static final double UP_HORIZONTAL = 8.0;
    private static final double UP_UP = 2.0;
    private static final double UP_DOWN = 0.0;

    private static final double DOWN_HORIZONTAL = 8.0;
    private static final double DOWN_UP = 0.0;
    private static final double DOWN_DOWN = 10.5;

    private static final double HORIZONTAL_HORIZONTAL = 8.0;
    private static final double HORIZONTAL_UP = 2.0;
    private static final double HORIZONTAL_DOWN = 6.0;

    protected final FluidTank tank;
    private final IntSupplier maxConsumptionSupplier;

    public SprayBehaviour(SmartBlockEntity be, FluidTank tank, IntSupplier maxConsumptionSupplier) {
        super(be);
        this.tank = tank;
        this.maxConsumptionSupplier = maxConsumptionSupplier;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getWorld();
        if (level == null || level.isClientSide()) return;
        trySpray(level);
    }

    /**
     * 执行一次喷洒尝试：检查流体、获取处理器、计算消耗、应用效果、生成粒子。
     */
    private void trySpray(Level level) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) return;

        OpenPipeEffectHandler handler = OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
        if (handler == null) return;

        int maxAllowed = maxConsumptionSupplier.getAsInt();
        if (maxAllowed <= 0) return;

        int toDrain = Math.min(maxAllowed, fluid.getAmount());
        if (toDrain <= 0) return;

        FluidStack drained = tank.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        // 用基准最大值归一化，使范围与原有设计一致（4mB 对应满范围）
        float ratio = (float) drained.getAmount() / MAX_CONSUMPTION;
        Direction facing = blockEntity.getBlockState().getValue(SprayerBlock.FACING);

        applyEffect(level, facing, ratio, drained, handler);
        if (level instanceof ServerLevel serverLevel) {
            spawnParticles(serverLevel, facing, ratio, drained);
        }
    }

    /**
     * 应用流体效果（如药水效果、灭火等）到范围内的实体。
     */
    protected void applyEffect(Level level, Direction facing, float ratio, FluidStack drained, OpenPipeEffectHandler handler) {
        AABB aabb = buildAABB(blockEntity.getBlockPos(), facing, ratio);
        handler.apply(level, aabb, drained);
    }

    /**
     * 生成 Create 的 FLUID_PARTICLE 粒子，模拟喷洒锥形。
     * 粒子数量根据最近玩家的距离动态调整，确保远处也能观察到工作状态。
     */
    protected void spawnParticles(ServerLevel serverLevel, Direction facing, float ratio, FluidStack drained) {
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 origin = Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));

        double distanceFactor = 0.0;
        Player nearestPlayer = serverLevel.getNearestPlayer(origin.x, origin.y, origin.z, 32.0, false);
        if (nearestPlayer != null) {
            double dist = Math.sqrt(nearestPlayer.distanceToSqr(origin.x, origin.y, origin.z));
            if (dist <= 8.0) {
                distanceFactor = 1.0;
            } else if (dist < 32.0) {
                distanceFactor = 1.0 - (dist - 8.0) / (32.0 - 8.0);
            }
        }

        int baseCount = Math.max(4, (int) (ratio * 20));
        int count = (int) (baseCount * distanceFactor);
        if (count < 2) count = 2;

        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 up = Math.abs(dir.dot(new Vec3(0, 1, 0))) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize();
        up = right.cross(dir).normalize();

        FluidParticleData particleData = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), drained);

        for (int i = 0; i < count; i++) {
            double theta = Math.random() * Math.PI / 2;
            double phi = Math.random() * 2 * Math.PI;

            Vec3 randomDir = dir.scale(Math.cos(theta))
                    .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                    .add(up.scale(Math.sin(theta) * Math.sin(phi)));

            double speed = 0.1 + ratio * 0.5;
            Vec3 velocity = randomDir.scale(speed);

            serverLevel.sendParticles(
                    particleData,
                    origin.x, origin.y, origin.z,
                    0,
                    velocity.x, velocity.y, velocity.z,
                    1.0
            );
        }
    }

    /**
     * 构建非对称 AABB，仅向朝向方向扩展，反向无效果。
     * 最大范围硬编码为半整数格数，乘以 ratio 线性缩放。
     */
    protected AABB buildAABB(BlockPos pos, Direction dir, float ratio) {
        Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(dir.getNormal()).scale(0.5));
        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());

        Vec3 upAxis, rightAxis;
        if (dir.getAxis() == Direction.Axis.Y) {
            rightAxis = new Vec3(1, 0, 0);
            upAxis = new Vec3(0, 0, 1);
        } else {
            upAxis = new Vec3(0, 1, 0);
            rightAxis = normal.cross(upAxis).normalize();
            if (rightAxis.lengthSqr() < 1e-6) {
                rightAxis = new Vec3(0, 0, 1);
            }
            upAxis = rightAxis.cross(normal).normalize();
        }

        double lenForward, lenBack, lenUp, lenDown, lenRight, lenLeft;
        if (dir == Direction.UP) {
            lenRight = lenLeft = UP_HORIZONTAL;
            lenUp = UP_UP;
            lenDown = UP_DOWN;
            lenForward = lenBack = 0;
        } else if (dir == Direction.DOWN) {
            lenRight = lenLeft = DOWN_HORIZONTAL;
            lenUp = DOWN_UP;
            lenDown = DOWN_DOWN;
            lenForward = lenBack = 0;
        } else {
            lenForward = HORIZONTAL_HORIZONTAL;
            lenBack = 0;
            lenRight = HORIZONTAL_HORIZONTAL;
            lenLeft = HORIZONTAL_HORIZONTAL;
            lenUp = HORIZONTAL_UP;
            lenDown = HORIZONTAL_DOWN;
        }

        lenForward *= ratio;
        lenBack *= ratio;
        lenRight *= ratio;
        lenLeft *= ratio;
        lenUp *= ratio;
        lenDown *= ratio;

        double minX = center.x - lenLeft * rightAxis.x - lenBack * normal.x - lenDown * upAxis.x;
        double maxX = center.x + lenRight * rightAxis.x + lenForward * normal.x + lenUp * upAxis.x;
        double minY = center.y - lenLeft * rightAxis.y - lenBack * normal.y - lenDown * upAxis.y;
        double maxY = center.y + lenRight * rightAxis.y + lenForward * normal.y + lenUp * upAxis.y;
        double minZ = center.z - lenLeft * rightAxis.z - lenBack * normal.z - lenDown * upAxis.z;
        double maxZ = center.z + lenRight * rightAxis.z + lenForward * normal.z + lenUp * upAxis.z;

        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        AABB near = new AABB(center, center).expandTowards(0.5, 0.5, 0.5);
        return aabb.minmax(near);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}