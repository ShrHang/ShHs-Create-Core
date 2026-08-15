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

/**
 * 方块实体类型注册中心，使用 CreateRegistrate 链式注册。
 * 所有动力学方块必须通过 .visual() 注册 Flywheel Visual，以在 Flywheel 启用时正常渲染。
 */
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

    /**
     * 喷洒器方块实体，包含流体储罐、角度调节和喷洒逻辑。
     * 传动轴由 SingleAxisRotatingVisual 渲染，外壳由 TER 渲染。
     */
    public static final BlockEntityEntry<SprayerBlockEntity> SPRAYER = REGISTRATE
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

    /**
     * 恶意吸收器方块实体，处理区块清除逻辑。
     * 齿轮由 SingleAxisRotatingVisual 渲染，底座由 TER 渲染。
     */
    public static final BlockEntityEntry<HostilityAbsorberBlockEntity> HOSTILITY_ABSORBER_BE = REGISTRATE
            .blockEntity("hostility_absorber", HostilityAbsorberBlockEntity::new)
            .visual(() -> SingleAxisRotatingVisual.of(AllPartialModels.MILLSTONE_COG))
            .renderer(() -> HostilityAbsorberRenderer::new)
            .validBlocks(ShHsBlocks.HOSTILITY_ABSORBER)
            .register();

    public static void register() {}
}