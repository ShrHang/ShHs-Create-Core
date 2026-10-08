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
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Frozen pre-batching implementation. Keep independent from the optimized implementation. */
final class PortableStockTickerCraftingReference {
    private PortableStockTickerCraftingReference() {}

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
        List<List<BigItemStack>> resolvedIngredients = new ArrayList<>();
        for (int i = 0; i < validIngredients.size(); i++) {
            resolvedIngredients.add(new ArrayList<>());
        }

        boolean everythingTaken = false;
        while (!everythingTaken) {
            everythingTaken = true;
            ingredientLoop:
            for (int i = 0; i < validIngredients.size(); i++) {
                List<BigItemStack> list = validIngredients.get(i);
                List<BigItemStack> resolvedList = resolvedIngredients.get(i);
                for (BigItemStack bigItemStack : list) {
                    if (bigItemStack.count == 0) {
                        continue;
                    }
                    bigItemStack.count -= 1;
                    everythingTaken = false;
                    for (BigItemStack resolvedItemStack : resolvedList) {
                        if (ItemStack.isSameItemSameComponents(resolvedItemStack.stack, bigItemStack.stack)) {
                            resolvedItemStack.count++;
                            continue ingredientLoop;
                        }
                    }
                    resolvedList.add(new BigItemStack(bigItemStack.stack, 1));
                    continue ingredientLoop;
                }
            }
        }

        return resolvedIngredients;
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
            int maxCrafts = targetCount - craftedCount;
            int availableCrafts = 0;
            boolean itemsExhausted = false;
            while (availableCrafts < maxCrafts && !itemsExhausted) {
                List<BigItemStack> previousSnapshot = BigItemStack.duplicateWrappers(mutableOrder);
                itemsExhausted = true;
                boolean failedPattern = false;
                for (BigItemStack patternStack : pattern.stacks()) {
                    if (patternStack.stack.isEmpty()) {
                        continue;
                    }
                    boolean matched = false;
                    for (BigItemStack ordered : mutableOrder) {
                        if (!ItemStack.isSameItemSameComponents(ordered.stack, patternStack.stack) || ordered.count == 0) {
                            continue;
                        }
                        ordered.count -= 1;
                        itemsExhausted = false;
                        matched = true;
                        break;
                    }
                    if (!matched) {
                        mutableOrder = previousSnapshot;
                        failedPattern = true;
                        break;
                    }
                }
                if (failedPattern) {
                    break;
                }
                availableCrafts++;
            }
            if (availableCrafts == 0) {
                break;
            }
            craftList.add(new CraftingEntry(pattern, availableCrafts));
            craftedCount += availableCrafts;
        }
        return craftList;
    }
}
