package io.github.shrhang.shhs_create_core.content.fluid.sprayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 喷洒器的渲染器，仅负责渲染传动杆。
 * 后续可由美工扩展添加更多细节。
 */
public class SprayerRenderer extends KineticBlockEntityRenderer<SprayerBlockEntity> {
    public SprayerRenderer(BlockEntityRendererProvider.Context context) {super(context);}
    @Override
    protected void renderSafe(SprayerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    @Override
    protected BlockState getRenderedBlockState(SprayerBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}