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

public class SprayerRenderer extends KineticBlockEntityRenderer<SprayerBlockEntity> {

    public SprayerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SprayerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
                .setValue(BlockStateProperties.AXIS, getRotationAxisOf(be));
        SuperByteBuffer shaftBuffer = CachedBuffers.block(shaftState);
        standardKineticRotationTransform(shaftBuffer, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}