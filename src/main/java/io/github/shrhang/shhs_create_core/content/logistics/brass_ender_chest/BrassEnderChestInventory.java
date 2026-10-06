package io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

final class BrassEnderChestInventory implements Container {
    private final BrassEnderChestBlockEntity blockEntity;
    private final UUID owner;
    private final Player viewer;

    BrassEnderChestInventory(BrassEnderChestBlockEntity blockEntity, UUID owner, Player viewer) {
        this.blockEntity = blockEntity;
        this.owner = owner;
        this.viewer = viewer;
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean isEmpty() {
        PlayerEnderChestContainer inventory = inventory();
        return inventory == null || inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        PlayerEnderChestContainer inventory = inventory();
        return inventory == null ? ItemStack.EMPTY : inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        PlayerEnderChestContainer inventory = inventory();
        if (inventory == null)
            return ItemStack.EMPTY;
        ItemStack removed = inventory.removeItem(slot, amount);
        if (!removed.isEmpty())
            changed(inventory);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        PlayerEnderChestContainer inventory = inventory();
        if (inventory == null)
            return ItemStack.EMPTY;
        ItemStack removed = inventory.removeItemNoUpdate(slot);
        if (!removed.isEmpty())
            changed(inventory);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        PlayerEnderChestContainer inventory = inventory();
        if (inventory == null)
            return;
        inventory.setItem(slot, stack);
        changed(inventory);
    }

    @Override
    public void setChanged() {
        PlayerEnderChestContainer inventory = inventory();
        if (inventory != null)
            changed(inventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return viewer == player && blockEntity.canAccessMenu(player, owner);
    }

    @Override
    public void clearContent() {
        PlayerEnderChestContainer inventory = inventory();
        if (inventory == null)
            return;
        inventory.clearContent();
        changed(inventory);
    }

    private PlayerEnderChestContainer inventory() {
        return blockEntity.resolveMenuInventory(owner, viewer);
    }

    private void changed(PlayerEnderChestContainer inventory) {
        inventory.setChanged();
        blockEntity.markInventoryDirty(owner);
    }
}
