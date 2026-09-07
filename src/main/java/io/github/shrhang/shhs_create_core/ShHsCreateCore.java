package io.github.shrhang.shhs_create_core;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import io.github.shrhang.shhs_create_core.api.registrate.ShHsAtlases;
import io.github.shrhang.shhs_create_core.api.registrate.ShHsRegistrate;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import io.github.shrhang.shhs_create_core.content.data.ShHsLang;
import io.github.shrhang.shhs_create_core.content.data.ShHsRecipes;
import io.github.shrhang.shhs_create_core.content.data.ShHsTagKey;
import io.github.shrhang.shhs_create_core.content.event.MagicEventHandler;
import io.github.shrhang.shhs_create_core.content.event.ShHsAttackListener;
import io.github.shrhang.shhs_create_core.content.kinetics.fan.processing.ShHsFanProcessingTypes;
import io.github.shrhang.shhs_create_core.content.registries.*;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(ShHsCreateCore.MODID)
public class ShHsCreateCore {
    public static final String MODID = "shhs_create_core";
    public static final ShHsRegistrate REGISTRATE = (ShHsRegistrate) ShHsRegistrate.create(MODID)
            .defaultCreativeTab(ShHsCreativeTabs.DEFAULT.getKey())
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public ShHsCreateCore(IEventBus modEventBus, ModContainer modContainer) {
        gatherData();
        ShHsConfig.register(modContainer);

        ShHsBlocks.register();
        ShHsBlockEntityTypes.register();
        ShHsItems.register();
        ShHsFluids.register();
        ShHsTraits.register();

        ShHsComponentTypes.register(modEventBus);
        ShHsCreativeTabs.register(modEventBus);
        ShHsMenuTypes.register(modEventBus);
        ShHsRecipeTypes.register(modEventBus);
        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> () -> CreateEnchantmentIndustry.register(modEventBus));

        modEventBus.addListener(ShHsCreateCore::init);
        modEventBus.addListener(ShHsCreateCore::onRegister);
        modEventBus.addListener(ShHsPackets::register);
        modEventBus.addListener(ShHsCreateCore::modifyEntityAttributes);
    }

    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(ShHsInventoryIdentifiers::register);
        event.enqueueWork(ShHsOpenPipeEffects::register);
        MagicEventHandler.init();
        ShHsAttackListener.init();
    }

    public static void onRegister(final RegisterEvent event) {
        ShHsFanProcessingTypes.init();
    }

    public static void modifyEntityAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(entityType -> event.add(entityType, CoPAttrs.REALITY));
    }

    private static void gatherData() {
        ShHsAtlases.init();
        ShHsLang.init();
        ShHsRecipes.init();
        ShHsTagKey.init();
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(MODID, id);
    }
}
