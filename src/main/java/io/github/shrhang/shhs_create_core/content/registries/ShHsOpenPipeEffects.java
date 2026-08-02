package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.content.fluid.openpipe.HostilityEffectHandler;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;

public class ShHsOpenPipeEffects {
    public static void register() {
        OpenPipeEffectHandler.REGISTRY.register(ShHsFluids.HOSTILITY.getSource(), new HostilityEffectHandler());
    }
}
