package com.shrhang.shhs_create_core.content.util;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 喷洒辅助工具，提供 AABB 构建、效果应用和粒子生成等静态方法。
 * 供静态喷洒行为和移动喷洒行为复用。
 */
public class SprayHelper {
    // 各方向喷洒范围参数（与 SprayBehaviour 保持一致）
    private static final double UP_HORIZONTAL = 8.0;
    private static final double UP_UP = 2.0;
    private static final double UP_DOWN = 0.0;
    private static final double DOWN_HORIZONTAL = 8.0;
    private static final double DOWN_UP = 0.0;
    private static final double DOWN_DOWN = 10.5;
    private static final double HORIZONTAL_HORIZONTAL = 8.0;
    private static final double HORIZONTAL_UP = 2.0;
    private static final double HORIZONTAL_DOWN = 6.0;

    /**
     * 构建喷洒影响的 AABB。
     *
     * @param center 喷洒中心世界坐标
     * @param dir    喷洒方向
     * @param ratio  强度比例 (0~1)
     * @return 受影响区域的 AABB
     */
    public static AABB buildAABB(Vec3 center, Direction dir, float ratio) {
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

    /**
     * 应用流体效果到指定 AABB 区域。
     *
     * @param level 世界
     * @param aabb  作用区域
     * @param fluid 要应用的流体
     */
    public static void applyEffect(Level level, AABB aabb, FluidStack fluid) {
        if (fluid.isEmpty()) return;
        OpenPipeEffectHandler handler = OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
        if (handler == null) return;
        handler.apply(level, aabb, fluid);
    }

    /**
     * 在服务端生成喷洒粒子。
     *
     * @param level  服务端世界
     * @param origin 喷洒起点（世界坐标）
     * @param dir    喷洒方向
     * @param ratio  强度比例
     * @param fluid  流体
     */
    public static void spawnParticles(ServerLevel level, Vec3 origin, Direction dir, float ratio, FluidStack fluid) {
        // 根据最近玩家距离调整粒子数量
        double distanceFactor = 0.0;
        Player nearestPlayer = level.getNearestPlayer(origin.x, origin.y, origin.z, 32.0, false);
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

        Vec3 dirVec = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 up = Math.abs(dirVec.dot(new Vec3(0, 1, 0))) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = dirVec.cross(up).normalize();
        up = right.cross(dirVec).normalize();

        FluidParticleData particleData = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), fluid);

        for (int i = 0; i < count; i++) {
            double theta = Math.random() * Math.PI / 2;
            double phi = Math.random() * 2 * Math.PI;

            Vec3 randomDir = dirVec.scale(Math.cos(theta))
                    .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                    .add(up.scale(Math.sin(theta) * Math.sin(phi)));

            double speed = 0.1 + ratio * 0.5;
            Vec3 velocity = randomDir.scale(speed);

            level.sendParticles(
                    particleData,
                    origin.x, origin.y, origin.z,
                    0,
                    velocity.x, velocity.y, velocity.z,
                    1.0
            );
        }
    }
}