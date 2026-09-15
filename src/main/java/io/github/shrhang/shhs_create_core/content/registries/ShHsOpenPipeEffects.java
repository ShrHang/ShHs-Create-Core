package io.github.shrhang.shhs_create_core.content.registries;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import io.github.shrhang.shhs_create_core.content.fluid.openpipe.HostilityEffectHandler;
import io.github.shrhang.shhs_create_core.content.fluid.openpipe.LiquidFertilizerEffectHandler;

public class ShHsOpenPipeEffects {
    public static void register() {
        OpenPipeEffectHandler.REGISTRY.register(ShHsFluids.HOSTILITY.getSource(), new HostilityEffectHandler());
        OpenPipeEffectHandler.REGISTRY.register(ShHsFluids.LIQUID_FERTILIZER.getSource(), new LiquidFertilizerEffectHandler());
    }
}
