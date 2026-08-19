package io.github.shrhang.shhs_create_core.content.util;

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
    private static final double UP_HORIZONTAL = 6.0;      // 原 8.0
    private static final double UP_UP = 4.0;
    private static final double UP_DOWN = 0.0;
    private static final double DOWN_HORIZONTAL = 6.0;    // 原 8.0
    private static final double DOWN_UP = 0.0;
    private static final double DOWN_DOWN = 10.5;
    private static final double HORIZONTAL_HORIZONTAL = 6.0; // 原 8.0
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
        // 标准化方向向量
        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());

        // 构建两个正交的水平向量（垂直于 normal）
        Vec3 right, up;
        if (dir.getAxis() == Direction.Axis.Y) {
            // 上下方向，水平面为 XZ 平面
            right = new Vec3(1, 0, 0);
            up = new Vec3(0, 0, 1);
        } else {
            // 水平方向，一个水平向量为 Y 轴，另一个通过叉积计算
            up = new Vec3(0, 1, 0);
            right = normal.cross(up).normalize();
            if (right.lengthSqr() < 1e-6) {
                right = new Vec3(0, 0, 1);
            }
            up = right.cross(normal).normalize();
        }

        double minX, minY, minZ, maxX, maxY, maxZ;
        if (dir == Direction.UP) {
            double horiz = UP_HORIZONTAL * ratio;
            // 水平两个轴（X 和 Z）对称扩展
            minX = center.x - horiz;
            maxX = center.x + horiz;
            minZ = center.z - horiz;
            maxZ = center.z + horiz;
            // 垂直方向（Y轴）
            minY = center.y - UP_DOWN * ratio;
            maxY = center.y + UP_UP * ratio;
        } else if (dir == Direction.DOWN) {
            double horiz = DOWN_HORIZONTAL * ratio;
            minX = center.x - horiz;
            maxX = center.x + horiz;
            minZ = center.z - horiz;
            maxZ = center.z + horiz;
            minY = center.y - DOWN_DOWN * ratio;
            maxY = center.y + DOWN_UP * ratio;
        } else {
            // 水平方向喷洒（非 Y 轴）
            double lenForward = HORIZONTAL_HORIZONTAL * ratio;
            double lenBack = 0;
            double lenRight = HORIZONTAL_HORIZONTAL * ratio;
            double lenLeft = HORIZONTAL_HORIZONTAL * ratio;
            double lenUp = HORIZONTAL_UP * ratio;
            double lenDown = HORIZONTAL_DOWN * ratio;

            double fx = lenForward * normal.x;
            double fy = lenForward * normal.y;
            double fz = lenForward * normal.z;
            double bx = lenBack * normal.x;
            double by = lenBack * normal.y;
            double bz = lenBack * normal.z;
            double rx = lenRight * right.x;
            double ry = lenRight * right.y;
            double rz = lenRight * right.z;
            double lx = lenLeft * right.x;
            double ly = lenLeft * right.y;
            double lz = lenLeft * right.z;
            double ux = lenUp * up.x;
            double uy = lenUp * up.y;
            double uz = lenUp * up.z;
            double dx = lenDown * up.x;
            double dy = lenDown * up.y;
            double dz = lenDown * up.z;

            minX = center.x - lx - bx - dx;
            maxX = center.x + rx + fx + ux;
            minY = center.y - ly - by - dy;
            maxY = center.y + ry + fy + uy;
            minZ = center.z - lz - bz - dz;
            maxZ = center.z + rz + fz + uz;
        }
        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        return aabb;
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

            double speed = 0.1 + ratio * 0.7;
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