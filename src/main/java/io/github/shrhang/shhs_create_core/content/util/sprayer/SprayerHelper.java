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

    // ----- 原有 AABB 构建方法（基于 Direction） -----

    /**
     * 构建喷洒影响的 AABB（基于轴向方向）。
     *
     * @param center 喷洒中心世界坐标
     * @param dir    喷洒轴向方向
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

    /**
     * 便捷方法：基于 BlockPos 构建 AABB。
     *
     * @param pos    方块位置
     * @param facing 喷洒轴向方向
     * @param ratio  强度比例
     * @return AABB
     */
    public static AABB buildAABB(BlockPos pos, Direction facing, float ratio) {
        return buildAABB(getSprayCenter(pos, facing), facing, ratio);
    }

    /**
     * 便捷方法：基于角度计算比例，再构建 AABB。
     *
     * @param pos      方块位置
     * @param facing   轴向方向
     * @param angle    当前角度
     * @param maxAngle 最大角度
     * @return AABB
     */
    public static AABB buildAABBFromAngle(BlockPos pos, Direction facing, float angle, float maxAngle) {
        return buildAABB(pos, facing, getAngleRatio(angle, maxAngle));
    }

    // ----- 新增：基于任意方向向量的 AABB 构建 -----

    /**
     * 构建喷洒影响的 AABB（基于任意方向向量）。
     * 内部会将方向向量映射到最接近的轴向方向，然后调用轴向版本。
     *
     * @param center  喷洒中心世界坐标
     * @param dirVec  实际喷洒方向向量（不必归一化）
     * @param ratio   强度比例 (0~1)
     * @return 受影响区域的 AABB
     */
    public static AABB buildAABB(Vec3 center, Vec3 dirVec, float ratio) {
        Direction axial = getAxialDirection(dirVec);
        return buildAABB(center, axial, ratio);
    }

    // ----- 喷洒中心计算 -----

    /**
     * 计算喷洒中心（世界坐标），从方块中心沿方向偏移 0.5 格。
     *
     * @param pos    方块位置
     * @param facing 方向
     * @return 喷洒中心坐标
     */
    public static Vec3 getSprayCenter(BlockPos pos, Direction facing) {
        return getSprayCenter(Vec3.atCenterOf(pos), facing);
    }

    /**
     * 基于已有点向量计算喷洒中心。
     *
     * @param blockCenter 方块中心坐标
     * @param facing      方向
     * @return 喷洒中心
     */
    public static Vec3 getSprayCenter(Vec3 blockCenter, Direction facing) {
        return blockCenter.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
    }

    // ----- 角度/比例转换 -----

    /**
     * 将角度转换为比例（0~1）。
     *
     * @param angle    当前角度
     * @param maxAngle 最大角度
     * @return 比例
     */
    public static float getAngleRatio(float angle, float maxAngle) {
        return Mth.clamp(angle / maxAngle, 0f, 1f);
    }

    /**
     * 根据角度计算最大可消耗量。
     *
     * @param angle          当前角度
     * @param maxAngle       最大角度
     * @param maxConsumption 最大消耗基数
     * @return 实际最大消耗量
     */
    public static int getMaxConsumption(float angle, float maxAngle, int maxConsumption) {
        return (int) (maxConsumption * getAngleRatio(angle, maxAngle));
    }

    /**
     * 根据实际消耗量与最大消耗量计算比例。
     *
     * @param amount          实际消耗量
     * @param maxConsumption  最大消耗基数
     * @return 比例 (0~1)
     */
    public static float getFluidRatio(int amount, int maxConsumption) {
        return Mth.clamp((float) amount / maxConsumption, 0f, 1f);
    }

    // ----- 方向映射工具（用于旋转后向量 -> 轴向方向） -----

    /**
     * 将任意方向向量映射到最接近的轴向方向（上/下/东南西北）。
     * 规则：垂直分量 > sin(45°) 时映射为 UP，< -sin(45°) 为 DOWN，
     * 否则计算水平偏航角，选择最近的水平方向。
     *
     * @param dirVec 方向向量（不必归一化）
     * @return 最近的轴向方向
     */
    public static Direction getAxialDirection(Vec3 dirVec) {
        double len = dirVec.length();
        if (len < 1e-8) {
            return Direction.NORTH; // fallback
        }
        double vert = dirVec.y / len;
        double sin45 = Math.sin(Math.PI / 4);
        if (vert > sin45) {
            return Direction.UP;
        }
        if (vert < -sin45) {
            return Direction.DOWN;
        }
        // 水平方向：计算偏航角
        double horizLen = Math.sqrt(dirVec.x * dirVec.x + dirVec.z * dirVec.z);
        if (horizLen < 1e-8) {
            return Direction.NORTH;
        }
        // 偏航角：从正Z轴（南）逆时针到向量在水平面的投影
        double angle = Math.atan2(dirVec.x, dirVec.z); // 范围 -PI ~ PI
        angle = (angle + 2 * Math.PI) % (2 * Math.PI); // 归一化到 [0, 2PI)
        // 四个水平方向的角度（弧度）：南=0, 东=PI/2, 北=PI, 西=3*PI/2
        double[] dirAngles = {0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};
        Direction[] dirs = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double diff = Math.abs(angle - dirAngles[i]);
            diff = Math.min(diff, 2 * Math.PI - diff);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return dirs[best];
    }

    // ----- 形状数据内部结构 -----

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

    // ----- 效果应用 -----

    /**
     * 应用流体效果到指定 AABB 区域。
     *
     * @param handler 效果处理器
     * @param level   世界
     * @param aabb    作用区域
     * @param fluid   要应用的流体
     */
    public static void applyEffect(OpenPipeEffectHandler handler, Level level, AABB aabb, FluidStack fluid) {
        if (fluid.isEmpty()) return;
        handler.apply(level, aabb, fluid);
    }

    /**
     * 根据流体获取对应的效果处理器。
     *
     * @param fluid 流体
     * @return 处理器，若未注册则返回 null
     */
    public static OpenPipeEffectHandler getEffectHandler(FluidStack fluid) {
        if (fluid.isEmpty()) return null;
        return OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
    }

    // ----- 粒子生成（原有基于 Direction 的重载） -----

    /**
     * 在客户端生成喷洒粒子（基于轴向方向）。
     * 内部调用新重载，将 Direction 转为向量。
     *
     * @param level       客户端世界
     * @param origin      喷洒起点（世界坐标）
     * @param dir         喷洒轴向方向
     * @param ratio       强度比例
     * @param fluid       流体
     * @param countScale  粒子数量缩放因子（0~1）
     */
    public static void spawnParticles(Level level, Vec3 origin, Direction dir, float ratio,
                                      FluidStack fluid, float countScale) {
        Vec3 dirVec = Vec3.atLowerCornerOf(dir.getNormal());
        spawnParticles(level, origin, dirVec, ratio, fluid, countScale);
    }

    // ----- 新增：基于任意方向向量的粒子生成 -----

    /**
     * 在客户端生成喷洒粒子（基于任意方向向量）。
     * 粒子的速度方向以 dirVec 为中心随机散布，数量由 ratio 和 countScale 控制。
     *
     * @param level       客户端世界
     * @param origin      喷洒起点（世界坐标）
     * @param dirVec      实际喷洒方向向量（不必归一化）
     * @param ratio       强度比例
     * @param fluid       流体
     * @param countScale  粒子数量缩放因子（0~1），可用于距离衰减等
     */
    public static void spawnParticles(Level level, Vec3 origin, Vec3 dirVec, float ratio,
                                      FluidStack fluid, float countScale) {
        Vec3 dir = dirVec.normalize();
        // 基础粒子数，随 ratio 增大而增多
        int baseCount = Math.max(4, (int) (ratio * 20));
        int count = Math.max(2, (int) (baseCount * countScale));

        // 构造两个正交向量（右向量和上向量），用于随机散布
        Vec3 up = Math.abs(dir.dot(new Vec3(0, 1, 0))) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize();
        up = right.cross(dir).normalize();

        FluidParticleData particleData = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), fluid);
        RandomSource random = level.random;

        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI / 2;
            double phi = random.nextDouble() * 2 * Math.PI;

            Vec3 randomDir = dir.scale(Math.cos(theta))
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