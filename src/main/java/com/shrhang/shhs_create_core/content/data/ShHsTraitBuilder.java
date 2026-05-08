package com.shrhang.shhs_create_core.content.data;

import com.tterrag.registrate.builders.AbstractBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.xkmc.l2core.init.reg.registrate.NamedEntry;
import dev.xkmc.l2hostility.content.item.traits.TraitSymbol;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.data.LHTagGen;
import dev.xkmc.l2hostility.init.registrate.LHTraits;
import dev.xkmc.l2serial.util.Wrappers;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class ShHsTraitBuilder<T extends MobTrait>
        extends AbstractBuilder<MobTrait, T, ShHsRegistrate, ShHsTraitBuilder<T>> {

    private final NonNullSupplier<T> sup;

    public ShHsTraitBuilder(ShHsRegistrate owner, ShHsRegistrate parent, String name, BuilderCallback callback, NonNullSupplier<T> sup) {
        super(owner, parent, name, callback, LHTraits.TRAITS.key());
        this.sup = sup;
        addBlacklist(Consumers.nop());
        addWhitelist(Consumers.nop());
    }

    public ItemBuilder<TraitSymbol, ShHsTraitBuilder<T>> item() {
        return item(TraitSymbol::new);
    }

    public ShHsTraitBuilder<T> addWhitelist(Consumer<IntrinsicHolderTagsProvider.IntrinsicTagAppender<EntityType<?>>> pvd) {
        var id = ResourceLocation.fromNamespaceAndPath(getOwner().getModid(), getName());
        var tag = TagKey.create(Registries.ENTITY_TYPE, id.withSuffix("_whitelist"));
        LHTagGen.ENTITY_TAG_BUILDER.put(tag.location(), e -> pvd.accept(e.addTag(tag)));
        return this;
    }

    public ShHsTraitBuilder<T> addBlacklist(Consumer<IntrinsicHolderTagsProvider.IntrinsicTagAppender<EntityType<?>>> pvd) {
        var id = ResourceLocation.fromNamespaceAndPath(getOwner().getModid(), getName());
        var tag = TagKey.create(Registries.ENTITY_TYPE, id.withSuffix("_blacklist"));
        LHTagGen.ENTITY_TAG_BUILDER.put(tag.location(), e -> pvd.accept(e.addTag(tag)));
        return this;
    }

    @Override
    protected @NotNull ShHsTraitEntry<T> createEntryWrapper(@NotNull DeferredHolder<MobTrait, T> delegate) {
        return new ShHsTraitEntry<>(Wrappers.cast(this.getOwner()), delegate);
    }

    @Override
    public @NotNull ShHsTraitEntry<T> register() {
        return Wrappers.cast(super.register());
    }

    public ShHsTraitBuilder<T> lang(String name) {
        return this.lang(NamedEntry::getDescriptionId, name);
    }

    @Override
    public <D> @NotNull ShHsTraitBuilder<T> dataMap(@NotNull DataMapType<MobTrait, D> type, @NotNull D val) {
        super.dataMap(type, val);
        return this;
    }

    public <I extends TraitSymbol> ItemBuilder<I, ShHsTraitBuilder<T>> item(NonNullFunction<Item.Properties, I> sup) {
        return getOwner().item(this, getName(), sup)
                .model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bg"),
                        pvd.modLoc("item/trait/" + ctx.getName())))
                .setData(ProviderType.LANG, NonNullBiConsumer.noop())
                .tag(LHTagGen.TRAIT_ITEM);
    }

    @Override
    protected @NotNull T createEntry() {
        return this.sup.get();
    }

    public ShHsTraitBuilder<T> desc(String s) {
        getOwner().addRawLang("trait." + getOwner().getModid() + "." + getName() + ".desc", s);
        return this;
    }
}
