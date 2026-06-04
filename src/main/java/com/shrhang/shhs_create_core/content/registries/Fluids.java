package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.data.ShHsRegistrate;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.tterrag.registrate.util.entry.FluidEntry;

public class Fluids {
    private static final ShHsRegistrate REGISTRATE = ShHsCreateCore.REGISTRATE;

    public static final FluidEntry<VirtualFluid> HOSTILITY = REGISTRATE.virtualFluid("hostility")
            .lang("Hostility")
            .register();
    public static final FluidEntry<VirtualFluid> WONDER = REGISTRATE.virtualFluid("wonder")
            .lang("Wonder")
            .register();
    public static void register() {
    }
}
