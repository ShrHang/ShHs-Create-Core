package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.fluid.spray.SprayerBlockEntity;
import com.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.neoforged.neoforge.capabilities.Capabilities;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsBlockEntityTypes {

    public static final BlockEntityEntry<BrassEnderChestBlockEntity> BRASS_ENDER_CHEST_BE = REGISTRATE
            .blockEntity("brass_ender_chest", BrassEnderChestBlockEntity::new)
            .validBlocks(ShHsBlocks.BRASS_ENDER_CHEST)
            .transform(builder -> builder.registerCapability(event -> event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    builder.getEntry(),
                    (be, context) -> be.getInventory()
            )))
            .register();

    public static final BlockEntityEntry<SprayerBlockEntity> SPRAYER = REGISTRATE
            .blockEntity("sprayer", SprayerBlockEntity::new)
            .validBlocks(ShHsBlocks.SPRAYER)
            .transform(builder -> builder.registerCapability(event -> event.registerBlockEntity(
                    Capabilities.FluidHandler.BLOCK,
                    builder.getEntry(),
                    (be, context) -> be.getFluidHandler()
            )))
            .register();

    public static void register() {}
}