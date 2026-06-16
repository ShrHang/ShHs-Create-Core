package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.shrhang.shhs_create_core.api.registries.ShHsMenuTypes;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public class RemoteStockKeeperRequestMenu extends StockKeeperRequestMenu {
    public RemoteStockKeeperRequestMenu(MenuType<?> type, int id, Inventory playerInventory, StockTickerBlockEntity be) {
        super(type, id, playerInventory, be);
    }

    public RemoteStockKeeperRequestMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public static AbstractContainerMenu create(int pContainerId, Inventory pPlayerInventory, StockTickerBlockEntity stockTickerBlockEntity) {
        return new RemoteStockKeeperRequestMenu(ShHsMenuTypes.REMOTE_STOCK_KEEPER_REQUEST.get(), pContainerId, pPlayerInventory, stockTickerBlockEntity);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
