package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlockEntity;
import com.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberBlockEntity;
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
                    Capabilities.FluidHandler.BLOCK,
                    builder.getEntry(),
                    SprayerBlockEntity::getFluidHandlerForSide
            )))
            .register();
    /**
     * 恶意吸收器方块实体，处理区块清除逻辑。
     */
    public static final BlockEntityEntry<HostilityAbsorberBlockEntity> HOSTILITY_ABSORBER_BE = REGISTRATE
            .blockEntity("hostility_absorber", HostilityAbsorberBlockEntity::new)
            .validBlocks(ShHsBlocks.HOSTILITY_ABSORBER)
            .register();

    public static void register() {}
}