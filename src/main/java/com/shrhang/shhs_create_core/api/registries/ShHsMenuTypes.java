package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.RemoteStockKeeperRequestMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ShHsMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShHsCreateCore.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RemoteStockKeeperRequestMenu>> REMOTE_STOCK_KEEPER_REQUEST =
            MENUS.register("remote_stock_keeper_request", () ->
                    IMenuTypeExtension.create((windowId, inv, data) ->
                            new RemoteStockKeeperRequestMenu(ShHsMenuTypes.REMOTE_STOCK_KEEPER_REQUEST.get(), windowId, inv, data))
            );

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
