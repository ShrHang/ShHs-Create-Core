package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

final class PortableStockTickerCrafting {
    private PortableStockTickerCrafting() {}

    static Pair<Integer, List<List<BigItemStack>>> maxCraftable(List<Ingredient> ingredients,
            int outputCount, InventorySummary summary, Function<ItemStack, Integer> countModifier,
            int newTypeLimit, Predicate<ItemStack> isOrdered) {
        List<List<BigItemStack>> validEntriesByIngredient = new ArrayList<>();
        List<BigItemStack> alreadyCreated = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<BigItemStack> valid = new ArrayList<>();
            for (List<BigItemStack> list : summary.getItemMap().values()) {
                entries:
                for (BigItemStack entry : list) {
                    if (!ingredient.test(entry.stack)) {
                        continue;
                    }
                    for (BigItemStack visited : alreadyCreated) {
                        if (!ItemStack.isSameItemSameComponents(visited.stack, entry.stack)) {
                            continue;
                        }
                        valid.add(visited);
                        continue entries;
                    }
                    BigItemStack asBis = new BigItemStack(entry.stack, summary.getCountOf(entry.stack) + countModifier.apply(entry.stack));
                    if (asBis.count > 0) {
                        valid.add(asBis);
                        alreadyCreated.add(asBis);
                    }
                }
            }
            if (valid.isEmpty()) {
                return Pair.of(0, List.of());
            }
            valid.sort((left, right) -> -Integer.compare(summary.getCountOf(left.stack), summary.getCountOf(right.stack)));
            validEntriesByIngredient.add(valid);
        }

        if (newTypeLimit != -1) {
            int toRemove = (int) validEntriesByIngredient.stream()
                    .flatMap(List::stream)
                    .filter(entry -> !isOrdered.test(entry.stack))
                    .distinct()
                    .count() - newTypeLimit;
            for (int i = 0; i < toRemove; i++) {
                removeLeastEssentialItemStack(validEntriesByIngredient, isOrdered);
            }
        }

        validEntriesByIngredient = resolveIngredientAmounts(validEntriesByIngredient);
        int minCount = Integer.MAX_VALUE;
        for (List<BigItemStack> list : validEntriesByIngredient) {
            int sum = 0;
            for (BigItemStack entry : list) {
                sum += entry.count;
            }
            minCount = Math.min(sum, minCount);
        }
        if (minCount == 0) {
            return Pair.of(0, List.of());
        }

        return Pair.of(minCount * outputCount, validEntriesByIngredient);
    }

    private static void removeLeastEssentialItemStack(List<List<BigItemStack>> validIngredients, Predicate<ItemStack> isOrdered) {
        List<BigItemStack> longest = null;
        int most = 0;
        for (List<BigItemStack> list : validIngredients) {
            int count = (int) list.stream().filter(entry -> !isOrdered.test(entry.stack)).count();
            if (longest != null && count <= most) {
                continue;
            }
            longest = list;
            most = count;
        }
        if (longest == null || longest.isEmpty()) {
            return;
        }

        BigItemStack chosen = null;
        for (int i = 0; i < longest.size(); i++) {
            BigItemStack entry = longest.get(longest.size() - 1 - i);
            if (isOrdered.test(entry.stack)) {
                continue;
            }
            chosen = entry;
            break;
        }
        if (chosen == null) {
            return;
        }
        for (List<BigItemStack> list : validIngredients) {
            list.remove(chosen);
        }
    }

    static List<List<BigItemStack>> resolveIngredientAmounts(List<List<BigItemStack>> validIngredients) {
        List<List<BigItemStack>> resolved = new ArrayList<>();
        for (int i = 0; i < validIngredients.size(); i++) {
            resolved.add(new ArrayList<>());
        }
        BigItemStack[] selected = new BigItemStack[validIngredients.size()];
        Map<BigItemStack, Integer> uses = new IdentityHashMap<>();
        while (true) {
            uses.clear();
            for (int i = 0; i < validIngredients.size(); i++) {
                selected[i] = firstAvailable(validIngredients.get(i));
                if (selected[i] != null) {
                    uses.merge(selected[i], 1, Integer::sum);
                }
            }
            if (uses.isEmpty()) {
                return resolved;
            }
            int rounds = Integer.MAX_VALUE;
            for (var entry : uses.entrySet()) {
                rounds = Math.min(rounds, entry.getKey().count / entry.getValue());
            }
            if (rounds == 0) {
                // A shared candidate runs out within this round; preserve slot-by-slot selection.
                for (int i = 0; i < validIngredients.size(); i++) {
                    BigItemStack candidate = firstAvailable(validIngredients.get(i));
                    if (candidate != null) {
                        candidate.count--;
                        addResolved(resolved.get(i), candidate.stack, 1);
                    }
                }
                continue;
            }
            for (var entry : uses.entrySet()) {
                entry.getKey().count -= rounds * entry.getValue();
            }
            for (int i = 0; i < selected.length; i++) {
                if (selected[i] != null) {
                    addResolved(resolved.get(i), selected[i].stack, rounds);
                }
            }
        }
    }

    private static BigItemStack firstAvailable(List<BigItemStack> candidates) {
        for (BigItemStack candidate : candidates) {
            if (candidate.count > 0) {
                return candidate;
            }
        }
        return null;
    }

    private static void addResolved(List<BigItemStack> result, ItemStack stack, int count) {
        for (BigItemStack entry : result) {
            if (ItemStack.isSameItemSameComponents(entry.stack, stack)) {
                entry.count += count;
                return;
            }
        }
        result.add(new BigItemStack(stack, count));
    }

    static List<CraftingEntry> planCrafts(CraftingRecipe craftingRecipe, int targetCount,
            List<BigItemStack> itemsToOrder) {
        List<CraftingEntry> craftList = new ArrayList<>();
        int craftedCount = 0;
        List<BigItemStack> mutableOrder = BigItemStack.duplicateWrappers(itemsToOrder);
        while (craftedCount < targetCount) {
            PackageOrder pattern = new PackageOrder(
                    FactoryPanelScreen.convertRecipeToPackageOrderContext(craftingRecipe, mutableOrder, true)
            );
            int availableCrafts = consumePattern(pattern, mutableOrder, targetCount - craftedCount);
            if (availableCrafts == 0) {
                break;
            }
            craftList.add(new CraftingEntry(pattern, availableCrafts));
            craftedCount += availableCrafts;
        }
        return craftList;
    }

    static int consumePattern(PackageOrder pattern, List<BigItemStack> order, int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<BigItemStack> demands = new ArrayList<>();
        for (BigItemStack entry : pattern.stacks()) {
            if (!entry.stack.isEmpty()) {
                addResolved(demands, entry.stack, 1);
            }
        }
        // The original loop emits one craft per empty pattern, not one combined entry.
        if (demands.isEmpty()) {
            return 1;
        }
        int crafts = limit;
        for (BigItemStack demand : demands) {
            long available = 0;
            for (BigItemStack entry : order) {
                if (ItemStack.isSameItemSameComponents(entry.stack, demand.stack)) {
                    available += entry.count;
                }
            }
            crafts = (int) Math.min(crafts, available / demand.count);
        }
        if (crafts == 0) {
            return 0;
        }
        for (BigItemStack demand : demands) {
            long remaining = (long) crafts * demand.count;
            for (BigItemStack entry : order) {
                if (!ItemStack.isSameItemSameComponents(entry.stack, demand.stack)) {
                    continue;
                }
                int consumed = (int) Math.min(remaining, entry.count);
                entry.count -= consumed;
                remaining -= consumed;
                if (remaining == 0) {
                    break;
                }
            }
        }
        return crafts;
    }
}
