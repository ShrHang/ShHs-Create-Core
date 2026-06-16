package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.shrhang.shhs_create_core.api.registries.ShHsMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PortableStockTickerMenu extends AbstractContainerMenu {
    public final UUID networkId;

    public PortableStockTickerMenu(MenuType<?> type, int id, Inventory playerInventory, UUID networkId) {
        super(type, id);
        this.networkId = networkId;
    }

    public PortableStockTickerMenu(MenuType<?> type, int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(type, id, playerInventory, extraData.readUUID());
    }

    public static PortableStockTickerMenu create(int containerId, Inventory playerInventory, UUID networkId) {
        return new PortableStockTickerMenu(ShHsMenuTypes.PORTABLE_STOCK_TICKER.get(), containerId, playerInventory, networkId);
    }

    @Override
    public @NotNull net.minecraft.world.item.ItemStack quickMoveStack(@NotNull Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }
}
