package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.resources.ResourceLocation;

public class Fluids {
    private static final CreateRegistrate REGISTRATE = ShHsCreateCore.createRegistrate;

    public static final FluidEntry<VirtualFluid> HOSTILITY = REGISTRATE.virtualFluid("hostility", ResourceLocation.parse("shhs_create_core:block/hostility_still"), ResourceLocation.parse("shhs_create_core:block/hostility_flow"))
            .lang("Hostility")
            .register();
    public static final FluidEntry<VirtualFluid> WONDER = REGISTRATE.virtualFluid("wonder", ResourceLocation.parse("shhs_create_core:block/wonder_still"), ResourceLocation.parse("shhs_create_core:block/wonder_flow"))
            .lang("Wonder")
            .register();
    public static void register() {
    }
}
