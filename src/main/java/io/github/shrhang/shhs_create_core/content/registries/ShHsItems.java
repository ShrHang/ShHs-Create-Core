package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.content.hostility.EmptyTraitItem;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import top.theillusivec4.curios.api.CuriosTags;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsItems {
    public static final ItemEntry<PortableStockTickerItem> PORTABLE_STOCK_TICKER;
    public static final ItemEntry<EmptyTraitItem> EMPTY_TRAIT;

    static {
        PORTABLE_STOCK_TICKER = REGISTRATE.item("portable_stock_ticker", PortableStockTickerItem::new)
                .properties(p -> p.stacksTo(1))
                .model((ctx , prov) -> {})
                .lang("Portable Stock Ticker")
                .tooltipSummary("Allows you to connect to a _Logistics Network_ and remotely view its stock information.")
                .tooltipBehaviour(1, "When used", "If linked network exists and _is loaded_, opens a request menu.")
                .tooltipBehaviour(2, "When used in Sneak on Blocks", "If the target block is a _Stock Ticker_, _Stock Link_ or _Redstone Requester_, links to its network.")
                .tag(CuriosTags.CURIO)
                .register();

        EMPTY_TRAIT = REGISTRATE.item("empty_trait", EmptyTraitItem::new)
                .model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bg")))
                .lang("Empty Trait")
                .tooltipSummary("An _Empty Vessel_ eager to absorb a little _Hostility_ from nearby creatures.")
                .tooltipBehaviour(1, "When Used while Sneaking", "After _Charge_, _absorbs_ _one level_ of a random _Trait_ from the target and turns into its _Trait Item_.")
                .register();
    }

    public static void register() {
    }
}
