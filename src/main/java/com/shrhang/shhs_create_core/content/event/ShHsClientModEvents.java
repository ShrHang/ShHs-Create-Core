package com.shrhang.shhs_create_core.content.event;

import com.shrhang.shhs_create_core.content.fluid.sprayer.SprayerRenderer;
import com.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberRenderer;
import com.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ShHsClientModEvents {

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ShHsBlockEntityTypes.SPRAYER.get(),
                SprayerRenderer::new
        );
        event.registerBlockEntityRenderer(
                ShHsBlockEntityTypes.HOSTILITY_ABSORBER_BE.get(),
                HostilityAbsorberRenderer::new
        );
    }
}