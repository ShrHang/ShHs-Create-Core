package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerMenu;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ShHsMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShHsCreateCore.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PortableStockTickerMenu>> PORTABLE_STOCK_TICKER =
            MENUS.register("portable_stock_ticker", () ->
                    IMenuTypeExtension.create((windowId, inv, data) ->
                            new PortableStockTickerMenu(ShHsMenuTypes.PORTABLE_STOCK_TICKER.get(), windowId, inv, data))
            );

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ShHsMenuTypes.PORTABLE_STOCK_TICKER.get(), PortableStockTickerScreen::new);
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
        if (FMLEnvironment.dist.isClient()) bus.addListener(ShHsMenuTypes::registerScreens);
    }
}
