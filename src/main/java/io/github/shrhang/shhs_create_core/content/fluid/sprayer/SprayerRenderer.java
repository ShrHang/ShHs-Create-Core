package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import io.github.shrhang.shhs_create_core.content.registries.ShHsPartialModels;
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
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(SprayerBlock.FACING);
        Axis shaftAxis = getRotationAxisOf(be);
        Axis gaugeAxis = getGaugeAxis(facing.getAxis(), shaftAxis);

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

    private static Axis getGaugeAxis(Axis facingAxis, Axis shaftAxis) {
        for (Axis axis : Axis.values()) {
            if (axis != facingAxis && axis != shaftAxis) {
                return axis;
            }
        }
        return Axis.Y;
    }

    /**
     * 与 SprayerVisual 相似的面朝向角度计算。
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

    private static void renderGauge(BlockState state, Direction face, PoseStack ms,
                                    MultiBufferSource buffer, int light) {
        float[] angles = getRotationForFace(face);
        SuperByteBuffer gauge = CachedBuffers.partial(ShHsPartialModels.SPRAYER_GAUGE, state);
        gauge.center()
                .rotateYDegrees(angles[0])
                .rotateXDegrees(angles[1])
                .uncenter()
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    private static void renderPointer(SprayerBlockEntity be, float partialTicks, BlockState state,
                                      Direction face, PoseStack ms, MultiBufferSource buffer, int light) {
        float angle = be.getRenderedAngle(partialTicks);
        float pointerRotation = angle;
        pointerRotation = Math.min(pointerRotation, 270);

        float[] angles = getRotationForFace(face);
        SuperByteBuffer pointer = CachedBuffers.partial(ShHsPartialModels.SPRAYER_POINTER, state);
        pointer.center()
                .rotateYDegrees(angles[0])
                .rotateXDegrees(angles[1])
                .rotateZDegrees(pointerRotation)
                .uncenter()
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }
}