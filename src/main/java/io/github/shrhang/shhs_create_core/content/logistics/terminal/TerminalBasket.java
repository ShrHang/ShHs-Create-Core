package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Selection limits and submission identity, independent of screen coordinates. */
final class TerminalBasket {
    private final LinkedHashMap<TerminalData.Selection, Integer> basket = new LinkedHashMap<>();
    private final Supplier<List<TerminalStock.Entry>> stock;
    private UUID submission;

    TerminalBasket(Supplier<List<TerminalStock.Entry>> stock) {
        this.stock = stock;
    }

    List<Map.Entry<TerminalData.Selection, Integer>> entries() {
        return basket.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList();
    }

    int size() {
        return basket.size();
    }

    void acknowledge(UUID accepted) {
        if (submission != null && submission.equals(accepted)) {
            basket.clear();
            submission = null;
        }
    }

    void submit(DimensionLogisticsTerminalMenu menu) {
        if (!valid()) return;
        if (submission == null) submission = UUID.randomUUID();
        List<TerminalStock.Entry> entries = basket.entrySet().stream()
                .map(e -> new TerminalStock.Entry(e.getKey().key().copyStackWithCount(1),
                        e.getValue(), e.getKey().network(), true))
                .toList();
        TerminalPackets.requestSubmit(menu, submission, entries);
    }

    TerminalStock.Entry lookup(TerminalData.Selection selection) {
        for (TerminalStock.Entry entry : stock.get())
            if (Objects.equals(entry.network(), selection.network()) && selection.key().equals(new ItemStackKey(entry.stack())))
                return entry;
        return null;
    }

    boolean valid() {
        if (basket.isEmpty() || basket.size() > TerminalData.MAX_ORDER_LINES) return false;
        for (var selected : basket.entrySet()) {
            TerminalStock.Entry entry = lookup(selected.getKey());
            if (selected.getKey().network() == null || entry == null || !entry.requestable()
                    || selected.getValue() > entry.amount()) return false;
        }
        return true;
    }

    boolean change(TerminalData.Selection key, int delta) {
        if (key.network() == null) return false;
        TerminalStock.Entry stock = lookup(key);
        int old = basket.getOrDefault(key, 0);
        if (delta > 0 && (stock == null || !stock.requestable()
                || basket.size() >= TerminalData.MAX_ORDER_LINES && old == 0)) return false;
        long max = stock == null ? old : stock.amount();
        int total = basket.values().stream().mapToInt(Integer::intValue).sum();
        long upper = Math.clamp((long) TerminalData.MAX_ITEMS - total + old, 0L, max);
        int amount = (int) Math.clamp((long) old + delta, 0L, upper);
        if (amount == 0) basket.remove(key); else basket.put(key, amount);
        submission = null;
        return amount != old;
    }

}
