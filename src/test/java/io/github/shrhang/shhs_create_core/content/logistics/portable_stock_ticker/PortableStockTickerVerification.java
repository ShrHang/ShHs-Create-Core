package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.fml.loading.LoadingModList;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Standalone regression runner; invoked by verifyPortableStockTicker and check. */
public final class PortableStockTickerVerification {
    public static void main(String[] args) {
        // Only vanilla fixtures are needed; no mod lifecycle or game client is launched.
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("distinct component"));
        List<ItemStack> types = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), named);
        Random random = new Random(0x5A17C0DEL);
        for (int trial = 0; trial < 10_000; trial++) {
            List<BigItemStack> pool = new ArrayList<>();
            for (int i = 0; i < 1 + random.nextInt(6); i++) {
                pool.add(new BigItemStack(types.get(random.nextInt(types.size())), random.nextInt(20)));
            }
            List<List<BigItemStack>> slots = new ArrayList<>();
            for (int i = 0; i < random.nextInt(10); i++) {
                List<BigItemStack> candidates = new ArrayList<>();
                for (int j = 0; j < random.nextInt(7); j++) {
                    candidates.add(pool.get(random.nextInt(pool.size())));
                }
                slots.add(candidates);
            }
            equalSlots(PortableStockTickerCraftingReference.resolveIngredientAmounts(copySlots(slots)),
                    PortableStockTickerCrafting.resolveIngredientAmounts(copySlots(slots)));

            List<BigItemStack> pattern = new ArrayList<>();
            for (int i = 0; i < random.nextInt(10); i++) {
                pattern.add(new BigItemStack(random.nextBoolean() ? ItemStack.EMPTY : types.get(random.nextInt(types.size())), 1));
            }
            List<BigItemStack> expected = BigItemStack.duplicateWrappers(pool);
            List<BigItemStack> actual = BigItemStack.duplicateWrappers(pool);
            int limit = random.nextInt(30);
            check(referenceConsume(pattern, expected, limit)
                    == PortableStockTickerCrafting.consumePattern(new PackageOrder(pattern), actual, limit), "pattern count");
            equalStacks(expected, actual);
        }
        checkPlanning(types);
        checkSummaryCopyOrder();
        checkCraftEntries(types, random);
        checkLargeCounts(types.getFirst());
        checkSnapshots();
        checkVisibleRanges(random);
        System.out.println("Portable stock ticker: 10000 allocation/pattern cases, 500 craft-entry cases, 1000 visible-range cases and boundary checks passed.");
    }

    private static void checkPlanning(List<ItemStack> types) {
        InventorySummary summary = new InventorySummary();
        summary.add(types.get(0), 2);
        summary.add(types.get(1), 1);
        summary.add(types.get(2), 3);
        List<BigItemStack> before = BigItemStack.duplicateWrappers(summary.getStacksByCount());
        List<Ingredient> ingredients = List.of(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), Ingredient.EMPTY,
                Ingredient.of(Items.GOLD_INGOT));
        for (int limit = -1; limit <= 9; limit++) {
            for (int modifier = -2; modifier <= 0; modifier++) {
                int delta = modifier;
                var expected = PortableStockTickerCraftingReference.maxCraftable(ingredients, 4, summary, stack -> delta,
                        limit, stack -> ItemStack.isSameItemSameComponents(stack, types.getFirst()));
                var actual = PortableStockTickerCrafting.maxCraftable(ingredients, 4, summary, stack -> delta,
                        limit, stack -> ItemStack.isSameItemSameComponents(stack, types.getFirst()));
                check(expected.getFirst().equals(actual.getFirst()), "planning output count");
                equalSlots(expected.getSecond(), actual.getSecond());
            }
        }
        equalStacks(before, summary.getStacksByCount());
        InventorySummary regression = new InventorySummary();
        regression.add(types.get(0), 2);
        regression.add(types.get(1), 1);
        check(PortableStockTickerCrafting.maxCraftable(ingredients, 1, regression, stack -> 0, -1, stack -> false)
                .getFirst() == 1, "A/B then B polling regression");
    }

    private static void checkLargeCounts(ItemStack stack) {
        BigItemStack shared = new BigItemStack(stack, BigItemStack.INF);
        var result = PortableStockTickerCrafting.resolveIngredientAmounts(List.of(List.of(shared), List.of(shared), List.of(shared)));
        check(result.get(0).getFirst().count == 333_333_334, "large first slot");
        check(result.get(1).getFirst().count == 333_333_333, "large second slot");
        check(result.get(2).getFirst().count == 333_333_333, "large third slot");
        List<BigItemStack> order = new ArrayList<>(List.of(new BigItemStack(stack, BigItemStack.INF)));
        check(PortableStockTickerCrafting.consumePattern(new PackageOrder(List.of(new BigItemStack(stack, 1),
                new BigItemStack(stack, 1))), order, BigItemStack.INF) == 500_000_000, "large pattern");
        check(order.getFirst().count == 0, "large pattern remainder");
    }

    private static void checkSummaryCopyOrder() {
        var items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(200).toList();
        InventorySummary summary = new InventorySummary();
        for (var item : items) summary.add(new ItemStack(item), 2);
        List<Ingredient> ingredients = List.of(Ingredient.of(items.stream().map(ItemStack::new)));
        var expected = PortableStockTickerCraftingReference.maxCraftable(ingredients, 1, summary.copy(), stack -> 0,
                -1, stack -> false);
        var actual = PortableStockTickerCrafting.maxCraftable(ingredients, 1, summary, stack -> 0,
                -1, stack -> false);
        equalSlots(expected.getSecond(), actual.getSecond());
    }

    private static void checkCraftEntries(List<ItemStack> types, Random random) {
        for (int trial = 0; trial < 500; trial++) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int slot = 0; slot < 1 + random.nextInt(9); slot++) {
                ingredients.add(random.nextBoolean() ? Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT)
                        : Ingredient.of(Items.IRON_INGOT));
            }
            var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC,
                    new ItemStack(Items.IRON_BLOCK, 1 + random.nextInt(4)), ingredients);
            List<BigItemStack> order = new ArrayList<>();
            for (var type : types) order.add(new BigItemStack(type, random.nextInt(20)));
            List<BigItemStack> before = BigItemStack.duplicateWrappers(order);
            int target = random.nextInt(30);
            var expected = PortableStockTickerCraftingReference.planCrafts(recipe, target, order);
            var actual = PortableStockTickerCrafting.planCrafts(recipe, target, order);
            check(expected.size() == actual.size(), "craft entry count");
            for (int i = 0; i < expected.size(); i++) {
                check(expected.get(i).count() == actual.get(i).count(), "craft repetitions");
                equalStacks(expected.get(i).pattern().stacks(), actual.get(i).pattern().stacks());
            }
            equalStacks(before, order);
        }
    }

    private static void checkSnapshots() {
        PortableStockTickerClientData.clear();
        UUID id = UUID.randomUUID();
        var snapshot = PortableStockTickerClientData.getOrCreate(id);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        long initial = snapshot.revision();
        check(snapshot.requestStock(0, true), "initial request");
        check(!snapshot.requestStock(1, false), "merge pending");
        check(!snapshot.requestStock(2, true), "queue forced refresh");
        PortableStockTickerClientData.receive(id, List.of(new BigItemStack(iron, 5)), false);
        check(snapshot.revision() == initial && snapshot.summary().getCountOf(iron) == 0,
                "partial response is not published");
        PortableStockTickerClientData.receive(id, List.of(), true);
        check(snapshot.revision() != initial && snapshot.summary().getCountOf(iron) == 5,
                "complete response published");
        check(snapshot.requestStock(3, false), "forced post-order refresh retained");
        for (int tick = 4; tick < 103; tick++) {
            snapshot.tick();
            check(!snapshot.requestStock(tick, false), "pending timeout interval");
        }
        snapshot.tick();
        check(snapshot.requestStock(103, false), "timeout retry");
        PortableStockTickerClientData.receive(id, List.of(), true);
        for (int tick = 104; tick < 119; tick++) {
            snapshot.tick();
            check(!snapshot.requestStock(tick, false), "late response throttle");
        }
        snapshot.tick();
        check(snapshot.requestStock(119, false), "normal refresh cadence");
        long previous = snapshot.revision();
        PortableStockTickerClientData.clear();
        check(PortableStockTickerClientData.getOrCreate(id).revision() != previous, "recreated revision");
        PortableStockTickerClientData.clear();
    }

    private static void checkVisibleRanges(Random random) {
        int sliceHeight = AllGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight();
        for (int trial = 0; trial < 1000; trial++) {
            int size = random.nextInt(10_000);
            int top = random.nextInt(100);
            int height = 100 + random.nextInt(600);
            int maxScroll = (int) Math.max(0, ((2 + Mth.ceil(size / 9f)) * 20 - (height - 84) + 50) / 20f);
            float scroll = random.nextFloat() * maxScroll;
            int firstRow = Math.max(0, Mth.floor((scroll * 20 - 37) / 20) - 1);
            List<Integer> expected = new ArrayList<>();
            List<Integer> actual = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                float y = top + 37 + ((float) index / 9) * 20 - scroll * 20;
                if (y >= top && y <= top + height - 72) expected.add(index);
            }
            for (int index = firstRow * 9; index < size; index++) {
                float y = top + 37 + ((float) index / 9) * 20 - scroll * 20;
                if (y < top) continue;
                if (y > top + height - 72) break;
                actual.add(index);
            }
            check(expected.equals(actual), "visible item indices");
            expected.clear();
            actual.clear();
            for (int y = -2; y < maxScroll * 20 + height - 72; y += sliceHeight) {
                if (y - scroll * 20 >= -20 && y - scroll * 20 <= height - 72) expected.add(y);
            }
            int firstSlice = Math.max(0, Mth.floor((scroll * 20 - 18) / sliceHeight) - 1);
            for (int y = -2 + firstSlice * sliceHeight; y < maxScroll * 20 + height - 72; y += sliceHeight) {
                if (y - scroll * 20 < -20) continue;
                if (y - scroll * 20 > height - 72) break;
                actual.add(y);
            }
            check(expected.equals(actual), "visible background slices");
        }
    }

    private static List<List<BigItemStack>> copySlots(List<List<BigItemStack>> slots) {
        var copies = new IdentityHashMap<BigItemStack, BigItemStack>();
        List<List<BigItemStack>> result = new ArrayList<>();
        for (var slot : slots) {
            List<BigItemStack> copy = new ArrayList<>();
            for (var entry : slot) {
                copy.add(copies.computeIfAbsent(entry, value -> new BigItemStack(value.stack, value.count)));
            }
            result.add(copy);
        }
        return result;
    }

    private static int referenceConsume(List<BigItemStack> pattern, List<BigItemStack> order, int limit) {
        int crafts = 0;
        boolean exhausted = false;
        while (crafts < limit && !exhausted) {
            List<BigItemStack> previous = BigItemStack.duplicateWrappers(order);
            exhausted = true;
            for (var slot : pattern) {
                if (slot.stack.isEmpty()) continue;
                boolean matched = false;
                for (var entry : order) {
                    if (entry.count == 0 || !ItemStack.isSameItemSameComponents(entry.stack, slot.stack)) continue;
                    entry.count--;
                    exhausted = false;
                    matched = true;
                    break;
                }
                if (!matched) {
                    order.clear();
                    order.addAll(previous);
                    return crafts;
                }
            }
            crafts++;
        }
        return crafts;
    }

    private static void equalSlots(List<List<BigItemStack>> expected, List<List<BigItemStack>> actual) {
        check(expected.size() == actual.size(), "slot count");
        for (int i = 0; i < expected.size(); i++) equalStacks(expected.get(i), actual.get(i));
    }

    private static void equalStacks(List<BigItemStack> expected, List<BigItemStack> actual) {
        check(expected.size() == actual.size(), "stack count");
        for (int i = 0; i < expected.size(); i++) {
            check(expected.get(i).count == actual.get(i).count
                    && ItemStack.isSameItemSameComponents(expected.get(i).stack, actual.get(i).stack), "stack order/components/count");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
