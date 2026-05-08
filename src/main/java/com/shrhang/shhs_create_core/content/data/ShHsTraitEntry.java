package com.shrhang.shhs_create_core.content.data;

import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ShHsTraitEntry<T extends MobTrait> extends RegistryEntry<MobTrait, T> {
    public ShHsTraitEntry(ShHsRegistrate owner, DeferredHolder<MobTrait, T> delegate) {super(owner, delegate);}
}
