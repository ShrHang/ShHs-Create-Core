package io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest;

import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.UUID;

/** A capability may outlive the current lock or owner, so every operation revalidates access. */
final class BrassEnderChestItemHandler implements IItemHandlerModifiable {
    private final BrassEnderChestBlockEntity blockEntity;
    private final UUID owner;

    BrassEnderChestItemHandler(BrassEnderChestBlockEntity blockEntity, UUID owner) {
        this.blockEntity = blockEntity;
        this.owner = owner;
    }

    @Override
    public int getSlots() {
        return 27;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        InvWrapper delegate = delegate();
        return delegate == null ? ItemStack.EMPTY : delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        InvWrapper delegate = delegate();
        if (delegate == null)
            return stack;
        ItemStack remainder = delegate.insertItem(slot, stack, simulate);
        if (!simulate && remainder.getCount() < stack.getCount())
            blockEntity.markInventoryDirty(owner);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        InvWrapper delegate = delegate();
        if (delegate == null)
            return ItemStack.EMPTY;
        ItemStack extracted = delegate.extractItem(slot, amount, simulate);
        if (!simulate && !extracted.isEmpty())
            blockEntity.markInventoryDirty(owner);
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        InvWrapper delegate = delegate();
        return delegate == null ? 64 : delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        InvWrapper delegate = delegate();
        return delegate != null && delegate.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        InvWrapper delegate = delegate();
        if (delegate != null) {
            delegate.setStackInSlot(slot, stack);
            blockEntity.markInventoryDirty(owner);
        }
    }

    private InvWrapper delegate() {
        PlayerEnderChestContainer inventory = blockEntity.resolveInventory(owner,
                BrassEnderChestInventory.Access.AUTOMATION, null, false);
        return inventory == null ? null : new InvWrapper(inventory);
    }
}
