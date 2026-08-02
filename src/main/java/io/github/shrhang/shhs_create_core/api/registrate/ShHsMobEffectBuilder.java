package io.github.shrhang.shhs_create_core.api.registrate;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.AbstractBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ShHsMobEffectBuilder<T extends MobEffect, P>
        extends AbstractBuilder<MobEffect, T, P, ShHsMobEffectBuilder<T, P>> {

    private final NonNullSupplier<T> factory;

    public ShHsMobEffectBuilder(
            AbstractRegistrate<?> owner,
            P parent,
            String name,
            BuilderCallback callback,
            NonNullSupplier<T> factory
    ) {
        super(owner, parent, name, callback, Registries.MOB_EFFECT);
        this.factory = factory;
    }

    @Override
    protected T createEntry() {
        return factory.get();
    }

    @Override
    public ShHsMobEffectEntry<T> register() {
        return (ShHsMobEffectEntry<T>) super.register();
    }

    @Override
    protected RegistryEntry<MobEffect, T> createEntryWrapper(DeferredHolder<MobEffect, T> delegate) {
        return new ShHsMobEffectEntry<>(getOwner(), delegate);
    }

    public ShHsMobEffectBuilder<T, P> lang(String name) {
        return lang(MobEffect::getDescriptionId, name);
    }

    public ShHsMobEffectBuilder<T, P> desc(String desc) {
        getOwner().addRawLang("effect." + getOwner().getModid() + "." + getName() + ".description", desc);
        getOwner().addRawLang("effect." + getOwner().getModid() + "." + getName() + ".desc", desc);
        return this;
    }

    public static class ShHsMobEffectEntry<T extends MobEffect> extends RegistryEntry<MobEffect, T> {
        public ShHsMobEffectEntry(AbstractRegistrate<?> owner, DeferredHolder<MobEffect, T> delegate) {
            super(owner, delegate);
        }

        public static <T extends MobEffect> ShHsMobEffectEntry<T> cast(RegistryEntry<MobEffect, T> entry) {
            return RegistryEntry.cast(ShHsMobEffectEntry.class, entry);
        }
    }
}
