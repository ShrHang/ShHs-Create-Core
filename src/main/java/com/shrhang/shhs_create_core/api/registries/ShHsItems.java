package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.content.hostility.EmptyTraitItem;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsItems {
    public static final ItemEntry<PortableStockTickerItem> PORTABLE_STOCK_TICKER =
            REGISTRATE.item("portable_stock_ticker", PortableStockTickerItem::new)
                    .properties(p -> p.stacksTo(1))
                    .model((ctx , prov) -> {})
                    .lang("Portable Stock Ticker")
                    .tooltipSummary("Allows you to connect to a _Stock Ticker_ and remotely view its stock information.")
                    .tooltipBehaviour(1, "When used", "If in the _same dimension_ as the connected _Stock Ticker_ and a _Stock Keeper_ exists, opens the request menu.")
                    .tooltipBehaviour(2, "When used in Sneak on Blocks", "If the target block is a _Stock Ticker_, connects to it.")
                    .register();

    public static final ItemEntry<EmptyTraitItem> EMPTY_TRAIT = REGISTRATE.item("empty_trait", EmptyTraitItem::new)
            .model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bg")))
            .lang("Empty Trait")
            .tooltipSummary("An _Empty Vessel_ eager to absorb a little _Hostility_ from nearby creatures.")
            .tooltipBehaviour(1, "When Used while Sneaking", "After _Charge_, _absorbs_ _one level_ of a random _Trait_ from the target and turns into its _Trait Item_.")
            .register();

    public static void register() {
    }
}
