package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlockEntity;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerRenderer;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerVisual;
import io.github.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberBlockEntity;
import io.github.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberRenderer;
import io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.neoforged.neoforge.capabilities.Capabilities;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

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

    public static final BlockEntityEntry<SprayerBlockEntity> SPRAYER_BE = REGISTRATE
            .blockEntity("sprayer", SprayerBlockEntity::new)
            .visual(() -> SprayerVisual::new)
            .renderer(() -> SprayerRenderer::new)
            .validBlocks(ShHsBlocks.SPRAYER)
            .transform(builder -> builder.registerCapability(event -> event.registerBlockEntity(
                    Capabilities.FluidHandler.BLOCK,
                    builder.getEntry(),
                    SprayerBlockEntity::getFluidHandlerForSide
            )))
            .register();

    public static final BlockEntityEntry<HostilityAbsorberBlockEntity> HOSTILITY_ABSORBER_BE = REGISTRATE
            .blockEntity("hostility_absorber", HostilityAbsorberBlockEntity::new)
            .visual(() -> SingleAxisRotatingVisual.of(AllPartialModels.MILLSTONE_COG))
            .renderer(() -> HostilityAbsorberRenderer::new)
            .validBlocks(ShHsBlocks.HOSTILITY_ABSORBER)
            .register();

    public static void register() {}
}