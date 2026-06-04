package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.data.ShHsRegistrate;
import com.shrhang.shhs_create_core.content.hostility.EmptyTraitItem;
import com.tterrag.registrate.util.entry.ItemEntry;

public class Items {
    private static final ShHsRegistrate REGISTRATE = ShHsCreateCore.REGISTRATE;

    public static final ItemEntry<EmptyTraitItem> EMPTY_TRAIT = REGISTRATE.item("empty_trait", EmptyTraitItem::new)
            .model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bg")))
            .lang("Empty Trait")
            .tooltipSummary("An _Empty Vessel_ eager to absorb a little _Hostility_ from nearby creatures.")
            .tooltipBehaviour(1, "When Used while Sneaking",
                    "After _Charge_, _absorbs_ _one level_ of a random _Trait_ from the target and turns into its _Trait Item_.")
            .register();

    public static void register() {
    }
}
