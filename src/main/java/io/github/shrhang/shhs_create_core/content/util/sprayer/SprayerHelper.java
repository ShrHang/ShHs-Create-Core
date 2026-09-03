package io.github.shrhang.shhs_create_core.content.util.sprayer;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.EnumMap;

/**
 * 喷洒辅助工具，提供 AABB 构建、效果应用和粒子生成等静态方法。
 * 供静态喷洒行为和移动喷洒行为复用。
 */
public class SprayerHelper {
    private static final double FACING = 6.0;
    private static final double PERPENDICULAR = 4.5;
    private static final double GRAVITY_UP = -1.0;
    private static final double GRAVITY_DOWN = 5.0;
    private static final EnumMap<Direction, SprayShape> SHAPES = createShapes();

    /**
     * 构建喷洒影响的 AABB。
     *
     * @param center 喷洒中心世界坐标
     * @param dir    喷洒方向
     * @param ratio  强度比例 (0~1)
     * @return 受影响区域的 AABB
     */
    public static AABB buildAABB(Vec3 center, Direction dir, float ratio) {
        SprayShape shape = SHAPES.get(dir);
        return new AABB(
                center.x - shape.west * ratio,
                center.y - shape.down * ratio,
                center.z - shape.north * ratio,
                center.x + shape.east * ratio,
                center.y + shape.up * ratio,
                center.z + shape.south * ratio
        );
    }

    public static AABB buildAABB(BlockPos pos, Direction facing, float ratio) {
        return buildAABB(getSprayCenter(pos, facing), facing, ratio);
    }

    public static AABB buildAABBFromAngle(BlockPos pos, Direction facing, float angle, float maxAngle) {
        return buildAABB(pos, facing, getAngleRatio(angle, maxAngle));
    }

    public static Vec3 getSprayCenter(BlockPos pos, Direction facing) {
        return getSprayCenter(Vec3.atCenterOf(pos), facing);
    }

    public static Vec3 getSprayCenter(Vec3 blockCenter, Direction facing) {
        return blockCenter.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
    }

    public static float getAngleRatio(float angle, float maxAngle) {
        return Mth.clamp(angle / maxAngle, 0f, 1f);
    }

    public static int getMaxConsumption(float angle, float maxAngle, int maxConsumption) {
        return (int) (maxConsumption * getAngleRatio(angle, maxAngle));
    }

    public static float getFluidRatio(int amount, int maxConsumption) {
        return Mth.clamp((float) amount / maxConsumption, 0f, 1f);
    }

    private static EnumMap<Direction, SprayShape> createShapes() {
        EnumMap<Direction, SprayShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            shapes.put(facing, createShape(facing));
        }
        return shapes;
    }

    private static SprayShape createShape(Direction facing) {
        return new SprayShape(
                getExtension(facing, Direction.EAST),
                getExtension(facing, Direction.WEST),
                getExtension(facing, Direction.UP),
                getExtension(facing, Direction.DOWN),
                getExtension(facing, Direction.SOUTH),
                getExtension(facing, Direction.NORTH)
        );
    }

    private static double getExtension(Direction facing, Direction direction) {
        double extension = getBaseExtension(facing, direction);
        if (extension <= 0) return 0.0;
        if (direction == Direction.UP) {
            return extension + GRAVITY_UP;
        }
        if (direction == Direction.DOWN) {
            return extension + GRAVITY_DOWN;
        }
        return extension;
    }

    private static double getBaseExtension(Direction facing, Direction direction) {
        if (direction == facing) {
            return FACING;
        }
        if (direction.getAxis() != facing.getAxis()) {
            return PERPENDICULAR;
        }
        return 0.0;
    }

    private record SprayShape(double east, double west, double up, double down, double south, double north) {
    }

    /**
     * 应用流体效果到指定 AABB 区域。
     *
     * @param level 世界
     * @param aabb  作用区域
     * @param fluid 要应用的流体
     */
    public static void applyEffect(OpenPipeEffectHandler handler, Level level, AABB aabb, FluidStack fluid) {
        if (fluid.isEmpty()) return;
        handler.apply(level, aabb, fluid);
    }

    public static OpenPipeEffectHandler getEffectHandler(FluidStack fluid) {
        if (fluid.isEmpty()) return null;
        return OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
    }

    /**
     * 在客户端生成喷洒粒子（直接本地生成，不通过网络发送）。
     * 原本服务端调用改为客户端调用，因此参数改为 Level（客户端的 Level）。
     *
     * @param level       客户端世界
     * @param origin      喷洒起点（世界坐标）
     * @param dir         喷洒方向
     * @param ratio       强度比例
     * @param fluid       流体
     * @param countScale  粒子数量缩放因子（0~1），泛用性强，可用于距离衰减、性能设置等
     */
    public static void spawnParticles(Level level, Vec3 origin, Direction dir, float ratio, FluidStack fluid, float countScale) {
        // 基础粒子数，随 ratio 增大而增多
        int baseCount = Math.max(4, (int) (ratio * 20));
        // 应用数量缩放因子，保证至少有 2 个粒子（避免完全消失）
        int count = Math.max(2, (int) (baseCount * countScale));

        Vec3 dirVec = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 up = Math.abs(dirVec.dot(new Vec3(0, 1, 0))) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = dirVec.cross(up).normalize();
        up = right.cross(dirVec).normalize();

        FluidParticleData particleData = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), fluid);
        RandomSource random = level.random;

        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI / 2;
            double phi = random.nextDouble() * 2 * Math.PI;

            Vec3 randomDir = dirVec.scale(Math.cos(theta))
                    .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                    .add(up.scale(Math.sin(theta) * Math.sin(phi)));

            double speed = 0.1 + ratio * 0.7;
            Vec3 velocity = randomDir.scale(speed);

            level.addParticle(
                    particleData,
                    origin.x, origin.y, origin.z,
                    velocity.x, velocity.y, velocity.z
            );
        }
    }
}