package com.shrhang.shhs_create_core.content.data;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.AbstractBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ShHsPotionBuilder<P>
        extends AbstractBuilder<Potion, Potion, P, ShHsPotionBuilder<P>> {

    private final List<NonNullSupplier<MobEffectInstance>> effects = new ArrayList<>();
    @Nullable
    private String baseName;

    public ShHsPotionBuilder(
            AbstractRegistrate<?> owner,
            P parent,
            String name,
            BuilderCallback callback
    ) {
        super(owner, parent, name, callback, Registries.POTION);
    }

    @Override
    protected Potion createEntry() {
        MobEffectInstance[] instances = effects.stream()
                .map(NonNullSupplier::get)
                .toArray(MobEffectInstance[]::new);
        return baseName == null ? new Potion(instances) : new Potion(baseName, instances);
    }

    @Override
    public ShHsPotionEntry register() {
        return (ShHsPotionEntry) super.register();
    }

    @Override
    protected RegistryEntry<Potion, Potion> createEntryWrapper(DeferredHolder<Potion, Potion> delegate) {
        return new ShHsPotionEntry(getOwner(), delegate);
    }

    public ShHsPotionBuilder<P> baseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    public ShHsPotionBuilder<P> effect(Holder<MobEffect> effect, int duration) {
        return effect(effect, duration, 0);
    }

    public ShHsPotionBuilder<P> effect(Holder<MobEffect> effect, int duration, int amplifier) {
        effects.add(() -> new MobEffectInstance(effect, duration, amplifier));
        return this;
    }

    public ShHsPotionBuilder<P> effect(MobEffectInstance effect) {
        effects.add(() -> effect);
        return this;
    }

    public ShHsPotionBuilder<P> lang(String name) {
        String keyName = baseName == null ? getName() : baseName;
        getOwner().addRawLang("item.minecraft.potion.effect." + keyName, "Potion of " + name);
        getOwner().addRawLang("item.minecraft.splash_potion.effect." + keyName, "Splash Potion of " + name);
        getOwner().addRawLang("item.minecraft.lingering_potion.effect." + keyName, "Lingering Potion of " + name);
        getOwner().addRawLang("item.minecraft.tipped_arrow.effect." + keyName, "Arrow of " + name);
        return this;
    }

    public static class ShHsPotionEntry extends RegistryEntry<Potion, Potion> {
        public ShHsPotionEntry(AbstractRegistrate<?> owner, DeferredHolder<Potion, Potion> delegate) {
            super(owner, delegate);
        }

        public static ShHsPotionEntry cast(RegistryEntry<Potion, Potion> entry) {
            return RegistryEntry.cast(ShHsPotionEntry.class, entry);
        }
    }
}
