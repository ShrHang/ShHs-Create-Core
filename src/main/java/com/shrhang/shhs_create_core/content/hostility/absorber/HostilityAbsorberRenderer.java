package com.shrhang.shhs_create_core.content.hostility.absorber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class HostilityAbsorberRenderer extends KineticBlockEntityRenderer<HostilityAbsorberBlockEntity> {
    public HostilityAbsorberRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
    @Override
    protected void renderSafe(@NotNull HostilityAbsorberBlockEntity be, float partialTicks, @NotNull PoseStack ms,
                              @NotNull MultiBufferSource buffer, int light, int overlay) {
        // 1. 调用父类渲染（如果有其它模型需要）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        // 2. 手动获取齿轮模型并渲染
        BlockState state = be.getBlockState();
        SuperByteBuffer gearBuffer = CachedBuffers.partial(AllPartialModels.MILLSTONE_COG, state);
        standardKineticRotationTransform(gearBuffer, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}