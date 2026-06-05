package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.fluid.openpipe.HostilityEffectHandler;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;

public class ShHsOpenPipeEffects {
    public static void register() {
        OpenPipeEffectHandler.REGISTRY.register(ShHsFluids.HOSTILITY.getSource(), new HostilityEffectHandler());
    }
}
