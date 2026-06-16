package com.shrhang.shhs_create_core;

import com.shrhang.shhs_create_core.api.registries.*;
import com.shrhang.shhs_create_core.compat.Mods;
import com.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import com.shrhang.shhs_create_core.content.data.ShHsAtlases;
import com.shrhang.shhs_create_core.content.data.ShHsLang;
import com.shrhang.shhs_create_core.content.data.ShHsRegistrate;
import com.shrhang.shhs_create_core.content.data.ShHsTagKey;
import com.shrhang.shhs_create_core.content.event.MagicEventHandler;
import com.shrhang.shhs_create_core.content.event.ShHsAttackListener;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

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
        ShHsConfig.init(modContainer);

        ShHsBlocks.register();
        ShHsBlockEntityTypes.register();
        ShHsItems.register();
        ShHsFluids.register();
        ShHsTraits.register();

        ShHsComponentTypes.register(modEventBus);
        ShHsCreativeTabs.register(modEventBus);
        ShHsMenuTypes.register(modEventBus);

        modEventBus.addListener(ShHsCreateCore::init);
        modEventBus.addListener(ShHsPackets::register);
        modEventBus.addListener(ShHsCreateCore::modifyEntityAttributes);
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ShHsCreateCore::registerScreens);
        }
    }

    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(ShHsInventoryIdentifiers::register);
        event.enqueueWork(ShHsOpenPipeEffects::register);
        MagicEventHandler.init();
        ShHsAttackListener.init();
        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> CreateEnchantmentIndustry::init);
    }

    public static void modifyEntityAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(entityType -> event.add(entityType, CoPAttrs.REALITY));
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ShHsMenuTypes.PORTABLE_STOCK_TICKER.get(), PortableStockTickerScreen::new);
    }

    private static void gatherData() {
        ShHsAtlases.init();
        ShHsLang.init();
        ShHsTagKey.init();
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(MODID, id);
    }
}
