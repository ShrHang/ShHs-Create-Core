package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.fluid.openpipe.HostilityEffectHandler;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;

public class OpenPipeEffects {
    public static void register() {
        OpenPipeEffectHandler.REGISTRY.register(Fluids.HOSTILITY.getSource(), new HostilityEffectHandler());
    }
}
