package io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryIdentifier;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.tooltipComponentForGoggles;

public class BrassEnderChestBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, VirtualInventoryProvider {
    private UUID targetUUID;
    private boolean locked = true;
    private String targetName = "???";
    private IItemHandler inventory;
    private boolean automationAvailable;

    public BrassEnderChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(tooltipComponentForGoggles("brass_ender_chest.header"));
        if (targetUUID == null) {
            if (isPlayerSneaking)
                tooltip.add(tooltipComponentForGoggles("brass_ender_chest.no_owner").withStyle(ChatFormatting.RED));
            return true;
        }
        tooltip.add(tooltipComponentForGoggles("brass_ender_chest.owner",
                Component.literal(targetName).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY));
        if (isPlayerSneaking)
            tooltip.add(tooltipComponentForGoggles(locked ? "brass_ender_chest.locked" : "brass_ender_chest.unlocked")
                    .withStyle(locked ? ChatFormatting.RED : ChatFormatting.GREEN));
        return true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (!(level instanceof ServerLevel serverLevel) || targetUUID == null)
            return;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetUUID);
        if (player != null && !player.getName().getString().equals(targetName)) {
            targetName = player.getName().getString();
            notifyUpdate();
        }
        boolean available = !locked && EnderChestInventoryManager.get(serverLevel.getServer()).isAvailable(targetUUID);
        if (available != automationAvailable) {
            automationAvailable = available;
            level.invalidateCapabilities(worldPosition);
        }
    }

    @Override
    public @Nullable VirtualInventoryIdentifier getVirtualInventoryIdentifier() {
        return targetUUID == null ? null : new BrassEnderChestBlockInvId(targetUUID);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (targetUUID != null)
            tag.putUUID("TargetPlayer", targetUUID);
        tag.putBoolean("IsLocked", locked);
        tag.putString("DisplayName", targetName);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targetUUID = tag.hasUUID("TargetPlayer") ? tag.getUUID("TargetPlayer") : null;
        locked = !tag.contains("IsLocked") || tag.getBoolean("IsLocked");
        targetName = tag.contains("DisplayName") ? tag.getString("DisplayName") : "???";
        inventory = null;
        automationAvailable = false;
    }

    public void setOwner(Player player) {
        setTarget(player.getUUID(), player.getName().getString(), locked);
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isOwner(Player player) {
        return targetUUID != null && targetUUID.equals(player.getUUID());
    }

    public boolean isLocked() {
        return locked;
    }

    public void changeLock() {
        setLock(!locked);
    }

    public void setLock(boolean lock) {
        if (locked == lock)
            return;
        locked = lock;
        accessChanged();
    }

    @Nullable
    public IItemHandler getInventory() {
        if (!(level instanceof ServerLevel) || targetUUID == null || locked)
            return null;
        if (resolveInventory(targetUUID, BrassEnderChestInventory.Access.AUTOMATION, null, true) == null)
            return null;
        if (inventory == null)
            inventory = new BrassEnderChestItemHandler(this, targetUUID);
        automationAvailable = true;
        return inventory;
    }

    @Nullable
    public Container getMenuInventory(Player player) {
        if (targetUUID == null || !canAccess(player))
            return null;
        if (resolveInventory(targetUUID, BrassEnderChestInventory.Access.MENU, player.getUUID(), true) == null)
            return null;
        return new BrassEnderChestInventory(this, targetUUID, BrassEnderChestInventory.Access.MENU, player.getUUID());
    }

    boolean canAccessMenu(Player player, UUID expectedOwner) {
        return !isRemoved() && level != null && player.level() == level && Objects.equals(targetUUID, expectedOwner)
                && level.getBlockEntity(worldPosition) == this && canAccess(player)
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5,
                worldPosition.getZ() + .5) <= 64;
    }

    @Nullable
    PlayerEnderChestContainer resolveInventory(UUID expectedOwner, BrassEnderChestInventory.Access access,
                                                @Nullable UUID viewer, boolean requestLoad) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved() || !Objects.equals(targetUUID, expectedOwner))
            return null;
        if (access == BrassEnderChestInventory.Access.AUTOMATION && locked)
            return null;
        if (access == BrassEnderChestInventory.Access.MENU && locked && !expectedOwner.equals(viewer))
            return null;
        return EnderChestInventoryManager.get(serverLevel.getServer()).getInventory(expectedOwner, requestLoad);
    }

    void markInventoryDirty(UUID expectedOwner) {
        if (level instanceof ServerLevel serverLevel && Objects.equals(targetUUID, expectedOwner))
            EnderChestInventoryManager.get(serverLevel.getServer()).markDirty(expectedOwner);
    }

    private boolean canAccess(Player player) {
        return !locked || isOwner(player);
    }

    private void setTarget(UUID owner, String name, boolean lock) {
        boolean accessChanged = !Objects.equals(targetUUID, owner) || locked != lock;
        boolean changed = accessChanged || !targetName.equals(name);
        if (!changed)
            return;
        targetUUID = owner;
        targetName = name;
        locked = lock;
        if (accessChanged)
            accessChanged();
        else
            notifyUpdate();
    }

    private void accessChanged() {
        inventory = null;
        automationAvailable = false;
        notifyUpdate();
        if (level != null && !level.isClientSide)
            level.invalidateCapabilities(worldPosition);
    }

    private record BrassEnderChestBlockInvId(UUID targetUUID) implements VirtualInventoryIdentifier {
    }
}
