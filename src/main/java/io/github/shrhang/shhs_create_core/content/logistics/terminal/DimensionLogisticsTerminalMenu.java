package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.content.registries.ShHsMenuTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.*;
import java.util.function.BooleanSupplier;

public class DimensionLogisticsTerminalMenu extends AbstractContainerMenu {
    public static final int RESULT = 0, CRAFT_START = 1, CRAFT_END = 10, PLAYER_START = 10, PLAYER_END = 46;
    public final Player player;
    public final int netId;
    public final UUID session;
    public final boolean compactLayout;
    public final int windowHeight;
    public final int rightPanelX;
    public final int orderPanelY;
    public final int craftPanelY;
    public final int playerPanelY;
    public final int bottomPanelY;
    public final boolean modulesCanShare;
    public final boolean orderCanShow;
    public final boolean craftCanShow;
    public final CraftingContainer crafting = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();
    public List<TerminalStock.Entry> clientStock = List.of();
    public List<TerminalOrderSummary> clientOrders = List.of();
    public Map<UUID, String> clientSourceAddresses = Map.of();
    public String clientNetworkName = "";
    public UUID acceptedSubmission;
    public long clientRevision = -1;
    private long receivingRevision = -1;
    private int nextPart;
    private final List<TerminalStock.Entry> receiving = new ArrayList<>();
    private final List<TerminalOrderSummary> receivingOrders = new ArrayList<>();
    private final Map<UUID, String> receivingSourceAddresses = new HashMap<>();
    private long revision;
    private int ticks;
    private boolean changingCraft;
    private boolean clientPlayerSlotsActive = true;
    private boolean clientCraftSlotsActive = true;

    public DimensionLogisticsTerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readInt(), buffer.readUUID());
    }

    public DimensionLogisticsTerminalMenu(int id, Inventory inventory, int netId, UUID session) {
        super(ShHsMenuTypes.DIMENSION_LOGISTICS_TERMINAL.get(), id);
        this.player = inventory.player; this.netId = netId; this.session = session;
        DimensionLogisticsTerminalClientLayout.Snapshot layout = FMLEnvironment.dist == Dist.CLIENT
                ? DimensionLogisticsTerminalClientLayout.create()
                : new DimensionLogisticsTerminalClientLayout.Snapshot(false,
                DimensionLogisticsTerminalLayout.windowHeight(266));
        compactLayout = layout.compact();
        windowHeight = layout.windowHeight();
        rightPanelX = compactLayout ? DimensionLogisticsTerminalLayout.COMPACT_RIGHT_PANEL_X
                : DimensionLogisticsTerminalLayout.RIGHT_PANEL_X;
        DimensionLogisticsTerminalLayout.RightPanels panels =
                DimensionLogisticsTerminalLayout.rightPanels(windowHeight);
        bottomPanelY = panels.bottomY();
        playerPanelY = panels.playerY();
        orderPanelY = panels.orderY();
        craftPanelY = panels.craftY();
        modulesCanShare = panels.modulesCanShare();
        orderCanShow = panels.orderCanShow();
        craftCanShow = panels.craftCanShow();
        addSlot(new ResultSlot(player, crafting, result, 0,
                rightPanelX + DimensionLogisticsTerminalLayout.CRAFT_RESULT_X,
                craftPanelY + DimensionLogisticsTerminalLayout.CRAFT_RESULT_Y) {
            @Override
            public boolean isActive() { return clientCraftSlotsActive; }

            @Override
            public boolean mayPickup(Player player) { return (player.level().isClientSide() || network() != null) && super.mayPickup(player); }

            @Override
            public void onTake(Player player, ItemStack stack) {
                if (player.level().isClientSide()) return;
                List<ItemStack> templates = crafting.getItems().stream().map(ItemStack::copy).toList();
                changingCraft = true;
                super.onTake(player, stack);
                for (int i = 0; i < 9; i++) {
                    if (!crafting.getItem(i).isEmpty() || templates.get(i).isEmpty()) continue;
                    ItemStack refill = withdraw(new ItemStackKey(templates.get(i)), 1);
                    if (!refill.isEmpty()) crafting.setItem(i, refill);
                }
                changingCraft = false;
                slotsChanged(crafting);
            }
        });
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
            addSlot(responsiveSlot(crafting, x + y * 3,
                    rightPanelX + DimensionLogisticsTerminalLayout.CRAFT_INPUT_X
                            + x * DimensionLogisticsTerminalLayout.CRAFT_SLOT_STEP,
                    craftPanelY + DimensionLogisticsTerminalLayout.CRAFT_INPUT_Y
                            + y * DimensionLogisticsTerminalLayout.CRAFT_SLOT_STEP,
                    () -> clientCraftSlotsActive));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++)
            addSlot(responsiveSlot(inventory, x + y * 9 + 9,
                    rightPanelX + DimensionLogisticsTerminalLayout.PLAYER_INVENTORY_X + x * 18,
                    playerPanelY + DimensionLogisticsTerminalLayout.PLAYER_INVENTORY_Y + y * 18,
                    () -> clientPlayerSlotsActive));
        for (int x = 0; x < 9; x++)
            addSlot(responsiveSlot(inventory, x,
                    rightPanelX + DimensionLogisticsTerminalLayout.PLAYER_INVENTORY_X + x * 18,
                    playerPanelY + DimensionLogisticsTerminalLayout.PLAYER_HOTBAR_Y,
                    () -> clientPlayerSlotsActive));
    }

    private Slot responsiveSlot(Container container, int index, int x, int y, BooleanSupplier active) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean isActive() { return active.getAsBoolean(); }
        };
    }

    public void setClientSlotActivity(boolean playerActive, boolean craftActive) {
        if (!player.level().isClientSide()) return;
        clientPlayerSlotsActive = playerActive;
        clientCraftSlotsActive = craftActive;
    }

    public boolean isClientCraftSlotsActive() {
        return clientCraftSlotsActive;
    }

    public DimensionsNet network() {
        if (player.level().isClientSide()) return null;
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        return primary != null && primary.getId() == netId && primary.getPlayers().contains(player.getUUID()) ? primary : null;
    }

    @Override
    public boolean stillValid(Player player) { return player.isAlive(); }

    public void depositCursor() {
        if (player.level().isClientSide() || getCarried().isEmpty()) return;
        ItemStack stack = getCarried();
        deposit(stack);
        setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        broadcastChanges();
    }

    public void clickLocal(ItemStack template, int button, boolean quickMove) {
        if (player.level().isClientSide() || template.isEmpty() || button < 0 || button > 1) return;
        DimensionsNet net = network();
        if (net == null) return;
        ItemStackKey key = new ItemStackKey(template);
        long stored = net.getUnifiedStorage().getStorage().stream()
                .filter(entry -> entry.key() instanceof ItemStackKey itemKey && itemKey.equals(key))
                .mapToLong(KeyAmount::amount).sum();
        if (stored <= 0) return;

        if (quickMove) {
            int count = Math.min((int) Math.min(stored, template.getMaxStackSize()), inventorySpace(template));
            ItemStack extracted = withdrawLocal(key, count);
            if (!extracted.isEmpty()) {
                player.getInventory().add(extracted);
                if (!extracted.isEmpty()) deposit(extracted);
            }
        } else {
            ItemStack carried = getCarried();
            if (carried.isEmpty()) {
                int maximum = (int) Math.min(stored, template.getMaxStackSize());
                int count = button == 1 ? (maximum + 1) / 2 : maximum;
                setCarried(withdrawLocal(key, count));
            } else if (ItemStack.isSameItemSameComponents(carried, template)) {
                depositAmount(carried, button == 1 ? 1 : carried.getCount());
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            } else {
                depositAmount(carried, carried.getCount());
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
        }
        broadcastChanges();
    }

    private int inventorySpace(ItemStack template) {
        int space = 0;
        for (ItemStack current : player.getInventory().items) {
            if (current.isEmpty()) space += template.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, template))
                space += Math.max(0, current.getMaxStackSize() - current.getCount());
            if (space >= template.getMaxStackSize()) return template.getMaxStackSize();
        }
        return space;
    }

    private void depositAmount(ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty()) return;
        DimensionsNet net = network();
        if (net == null) return;
        int offered = Math.min(count, stack.getCount());
        long remainder = net.getUnifiedStorage().insert(new ItemStackKey(stack), offered, false).amount();
        stack.shrink(offered - (int) remainder);
    }

    private void deposit(ItemStack stack) {
        DimensionsNet net = network();
        if (net == null || stack.isEmpty()) return;
        stack.setCount((int) net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount());
    }

    private ItemStack withdraw(ItemStackKey key, int count) {
        if (count <= 0) return ItemStack.EMPTY;
        int obtained = 0;
        DimensionsNet net = network();
        if (net != null) obtained = (int) net.getUnifiedStorage().extract(key, count, false, false).amount();
        for (int i = 0; i < 36 && obtained < count; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, key.getReadOnlyStack())) continue;
            int removed = Math.min(count - obtained, stack.getCount());
            stack.shrink(removed); obtained += removed;
        }
        return obtained == 0 ? ItemStack.EMPTY : key.copyStackWithCount(obtained);
    }

    private ItemStack withdrawLocal(ItemStackKey key, int count) {
        if (count <= 0 || network() == null) return ItemStack.EMPTY;
        int obtained = (int) network().getUnifiedStorage().extract(key, count, false, false).amount();
        return obtained == 0 ? ItemStack.EMPTY : key.copyStackWithCount(obtained);
    }

    public Map<ItemStackKey, Long> availableForCrafting() {
        Map<ItemStackKey, Long> pool = new LinkedHashMap<>();
        if (player.level().isClientSide()) {
            for (var entry : clientStock) if (entry.network() == null) pool.merge(new ItemStackKey(entry.stack()), entry.amount(), Long::sum);
        } else if (network() != null) {
            for (var entry : network().getUnifiedStorage().getStorage())
                if (entry.key() instanceof ItemStackKey key) pool.merge(key, entry.amount(), Long::sum);
        }
        for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty()) pool.merge(new ItemStackKey(stack), (long) stack.getCount(), Long::sum);
        for (ItemStack stack : crafting.getItems()) if (!stack.isEmpty()) pool.merge(new ItemStackKey(stack), (long) stack.getCount(), Long::sum);
        return pool;
    }

    public void fillRecipe(ResourceLocation recipeId, boolean maximum) {
        if (network() == null) return;
        var holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)) return;
        Map<ItemStackKey, Long> available = availableForCrafting();
        ItemStack[] plan = TerminalCrafting.plan(recipe, available);
        if (plan == null) { player.displayClientMessage(TerminalData.text("missing_materials"), true); return; }
        Map<ItemStackKey, Integer> uses = new HashMap<>();
        for (ItemStack stack : plan) if (!stack.isEmpty()) uses.merge(new ItemStackKey(stack), 1, Integer::sum);
        int multiplier = maximum ? 64 : 1;
        for (var entry : uses.entrySet()) multiplier = (int) Math.min(multiplier,
                Math.min(entry.getKey().getReadOnlyStack().getMaxStackSize(), available.get(entry.getKey()) / entry.getValue()));
        changingCraft = true;
        // Keep the old grid in a local escrow so a full inventory cannot lose ingredients during transfer.
        List<ItemStack> old = new ArrayList<>();
        for (int i = 0; i < 9; i++) old.add(crafting.removeItemNoUpdate(i));
        for (int i = 0; i < 9; i++) {
            if (plan[i].isEmpty()) continue;
            int need = multiplier;
            for (ItemStack stack : old) if (ItemStack.isSameItemSameComponents(stack, plan[i])) {
                int taken = Math.min(need, stack.getCount()); stack.shrink(taken); need -= taken;
            }
            ItemStack extracted = withdraw(new ItemStackKey(plan[i]), need);
            int obtained = multiplier - need + extracted.getCount();
            crafting.setItem(i, plan[i].copyWithCount(obtained));
        }
        for (ItemStack stack : old) giveBack(stack);
        changingCraft = false;
        slotsChanged(crafting);
        sendSnapshot();
    }

    private void giveBack(ItemStack stack) {
        if (stack.isEmpty()) return;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) deposit(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    public void clearCrafting() {
        if (player.level().isClientSide()) return;
        changingCraft = true;
        for (int i = 0; i < 9; i++) giveBack(crafting.removeItemNoUpdate(i));
        changingCraft = false;
        slotsChanged(crafting);
    }

    @Override
    public void slotsChanged(Container container) {
        if (changingCraft || !(player instanceof ServerPlayer serverPlayer)) return;
        CraftingInput input = crafting.asCraftInput();
        var recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level());
        ItemStack output = ItemStack.EMPTY;
        if (network() != null && recipe.isPresent() && result.setRecipeUsed(player.level(), serverPlayer, recipe.get())) {
            ItemStack assembled = recipe.get().value().assemble(input, player.registryAccess());
            if (assembled.isItemEnabled(player.level().enabledFeatures())) output = assembled;
        }
        result.setItem(0, output);
        setRemoteSlot(RESULT, output);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, output));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack original = slot.getItem().copy();
        if (original.isEmpty()) return ItemStack.EMPTY;
        if (index == RESULT) {
            if (network() == null) return ItemStack.EMPTY;
            for (int crafts = 0; crafts < 64 && slot.hasItem(); crafts++) {
                ItemStack output = slot.getItem().copy();
                if (!ItemStack.isSameItemSameComponents(original, output) || !fitsPlayer(output)) break;
                ItemStack taken = slot.remove(output.getCount());
                slot.onTake(player, taken);
                player.getInventory().add(taken);
            }
        } else if (index >= PLAYER_START) {
            deposit(slot.getItem()); slot.setChanged();
        } else {
            ItemStack stack = slot.getItem();
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) return ItemStack.EMPTY;
            slot.setChanged();
        }
        broadcastChanges();
        return ItemStack.EMPTY; // Do not repeat vanilla quick-move while automatic refill keeps the slot occupied.
    }

    private boolean fitsPlayer(ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack current : player.getInventory().items) {
            if (current.isEmpty()) remaining -= stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, stack)) remaining -= Math.max(0, current.getMaxStackSize() - current.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer && ticks++ % 20 == 0) {
            slotsChanged(crafting);
            sendSnapshot();
        }
    }

    public void sendSnapshot() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        DimensionsNet net = network();
        TerminalStock.Snapshot snapshot = TerminalStock.snapshot(serverPlayer, net);
        List<TerminalStock.Entry> stock = snapshot.entries();
        long version = ++revision;
        List<TerminalOrderSummary> summaries = TerminalOrders.get(serverPlayer.server).summaries(serverPlayer);
        int stockParts = Math.max(1, (stock.size() + 31) / 32);
        int parts = stockParts + summaries.size();
        for (int part = 0; part < parts; part++) {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (int i = part * 32; i < Math.min(stock.size(), (part + 1) * 32); i++) list.add(TerminalData.entry(stock.get(i), player.registryAccess()));
            tag.put("Stock", list);
            tag.putString("Name", net == null ? "" : net.getNetworkName().getString());
            if (part == 0) {
                ListTag sources = new ListTag();
                snapshot.addresses().forEach((network, address) -> {
                    CompoundTag source = new CompoundTag();
                    source.putUUID("Network", network);
                    source.putString("Address", address);
                    sources.add(source);
                });
                tag.put("Sources", sources);
            }
            if (part >= stockParts) {
                ListTag orderPart = new ListTag();
                orderPart.add(summaries.get(part - stockParts).save(player.registryAccess()));
                tag.put("Orders", orderPart);
            }
            if (acceptedSubmission != null) tag.putUUID("Accepted", acceptedSubmission);
            TerminalPackets.send(serverPlayer, containerId, session, version, part, part == parts - 1, tag);
        }
    }

    public void receive(long revision, int part, boolean last, CompoundTag tag) {
        if (revision <= clientRevision) return;
        if (part == 0) {
            receiving.clear(); receivingOrders.clear(); receivingSourceAddresses.clear();
            receivingRevision = revision; nextPart = 0;
        }
        if (revision != receivingRevision || part != nextPart++) return;
        ListTag list = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) receiving.add(TerminalData.entry(list.getCompound(i), player.registryAccess()));
        ListTag sources = tag.getList("Sources", Tag.TAG_COMPOUND);
        for (int i = 0; i < sources.size(); i++) {
            CompoundTag source = sources.getCompound(i);
            if (source.hasUUID("Network"))
                receivingSourceAddresses.put(source.getUUID("Network"), source.getString("Address"));
        }
        ListTag orderTags = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderTags.size(); i++)
            receivingOrders.add(TerminalOrderSummary.load(orderTags.getCompound(i), player.registryAccess()));
        if (last) {
            clientStock = List.copyOf(receiving); clientRevision = revision;
            clientOrders = List.copyOf(receivingOrders); clientNetworkName = tag.getString("Name");
            clientSourceAddresses = Map.copyOf(receivingSourceAddresses);
            if (tag.hasUUID("Accepted")) acceptedSubmission = tag.getUUID("Accepted");
        }
    }

    @Override
    public void removed(Player player) {
        clearCrafting();
        super.removed(player);
    }
}
