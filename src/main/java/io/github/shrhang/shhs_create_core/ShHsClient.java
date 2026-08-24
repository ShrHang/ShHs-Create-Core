package io.github.shrhang.shhs_create_core;

import io.github.shrhang.shhs_create_core.content.event.ClientEvents;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerScreen;
import io.github.shrhang.shhs_create_core.content.ponder.ShHsPonderPlugin;
import io.github.shrhang.shhs_create_core.content.registries.ShHsKeys;
import io.github.shrhang.shhs_create_core.content.registries.ShHsMenuTypes;
import io.github.shrhang.shhs_create_core.content.registries.ShHsPartialModels;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 模组客户端专用入口，负责注册客户端事件、模型、按键等。
 * 仅在客户端环境下加载。
 */
@Mod(value = ShHsCreateCore.MODID, dist = Dist.CLIENT)
public class ShHsClient {

    public ShHsClient(IEventBus modEventBus, ModContainer modContainer) {
        ShHsConfig.registerClient(modContainer);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        ShHsPartialModels.register();
        ClientEvents.init();

        modEventBus.addListener(ShHsClient::init);
        modEventBus.addListener(ShHsKeys::register);
        modEventBus.addListener(ShHsClient::registerScreens);
    }

    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new ShHsPonderPlugin()));
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ShHsMenuTypes.PORTABLE_STOCK_TICKER.get(), PortableStockTickerScreen::new);
    }
}