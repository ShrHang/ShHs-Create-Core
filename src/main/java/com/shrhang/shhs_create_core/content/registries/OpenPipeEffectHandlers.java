package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.fluid.openpipe.HostilityEffectHandler;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;

public class OpenPipeEffectHandlers {
    public static void registerDefaults() {
        OpenPipeEffectHandler.REGISTRY.register(Fluids.HOSTILITY.getSource(), new HostilityEffectHandler());
    }
}
