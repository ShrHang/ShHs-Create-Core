package io.github.shrhang.shhs_create_core.content.registries;

import com.simibubi.create.content.fluids.VirtualFluid;
import com.tterrag.registrate.util.entry.FluidEntry;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsFluids {

    public static final FluidEntry<VirtualFluid> HOSTILITY;
    public static final FluidEntry<VirtualFluid> WONDER;

    static {
        HOSTILITY = REGISTRATE.virtualFluid("hostility")
                .lang("Hostility")
                .register();

        WONDER = REGISTRATE.virtualFluid("wonder")
                .lang("Wonder")
                .register();
    }

    public static void register() {
    }
}
