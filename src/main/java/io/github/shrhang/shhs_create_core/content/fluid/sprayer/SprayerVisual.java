package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

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
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerRenderHelper;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerRenderHelper.FaceRotation;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class SprayerVisual extends ShaftVisual<SprayerBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance gaugePositive;
    private final TransformedInstance gaugeNegative;
    private final TransformedInstance pointerPositive;
    private final TransformedInstance pointerNegative;

    private final FaceRotation positiveRotation;
    private final FaceRotation negativeRotation;
    private float lastPointerRotation = Float.NaN;

    public SprayerVisual(VisualizationContext context, SprayerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        BlockState state = blockEntity.getBlockState();
        var facing = state.getValue(SprayerBlock.FACING);
        var shaftAxis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        var gaugeAxis = SprayerRenderHelper.getGaugeAxis(facing.getAxis(), shaftAxis);

        // 缓存两个面的方向（保持不变）
        var positiveFace = Direction.get(AxisDirection.POSITIVE, gaugeAxis);
        var negativeFace = Direction.get(AxisDirection.NEGATIVE, gaugeAxis);
        positiveRotation = SprayerRenderHelper.getRotationForFace(positiveFace);
        negativeRotation = SprayerRenderHelper.getRotationForFace(negativeFace);

        var provider = instancerProvider();
        gaugePositive = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_GAUGE)).createInstance();
        gaugeNegative = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_GAUGE)).createInstance();
        pointerPositive = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_POINTER)).createInstance();
        pointerNegative = provider.instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ShHsPartialModels.SPRAYER_POINTER)).createInstance();

        transformGauges();
        transformPointers(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        transformPointers(ctx.partialTick());
    }

    private void transformGauges() {
        transformGauge(gaugePositive, positiveRotation);
        transformGauge(gaugeNegative, negativeRotation);
    }

    private void transformPointers(float partialTick) {
        float angle = blockEntity.getRenderedAngle(partialTick);
        float pointerRotation = Math.min(angle, SprayerBlockEntity.MAX_ANGLE);
        if (pointerRotation == lastPointerRotation) {
            return;
        }

        lastPointerRotation = pointerRotation;
        transformPointer(pointerPositive, positiveRotation, pointerRotation);
        transformPointer(pointerNegative, negativeRotation, pointerRotation);
    }

    private void transformGauge(TransformedInstance gauge, FaceRotation rotation) {
        gauge.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(rotation.yDegrees())
                .rotateXDegrees(rotation.xDegrees())
                .uncenter()
                .setChanged();
    }

    private void transformPointer(TransformedInstance pointer, FaceRotation rotation, float pointerRotation) {
        pointer.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(rotation.yDegrees())
                .rotateXDegrees(rotation.xDegrees())
                .rotateZDegrees(pointerRotation)
                .uncenter()
                .setChanged();
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