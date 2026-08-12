package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import java.lang.reflect.Field;
import java.util.function.Consumer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import io.github.shrhang.shhs_create_core.content.registries.ShHsPartialModels;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 喷洒器的飞轮可视化对象，负责渲染外壳（gauge）和指针（pointer）。
 * 变换逻辑与 SprayerRenderer 的指针渲染完全一致：动态获取面朝向角度，一次 center/uncenter。
 */
public class SprayerVisual extends ShaftVisual<SprayerBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance gaugePositive;
    private final TransformedInstance gaugeNegative;
    private final TransformedInstance pointerPositive;
    private final TransformedInstance pointerNegative;

    // 缓存两个面的方向（保持不变）
    private final Direction positiveFace;
    private final Direction negativeFace;

    public SprayerVisual(VisualizationContext context, SprayerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        // 移除父类传动轴（避免干扰）
        removeParentShaft();

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(SprayerBlock.FACING);
        Axis shaftAxis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        Axis gaugeAxis = getGaugeAxis(facing.getAxis(), shaftAxis);

        positiveFace = Direction.get(AxisDirection.POSITIVE, gaugeAxis);
        negativeFace = Direction.get(AxisDirection.NEGATIVE, gaugeAxis);

        var provider = instancerProvider();
        gaugePositive = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_GAUGE)).createInstance();
        gaugeNegative = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_GAUGE)).createInstance();
        pointerPositive = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_POINTER)).createInstance();
        pointerNegative = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_POINTER)).createInstance();

        transform(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        transform(ctx.partialTick());
    }

    private static Axis getGaugeAxis(Axis facingAxis, Axis shaftAxis) {
        for (Axis axis : Axis.values()) {
            if (axis != facingAxis && axis != shaftAxis) {
                return axis;
            }
        }
        return Axis.Y;
    }

    /**
     * 与 SprayerRenderer 完全相同的面朝向角度计算（静态，供两者共用）。
     */
    private static float[] getRotationForFace(Direction face) {
        return switch (face) {
            case NORTH -> new float[]{0, 0};
            case SOUTH -> new float[]{180, 0};
            case EAST  -> new float[]{90, 0};
            case WEST  -> new float[]{270, 0};
            case UP    -> new float[]{0, 90};
            case DOWN  -> new float[]{0, 270};
        };
    }

    private void transform(float partialTick) {
        float angle = blockEntity.getRenderedAngle(partialTick);
        float pointerRotation = angle;
        pointerRotation = Math.min(pointerRotation, 270);

        // 获取两个面的角度
        float[] posAngles = getRotationForFace(positiveFace);
        float[] negAngles = getRotationForFace(negativeFace);

        // ---- 外壳（gauge）变换：仅旋转到面方向 ----
        gaugePositive.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(posAngles[0])
                .rotateXDegrees(posAngles[1])
                .uncenter()
                .setChanged();

        gaugeNegative.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(negAngles[0])
                .rotateXDegrees(negAngles[1])
                .uncenter()
                .setChanged();

        // ---- 指针（pointer）变换：面朝向 + Z 轴偏转 ----
        pointerPositive.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(posAngles[0])
                .rotateXDegrees(posAngles[1])
                .rotateZDegrees(pointerRotation)
                .uncenter()
                .setChanged();

        pointerNegative.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(negAngles[0])
                .rotateXDegrees(negAngles[1])
                .rotateZDegrees(pointerRotation)
                .uncenter()
                .setChanged();
    }

    /**
     * 通过反射移除父类 ShaftVisual 的传动轴实例，避免竖直方向出现多余渲染。
     */
    private void removeParentShaft() {
        try {
            Field shaftField = ShaftVisual.class.getDeclaredField("shaft");
            shaftField.setAccessible(true);
            TransformedInstance shaft = (TransformedInstance) shaftField.get(this);
            if (shaft != null) {
                shaft.delete();
                shaftField.set(this, null);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 忽略
        }
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(gaugePositive, gaugeNegative, pointerPositive, pointerNegative);
    }

    @Override
    protected void _delete() {
        super._delete();
        gaugePositive.delete();
        gaugeNegative.delete();
        pointerPositive.delete();
        pointerNegative.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(gaugePositive);
        consumer.accept(gaugeNegative);
        consumer.accept(pointerPositive);
        consumer.accept(pointerNegative);
    }
}