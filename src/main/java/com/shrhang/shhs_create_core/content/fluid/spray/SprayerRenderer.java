package com.shrhang.shhs_create_core.content.fluid.spray;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;

public class SprayerRenderer extends KineticBlockEntityRenderer<SprayerBlockEntity> {

    public SprayerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(@NotNull SprayerBlockEntity be, float partialTicks, @NotNull PoseStack ms,
                              @NotNull MultiBufferSource buffer, int light, int overlay) {
        // 1. 渲染壳（父类默认使用 be.getBlockState()）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        // 2. 渲染轴（沿传动轴方向，即侧面轴）
        BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
                .setValue(BlockStateProperties.AXIS, getRotationAxisOf(be));
        SuperByteBuffer shaftBuffer = CachedBuffers.block(shaftState);
        standardKineticRotationTransform(shaftBuffer, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}