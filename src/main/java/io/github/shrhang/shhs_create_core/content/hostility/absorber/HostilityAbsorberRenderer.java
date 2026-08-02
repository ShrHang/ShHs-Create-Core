package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class HostilityAbsorberRenderer extends KineticBlockEntityRenderer<HostilityAbsorberBlockEntity> {
    public HostilityAbsorberRenderer(BlockEntityRendererProvider.Context context) {super(context);}

    @Override
    protected SuperByteBuffer getRotatedModel(HostilityAbsorberBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.MILLSTONE_COG, state);
    }
}