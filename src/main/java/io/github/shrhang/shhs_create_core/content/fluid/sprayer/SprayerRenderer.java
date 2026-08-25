package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import io.github.shrhang.shhs_create_core.content.registries.ShHsPartialModels;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerRenderHelper;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerRenderHelper.FaceRotation;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 喷洒器的渲染器，应与 Flywheel Visual 一致。
 */
public class SprayerRenderer extends KineticBlockEntityRenderer<SprayerBlockEntity> {

    public SprayerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SprayerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) return;

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(SprayerBlock.FACING);
        Axis shaftAxis = getRotationAxisOf(be);
        Axis gaugeAxis = SprayerRenderHelper.getGaugeAxis(facing.getAxis(), shaftAxis);

        Direction positiveFace = Direction.get(AxisDirection.POSITIVE, gaugeAxis);
        Direction negativeFace = Direction.get(AxisDirection.NEGATIVE, gaugeAxis);

        renderGauge(state, positiveFace, ms, buffer, light);
        renderGauge(state, negativeFace, ms, buffer, light);
        renderPointer(be, partialTicks, state, positiveFace, ms, buffer, light);
        renderPointer(be, partialTicks, state, negativeFace, ms, buffer, light);
    }

    @Override
    protected BlockState getRenderedBlockState(SprayerBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

    private static void renderGauge(BlockState state, Direction face, PoseStack ms,
                                    MultiBufferSource buffer, int light) {
        FaceRotation rotation = SprayerRenderHelper.getRotationForFace(face);
        SuperByteBuffer gauge = CachedBuffers.partial(ShHsPartialModels.SPRAYER_GAUGE, state);
        gauge.center()
                .rotateYDegrees(rotation.yDegrees())
                .rotateXDegrees(rotation.xDegrees())
                .uncenter()
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    private static void renderPointer(SprayerBlockEntity be, float partialTicks, BlockState state,
                                      Direction face, PoseStack ms, MultiBufferSource buffer, int light) {
        float pointerRotation = be.getRenderedAngle(partialTicks);
        pointerRotation = Math.min(pointerRotation, SprayerBlockEntity.MAX_ANGLE);

        FaceRotation rotation = SprayerRenderHelper.getRotationForFace(face);
        SuperByteBuffer pointer = CachedBuffers.partial(ShHsPartialModels.SPRAYER_POINTER, state);
        pointer.center()
                .rotateYDegrees(rotation.yDegrees())
                .rotateXDegrees(rotation.xDegrees())
                .rotateZDegrees(pointerRotation)
                .uncenter()
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }
}
