package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared recipe planning for server execution and JEI's missing-material preview. */
public final class TerminalCrafting {
    private TerminalCrafting() {
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
