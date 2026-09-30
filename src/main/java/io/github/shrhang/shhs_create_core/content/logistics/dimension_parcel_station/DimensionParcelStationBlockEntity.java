package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import com.simibubi.create.Create;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import io.github.shrhang.shhs_create_core.ShHsConfig;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryIdentifier;
import io.github.shrhang.shhs_create_core.api.packager.VirtualInventoryProvider;
import io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DimensionParcelStationBlockEntity extends NetedBlockEntity
        implements MenuProvider, VirtualInventoryProvider {
    private boolean allowItemInput = true;
    private boolean allowItemOutput = true;
    private boolean allowFluidInput = true;
    private boolean allowFluidOutput = true;
    private final Map<UUID, String> receiveAddresses = new HashMap<>();

    private final DimensionParcelStationAccess access = new DimensionParcelStationAccess(this);

    public String getReceiveAddress(UUID network) {
        return receiveAddresses.getOrDefault(network, "");
    }

    public void setReceiveAddress(UUID network, String address, Player player) {
        if (!mayConfigure(player) || address.length() > 64
                || !DimensionParcelStationRouting.connectedNetworks(this).contains(network)
                || !Create.LOGISTICS.mayInteract(network, player))
            return;
        address = address.strip();
        if (address.contains("*") || address.contains("?")
                || address.chars().anyMatch(Character::isISOControl))
            return;
        if (address.isEmpty())
            receiveAddresses.remove(network);
        else
            receiveAddresses.put(network, address);
        setChanged();
        sendBlockUpdated();
    }

    public DimensionParcelStationBlockEntity(BlockPos pos, BlockState state) {
        this(ShHsBlockEntityTypes.DIMENSION_PARCEL_STATION_BE.get(), pos, state);
    }

    public DimensionParcelStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean trySetNetId(int netId) {
        if (!(level instanceof ServerLevel serverLevel) || netId < 0)
            return false;
        GlobalPos pos = GlobalPos.of(serverLevel.dimension(), worldPosition);
        if (!DimensionParcelStationBindingIndex.get(serverLevel.getServer())
                .tryReserve(serverLevel.getServer(), pos, netId))
            return false;
        super.setNetId(netId);
        access.resetHandlers();
        level.invalidateCapabilities(worldPosition);
        sendBlockUpdated();
        return getNetId() == netId;
    }

    @Override
    public void setNetId(int id) {
        if (id < 0) {
            if (level instanceof ServerLevel serverLevel)
                DimensionParcelStationBindingIndex.get(serverLevel.getServer())
                        .release(GlobalPos.of(serverLevel.dimension(), worldPosition));
            super.setNetId(id);
            access.resetHandlers();
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
                sendBlockUpdated();
            }
            return;
        }
        trySetNetId(id);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel)
            DimensionParcelStationBindingIndex.get(serverLevel.getServer()).onStationLoaded(this);
    }

    @Nullable
    public IItemHandlerModifiable getItemHandler(Direction side) {
        return access.getItemHandler(side);
    }

    @Nullable
    public IFluidHandler getFluidHandler(Direction side) {
        return access.getFluidHandler(side);
    }

    public boolean isItemPackagerPlacementTarget() {
        return access.isItemPackagerPlacementTarget();
    }

    public boolean isFluidPackagerPlacementTarget() {
        return access.isFluidPackagerPlacementTarget();
    }

    public boolean allowsWarehouseOutput(BlockEntity packager) {
        return access.allowsWarehouseOutput(packager);
    }

    public boolean mayConfigure(Player player) {
        DimensionsNet net = getNet();
        return net != null && ShHsConfig.SERVER.dimensionParcelStationConfigurePermission.get().allows(net, player);
    }

    public void toggle(Channel channel, Player player) {
        if (!mayConfigure(player))
            return;
        switch (channel) {
            case ITEM_INPUT -> allowItemInput = !allowItemInput;
            case ITEM_OUTPUT -> allowItemOutput = !allowItemOutput;
            case FLUID_INPUT -> allowFluidInput = !allowFluidInput;
            case FLUID_OUTPUT -> allowFluidOutput = !allowFluidOutput;
        }
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            sendBlockUpdated();
        }
    }

    private void sendBlockUpdated() {
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean isAllowed(Channel channel) {
        return switch (channel) {
            case ITEM_INPUT -> allowItemInput;
            case ITEM_OUTPUT -> allowItemOutput;
            case FLUID_INPUT -> allowFluidInput;
            case FLUID_OUTPUT -> allowFluidOutput;
        };
    }

    public int getStationCount() {
        if (!(level instanceof ServerLevel serverLevel) || getNetId() < 0)
            return 0;
        return DimensionParcelStationBindingIndex.get(serverLevel.getServer()).activeCount(getNetId());
    }

    @Override
    public @Nullable VirtualInventoryIdentifier getVirtualInventoryIdentifier() {
        return getNetId() < 0 ? null : new DimensionParcelStationInventoryIdentifier(getNetId());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        receiveAddresses.clear();
        ListTag addresses = tag.getList("TerminalAddresses", Tag.TAG_COMPOUND);
        for (int i = 0; i < addresses.size(); i++) {
            CompoundTag entry = addresses.getCompound(i);
            if (entry.hasUUID("Network")) receiveAddresses.put(entry.getUUID("Network"), entry.getString("Address"));
        }
        allowItemInput = !tag.contains("AllowItemInput") || tag.getBoolean("AllowItemInput");
        allowItemOutput = !tag.contains("AllowItemOutput") || tag.getBoolean("AllowItemOutput");
        allowFluidInput = !tag.contains("AllowFluidInput") || tag.getBoolean("AllowFluidInput");
        allowFluidOutput = !tag.contains("AllowFluidOutput") || tag.getBoolean("AllowFluidOutput");
        access.resetHandlers();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag addresses = new ListTag();
        receiveAddresses.forEach((network, address) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Network", network);
            entry.putString("Address", address);
            addresses.add(entry);
        });
        tag.put("TerminalAddresses", addresses);
        tag.putBoolean("AllowItemInput", allowItemInput);
        tag.putBoolean("AllowItemOutput", allowItemOutput);
        tag.putBoolean("AllowFluidInput", allowFluidInput);
        tag.putBoolean("AllowFluidOutput", allowFluidOutput);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.shhs_create_core.dimension_parcel_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DimensionParcelStationMenu(containerId, inventory, this);
    }

    public enum Channel {
        ITEM_INPUT,
        ITEM_OUTPUT,
        FLUID_INPUT,
        FLUID_OUTPUT
    }

    private record DimensionParcelStationInventoryIdentifier(int netId) implements VirtualInventoryIdentifier {
    }
}
