package io.github.shrhang.shhs_create_core.content.registries;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.hostility.items.EmptyTraitItem;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
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
                .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                        .define('A', AllBlocks.STOCK_LINK.asItem())
                        .define('B', AllItems.LINKED_CONTROLLER.asItem())
                        .define('C', AllBlocks.DISPLAY_LINK.asItem())
                        .pattern("A")
                        .pattern("B")
                        .pattern("C")
                        .unlockedBy("has_stock_link", RegistrateRecipeProvider.has(AllBlocks.STOCK_LINK.asItem()))
                        .save(prov, ShHsCreateCore.rl("crafting/portable_stock_ticker")))
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
