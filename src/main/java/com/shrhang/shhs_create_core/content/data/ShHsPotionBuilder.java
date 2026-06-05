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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShHsPotionBuilder<P>
        extends AbstractBuilder<Potion, Potion, P, ShHsPotionBuilder<P>> {

    private static final List<Consumer<PotionBrewing.Builder>> BREWING_RECIPES = new ArrayList<>();

    private final List<NonNullSupplier<MobEffectInstance>> effects = new ArrayList<>();
    private final List<BrewingRecipeFactory> brewingRecipes = new ArrayList<>();
    @Nullable
    private String baseName; // 原版习惯让药水的长效、强效等变体使用与基础药水相同的语言键，因此该变量用于记录基础版药水的名称。

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
        ShHsPotionEntry entry = (ShHsPotionEntry) super.register();
        brewingRecipes.forEach(recipe -> BREWING_RECIPES.add(builder -> recipe.register(builder, entry)));
        return entry;
    }

    @Override
    protected RegistryEntry<Potion, Potion> createEntryWrapper(DeferredHolder<Potion, Potion> delegate) {
        return new ShHsPotionEntry(getOwner(), delegate);
    }

    /**
     * 一般只有变体药水才会调用这个方法设置其对应基础药水名称
     */
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

    public ShHsPotionBuilder<P> recipe(Holder<Potion> input, Item reagent) {
        return recipe(input, () -> reagent);
    }

    public ShHsPotionBuilder<P> recipe(Holder<Potion> input, NonNullSupplier<? extends Item> reagent) {
        brewingRecipes.add((builder, result) -> builder.addMix(input, reagent.get(), result));
        return this;
    }

    /**
     * 一般只有基础药水才会设置语言键，变体药水通过设置baseName与基础药水共用语言键。
     */
    public ShHsPotionBuilder<P> lang(String name) {
        String keyName = baseName == null ? getName() : baseName;
        getOwner().addRawLang("item.minecraft.potion.effect." + keyName, "Potion of " + name);
        getOwner().addRawLang("item.minecraft.splash_potion.effect." + keyName, "Splash Potion of " + name);
        getOwner().addRawLang("item.minecraft.lingering_potion.effect." + keyName, "Lingering Potion of " + name);
        getOwner().addRawLang("item.minecraft.tipped_arrow.effect." + keyName, "Arrow of " + name);
        return this;
    }

    public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        BREWING_RECIPES.forEach(recipe -> recipe.accept(event.getBuilder()));
    }

    @FunctionalInterface
    private interface BrewingRecipeFactory {
        void register(PotionBrewing.Builder builder, ShHsPotionEntry result);
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
