package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Terminal item access and crafting materials; recipe planning is also used by JEI. */
public final class TerminalInventory {
    private final Player player;
    private final int netId;

    TerminalInventory(Player player, int netId) {
        this.player = player;
        this.netId = netId;
    }

    long stored(ItemStackKey key) {
        DimensionsNet net = network();
        return net == null ? 0 : net.getUnifiedStorage().getStorage().stream()
                .filter(entry -> entry.key() instanceof ItemStackKey itemKey && itemKey.equals(key))
                .mapToLong(KeyAmount::amount).sum();
    }

    DimensionsNet network() {
        if (player.level().isClientSide())
            return null;
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        return primary != null && primary.getId() == netId && primary.getPlayers().contains(player.getUUID()) ? primary : null;
    }

    int inventorySpace(ItemStack template) {
        int space = 0;
        for (ItemStack current : player.getInventory().items) {
            if (current.isEmpty()) space += template.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, template))
                space += Math.max(0, current.getMaxStackSize() - current.getCount());
            if (space >= template.getMaxStackSize()) return template.getMaxStackSize();
        }
        return space;
    }

    void depositAmount(ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty()) return;
        DimensionsNet net = network();
        if (net == null) return;
        int offered = Math.min(count, stack.getCount());
        long remainder = net.getUnifiedStorage().insert(new ItemStackKey(stack), offered, false).amount();
        stack.shrink(offered - (int) remainder);
    }

    void deposit(ItemStack stack) {
        DimensionsNet net = network();
        if (net == null || stack.isEmpty()) return;
        stack.setCount((int) net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount());
    }

    ItemStack withdraw(ItemStackKey key, int count) {
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

    ItemStack withdrawLocal(ItemStackKey key, int count) {
        DimensionsNet net = network();
        if (count <= 0 || net == null)
            return ItemStack.EMPTY;
        int obtained = (int) net.getUnifiedStorage().extract(key, count, false, false).amount();
        return obtained == 0 ? ItemStack.EMPTY : key.copyStackWithCount(obtained);
    }

    Map<ItemStackKey, Long> availableForCrafting(List<TerminalStock.Entry> clientStock, CraftingContainer crafting) {
        Map<ItemStackKey, Long> pool = new LinkedHashMap<>();
        if (player.level().isClientSide()) {
            for (var entry : clientStock)
                if (entry.network() == null)
                    pool.merge(new ItemStackKey(entry.stack()), entry.amount(), Long::sum);
        } else {
            DimensionsNet net = network();
            if (net != null)
                for (var entry : net.getUnifiedStorage().getStorage())
                    if (entry.key() instanceof ItemStackKey key)
                        pool.merge(key, entry.amount(), Long::sum);
        }
        for (ItemStack stack : player.getInventory().items)
            if (!stack.isEmpty())
                pool.merge(new ItemStackKey(stack), (long) stack.getCount(), Long::sum);
        for (ItemStack stack : crafting.getItems())
            if (!stack.isEmpty())
                pool.merge(new ItemStackKey(stack), (long) stack.getCount(), Long::sum);
        return pool;
    }

    void giveBack(ItemStack stack) {
        if (stack.isEmpty())
            return;
        player.getInventory().add(stack);
        if (!stack.isEmpty())
            deposit(stack);
        if (!stack.isEmpty())
            player.drop(stack, false);
    }

    boolean fitsPlayer(ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack current : player.getInventory().items) {
            if (current.isEmpty()) remaining -= stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, stack)) remaining -= Math.max(0, current.getMaxStackSize() - current.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static List<Ingredient> grid(CraftingRecipe recipe) {
        List<Ingredient> grid = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() > 3 || shaped.getHeight() > 3)
                return List.of();
            for (int y = 0; y < shaped.getHeight(); y++) {
                for (int x = 0; x < shaped.getWidth(); x++)
                    grid.set(y * 3 + x, shaped.getIngredients().get(y * shaped.getWidth() + x));
            }
        } else {
            if (recipe.getIngredients().size() > 9)
                return List.of();
            for (int i = 0; i < recipe.getIngredients().size(); i++)
                grid.set(i, recipe.getIngredients().get(i));
        }
        return grid;
    }

    public static ItemStack[] plan(CraftingRecipe recipe, Map<ItemStackKey, Long> available) {
        List<Ingredient> ingredients = grid(recipe);
        if (ingredients.isEmpty())
            return null;
        List<ItemStackKey> candidates = available.keySet().stream()
                .filter(key -> available.get(key) > 0)
                .sorted(Comparator.comparingLong((ItemStackKey key) -> available.get(key)).reversed())
                .toList();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++)
            if (!ingredients.get(i).isEmpty())
                slots.add(i);
        slots.sort(Comparator.comparingLong(index -> candidates.stream()
                .filter(key -> ingredients.get(index).test(key.getReadOnlyStack())).count()));
        ItemStack[] result = new ItemStack[9];
        Arrays.fill(result, ItemStack.EMPTY);
        return assign(ingredients, slots, candidates, new HashMap<>(available), result, 0, new int[]{10000}) ? result : null;
    }

    private static boolean assign(List<Ingredient> ingredients, List<Integer> slots, List<ItemStackKey> candidates,
                                  Map<ItemStackKey, Long> pool, ItemStack[] result, int depth, int[] budget) {
        if (depth == slots.size())
            return true;
        if (--budget[0] < 0)
            return false;
        int slot = slots.get(depth);
        for (ItemStackKey key : candidates) {
            long count = pool.getOrDefault(key, 0L);
            if (count <= 0 || !ingredients.get(slot).test(key.getReadOnlyStack()))
                continue;
            pool.put(key, count - 1);
            result[slot] = key.copyStackWithCount(1);
            if (assign(ingredients, slots, candidates, pool, result, depth + 1, budget))
                return true;
            pool.put(key, count);
        }
        result[slot] = ItemStack.EMPTY;
        return false;
    }
}
