package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import io.github.shrhang.shhs_create_core.content.registries.ShHsMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class PortableStockTickerMenu extends AbstractContainerMenu {
    public final UUID networkId;
    public Object screenReference;

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
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
