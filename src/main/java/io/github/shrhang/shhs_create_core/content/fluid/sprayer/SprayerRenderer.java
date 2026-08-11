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
 * 喷洒器的渲染器，负责渲染传动杆、仪表和指针。
 */
public class SprayerRenderer extends KineticBlockEntityRenderer<SprayerBlockEntity> {
    public SprayerRenderer(BlockEntityRendererProvider.Context context) {super(context);}
    @Override
    protected void renderSafe(SprayerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        Axis gaugeAxis = getGaugeAxis(state.getValue(SprayerBlock.FACING).getAxis(), getRotationAxisOf(be));

        renderDial(be, partialTicks, state, Direction.get(AxisDirection.POSITIVE, gaugeAxis), ms, buffer, light);
        renderDial(be, partialTicks, state, Direction.get(AxisDirection.NEGATIVE, gaugeAxis), ms, buffer, light);
    }

    @Override
    protected BlockState getRenderedBlockState(SprayerBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

    private static void renderDial(SprayerBlockEntity be, float partialTicks, BlockState state, Direction face,
                                   PoseStack ms, MultiBufferSource buffer, int light) {
        renderGauge(state, face, ms, buffer, light);
        renderPointer(be, partialTicks, state, face, ms, buffer, light);
    }

    private static void renderGauge(BlockState state, Direction face, PoseStack ms, MultiBufferSource buffer, int light) {
        SuperByteBuffer gauge = CachedBuffers.partial(ShHsPartialModels.SPRAYER_GAUGE, state);
        rotateToFace(gauge, face)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    private static void renderPointer(SprayerBlockEntity be, float partialTicks, BlockState state, Direction face,
                                      PoseStack ms, MultiBufferSource buffer, int light) {
        SuperByteBuffer pointer = CachedBuffers.partial(ShHsPartialModels.SPRAYER_POINTER, state);
        rotateToFace(pointer, face)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    private static Axis getGaugeAxis(Axis facingAxis, Axis shaftAxis) {
        for (Axis axis : Axis.values()) {
            if (axis != facingAxis && axis != shaftAxis) {
                return axis;
            }
        }
        return Axis.Y;
    }

    private static SuperByteBuffer rotateToFace(SuperByteBuffer buffer, Direction face) {
        return switch (face) {
            case NORTH -> buffer;
            case SOUTH -> buffer.center()
                    .rotateYDegrees(180)
                    .uncenter();
            case EAST -> buffer.center()
                    .rotateYDegrees(90)
                    .uncenter();
            case WEST -> buffer.center()
                    .rotateYDegrees(270)
                    .uncenter();
            case UP -> buffer.center()
                    .rotateXDegrees(90)
                    .uncenter();
            case DOWN -> buffer.center()
                    .rotateXDegrees(270)
                    .uncenter();
        };
    }
}
