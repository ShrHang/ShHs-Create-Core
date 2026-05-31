package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.hostility.EmptyTraitItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;

public class Items {
    private static final CreateRegistrate REGISTRATE = ShHsCreateCore.REGISTRATE;

    public static final ItemEntry<EmptyTraitItem> EMPTY_TRAIT = REGISTRATE.item("empty_trait", EmptyTraitItem::new)
            .model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bg")))
            .lang("Empty Trait")
            .register();

    public static void register() {
    }
}
