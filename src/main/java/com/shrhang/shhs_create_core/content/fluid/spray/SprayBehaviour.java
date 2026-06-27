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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 喷洒器行为：每 tick 消耗至多 4mB 流体，并按实际消耗比例线性衰减喷洒范围，
 * 调用 OpenPipeEffectHandler 应用效果，同时发射 Create 的 FLUID_PARTICLE 粒子，
 * 粒子在朝向方向的前半球内均匀分布，形成包络。
 * 喷洒范围非对称，直接硬编码最大几何尺寸（格数）。
 */
public class SprayBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<SprayBehaviour> TYPE = new BehaviourType<>();

    private static final int MAX_CONSUMPTION = 4;
    // 各方向最大范围（格数），乘以 ratio 得实际范围
    // 竖直朝上
    private static final double UP_HORIZONTAL = 8.0;
    private static final double UP_UP = 2.0;
    private static final double UP_DOWN = 0.0;

    // 竖直朝下
    private static final double DOWN_HORIZONTAL = 8.0;
    private static final double DOWN_UP = 0.0;
    private static final double DOWN_DOWN = 10.5;

    // 水平朝前（统一水平半径 8，竖直非对称）
    private static final double HORIZONTAL_HORIZONTAL = 8.0;
    private static final double HORIZONTAL_UP = 2.0;
    private static final double HORIZONTAL_DOWN = 6.0;

    private final FluidTank tank;

    public SprayBehaviour(SmartBlockEntity be, FluidTank tank) {
        super(be);
        this.tank = tank;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getWorld();
        if (level == null || level.isClientSide()) return;
        level.getGameTime();

        // 尝试进行喷洒
        trySpray(level);
    }

    /**
     * 尝试喷洒：消耗流体，计算比例，应用效果和粒子。
     */
    private void trySpray(Level level) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) return;

        int toDrain = Math.min(MAX_CONSUMPTION, fluid.getAmount());
        FluidStack drained = tank.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        float ratio = (float) drained.getAmount() / MAX_CONSUMPTION;

        Direction facing = blockEntity.getBlockState().getValue(SprayerBlock.FACING);

        // 应用效果
        applyEffect(level, facing, ratio, drained);

        // 发射粒子（服务端）
        if (level instanceof ServerLevel serverLevel) {
            spawnParticles(serverLevel, facing, ratio, drained);
        }
    }

    /**
     * 应用流体效果（如药水效果、灭火等）。
     */
    private void applyEffect(Level level, Direction facing, float ratio, FluidStack drained) {
        AABB aabb = buildAABB(blockEntity.getBlockPos(), facing, ratio);
        OpenPipeEffectHandler handler = OpenPipeEffectHandler.REGISTRY.get(drained.getFluid());
        if (handler != null) {
            handler.apply(level, aabb, drained);
        }
    }

    /**
     * 发射 Create 的 FLUID_PARTICLE 粒子，形成锥形包络。
     */
    private void spawnParticles(ServerLevel serverLevel, Direction facing, float ratio, FluidStack drained) {
        Vec3 origin = Vec3.atCenterOf(blockEntity.getBlockPos())
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
        int count = Math.max(4, (int) (ratio * 20));

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
    private AABB buildAABB(BlockPos pos, Direction dir, float ratio) {
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