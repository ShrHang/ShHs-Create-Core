package com.shrhang.shhs_create_core.content.hostility.absorber;

import org.jetbrains.annotations.NotNull;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 恶意吸收器的渲染器，直接复用石磨的齿轮模型（MILLSTONE_COG），实现旋转动画。
 */
public class HostilityAbsorberRenderer extends KineticBlockEntityRenderer<HostilityAbsorberBlockEntity> {
    public HostilityAbsorberRenderer(@NotNull BlockEntityRendererProvider.Context context) {
        super(context);
    }
    /**
     * 返回旋转后的模型，使用石磨齿轮部分模型。
     */
    @Override
    @NotNull
    protected SuperByteBuffer getRotatedModel(@NotNull HostilityAbsorberBlockEntity be, @NotNull BlockState state) {
        return CachedBuffers.partial(AllPartialModels.MILLSTONE_COG, state);
    }
}