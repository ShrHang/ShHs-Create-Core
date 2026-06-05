package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShHsCreateCore.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEFAULT = REGISTER.register("default", () ->
            CreativeModeTab.builder()
                    .title(REGISTRATE.langOfCreativeTab("shhs_stuff", "ShH's Stuff"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(ShHsTraits.AUGMENTED_REALITY.get()))
                    .build()
    );
    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
