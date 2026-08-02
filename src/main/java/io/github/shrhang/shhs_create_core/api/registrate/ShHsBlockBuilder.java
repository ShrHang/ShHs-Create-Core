package io.github.shrhang.shhs_create_core.api.registrate;

import com.simibubi.create.api.stress.BlockStressValues;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.nullness.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ShHsBlockBuilder<T extends Block, P> extends BlockBuilder<T, P> {

    public static <T extends Block, P> ShHsBlockBuilder<T, P> create(AbstractRegistrate<?> owner, P parent,
                                                                     String name, BuilderCallback callback,
                                                                     NonNullFunction<BlockBehaviour.Properties, T> factory) {
        ShHsBlockBuilder<T, P> builder = new ShHsBlockBuilder<>(owner, parent, name, callback, factory,
                BlockBehaviour.Properties::of);
        return builder.defaultBlockstate().defaultLoot().defaultLang();
    }

    protected ShHsBlockBuilder(AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback,
                               NonNullFunction<BlockBehaviour.Properties, T> factory,
                               NonNullSupplier<BlockBehaviour.Properties> initialProperties) {
        super(owner, parent, name, callback, factory, initialProperties);
    }

    @Override
    public ShHsBlockBuilder<T, P> simpleItem() {
        item().build();
        return this;
    }

    public ShHsBlockBuilder<T, P> item(NonNullConsumer<ShHsItemBuilder<BlockItem, BlockBuilder<T, P>>> config) {
        ShHsItemBuilder<BlockItem, BlockBuilder<T, P>> builder = item();
        config.accept(builder);
        builder.build();
        return this;
    }

    public <I extends Item> ShHsBlockBuilder<T, P> item(
            NonNullBiFunction<? super T, Item.Properties, ? extends I> factory,
            NonNullConsumer<ShHsItemBuilder<I, BlockBuilder<T, P>>> config) {
        ShHsItemBuilder<I, BlockBuilder<T, P>> builder = item(factory);
        config.accept(builder);
        builder.build();
        return this;
    }

    public ShHsBlockBuilder<T, P> stressImpact(double impact) {
        onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> impact));
        return this;
    }

    @Override
    public ShHsItemBuilder<BlockItem, BlockBuilder<T, P>> item() {
        return (ShHsItemBuilder<BlockItem, BlockBuilder<T, P>>) super.item();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Item> ShHsItemBuilder<I, BlockBuilder<T, P>> item(
            NonNullBiFunction<? super T, Item.Properties, ? extends I> factory) {
        return (ShHsItemBuilder<I, BlockBuilder<T, P>>) super.item(factory);
    }

    @Override
    public <BE extends BlockEntity> ShHsBlockBuilder<T, P> simpleBlockEntity(BlockEntityFactory<BE> factory) {
        blockEntity(factory).build();
        return this;
    }
    @Override
    public ShHsBlockBuilder<T, P> properties(NonNullUnaryOperator<BlockBehaviour.Properties> func) {
        super.properties(func);
        return this;
    }

    @Override
    public ShHsBlockBuilder<T, P> initialProperties(NonNullSupplier<? extends Block> block) {
        super.initialProperties(block);
        return this;
    }

    @Override
    public ShHsBlockBuilder<T, P> defaultBlockstate() {
        super.defaultBlockstate();
        return this;
    }

    @Override
    public ShHsBlockBuilder<T, P> blockstate(
            NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockstateProvider> cons) {
        super.blockstate(cons);
        return this;
    }

    @Override
    public ShHsBlockBuilder<T, P> defaultLang() {
        super.defaultLang();
        return this;
    }

    @Override
    public ShHsBlockBuilder<T, P> lang(String name) {
        super.lang(name);
        return this;
    }

    @Override
    public ShHsBlockBuilder<T, P> defaultLoot() {
        super.defaultLoot();
        return this;
    }

    @SafeVarargs
    public final ShHsBlockBuilder<T, P> blockTags(TagKey<Block>... tags) {
        super.tag(tags);
        return this;
    }
}
