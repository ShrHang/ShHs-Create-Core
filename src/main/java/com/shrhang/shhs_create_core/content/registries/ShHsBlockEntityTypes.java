package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlockEntity;
import com.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.neoforged.neoforge.capabilities.Capabilities;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsBlockEntityTypes {

    public static final BlockEntityEntry<BrassEnderChestBlockEntity> BRASS_ENDER_CHEST_BE = REGISTRATE
            .blockEntity("brass_ender_chest", BrassEnderChestBlockEntity::new)
            .validBlocks(ShHsBlocks.BRASS_ENDER_CHEST)
            .register();

    public static final BlockEntityEntry<SprayerBlockEntity> SPRAYER = REGISTRATE
            .blockEntity("sprayer", SprayerBlockEntity::new)
            .validBlocks(ShHsBlocks.SPRAYER)
            .transform(builder -> builder.registerCapability(event -> event.registerBlockEntity(
                    Capabilities.FluidHandler.BLOCK, // 正确使用 BLOCK 能力
                    builder.getEntry(),
                    SprayerBlockEntity::getFluidHandlerForSide // 方法引用
            )))
            .register();

    public static void register() {}
}