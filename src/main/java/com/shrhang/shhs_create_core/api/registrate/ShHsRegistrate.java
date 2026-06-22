package com.shrhang.shhs_create_core.api.registrate;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.simibubi.create.api.registrate.CreateRegistrateRegistrationCallback;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.FluidBuilder;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.xkmc.l2hostility.content.config.TraitConfig;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.registrate.LHTraits;
import dev.xkmc.l2serial.util.ModContainerHack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public class ShHsRegistrate extends CreateRegistrate {
    private ResourceKey<CreativeModeTab> defaultTab;

    protected ShHsRegistrate(String modid) {
        super(modid);
        var mod = ModContainerHack.getMod(modid);
        var bus = mod.getEventBus();
        if (bus != null) registerEventListeners(bus);
    }
    public static ShHsRegistrate create(String modid) {
        ShHsRegistrate registrate = new ShHsRegistrate(modid);
        CreateRegistrateRegistrationCallback.provideRegistrate(registrate);
        return registrate;
    }

    public MutableComponent langOfCreativeTab(String keyName, String value) {
        return this.addRawLang("itemGroup." + ShHsCreateCore.MODID + "." + keyName, value);
    }

    @Override
    public ShHsRegistrate defaultCreativeTab(ResourceKey<CreativeModeTab> creativeModeTab) {
        defaultTab = creativeModeTab;
        super.defaultCreativeTab(creativeModeTab);
        return this;
    }

    @Override
    public <T extends Item> ShHsItemBuilder<T, CreateRegistrate> item(NonNullFunction<Item.Properties, T> factory) {
        return item(this, factory);
    }

    @Override
    public <T extends Item> ShHsItemBuilder<T, CreateRegistrate> item(String name, NonNullFunction<Item.Properties, T> factory) {
        return item(this, name, factory);
    }

    @Override
    public <T extends Item, P> ShHsItemBuilder<T, P> item(P parent, NonNullFunction<Item.Properties, T> factory) {
        return item(parent, currentName(), factory);
    }

    @Override
    public <T extends Item, P> ShHsItemBuilder<T, P> item(P parent, String name, NonNullFunction<Item.Properties, T> factory) {
        ItemBuilder<T, P> builder = entry(name, callback -> {
            ShHsItemBuilder<T, P> itemBuilder = ShHsItemBuilder.create(this, parent, name, callback, factory);
            return defaultTab == null ? itemBuilder : itemBuilder.tab(defaultTab);
        });
        return (ShHsItemBuilder<T, P>) builder;
    }

    @Override
    public <T extends Block> ShHsBlockBuilder<T, CreateRegistrate> block(NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return block(this, factory);
    }

    @Override
    public <T extends Block> ShHsBlockBuilder<T, CreateRegistrate> block(String name,
                                                                         NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return block(this, name, factory);
    }

    @Override
    public <T extends Block, P> ShHsBlockBuilder<T, P> block(P parent,
                                                             NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return block(parent, currentName(), factory);
    }

    @Override
    public <T extends Block, P> ShHsBlockBuilder<T, P> block(P parent, String name,
                                                             NonNullFunction<BlockBehaviour.Properties, T> factory) {
        BlockBuilder<T, P> builder = entry(name, callback -> ShHsBlockBuilder.create(this, parent, name, callback, factory));
        return (ShHsBlockBuilder<T, P>) builder;
    }

    @Override
    public FluidBuilder<VirtualFluid, CreateRegistrate> virtualFluid(String name) {
        ShHsAtlases.addVirtualFluid(name);
        return super.virtualFluid(name);
    }

    @Override
    public <T extends BaseFlowingFluid> FluidBuilder<T, CreateRegistrate> virtualFluid(String name,
                                                                                       FluidBuilder.FluidTypeFactory typeFactory,
                                                                                       NonNullFunction<BaseFlowingFluid.Properties, T> sourceFactory,
                                                                                       NonNullFunction<BaseFlowingFluid.Properties, T> flowingFactory) {
        ShHsAtlases.addVirtualFluid(name);
        return super.virtualFluid(name, typeFactory, sourceFactory, flowingFactory);
    }

    public <T extends MobEffect> ShHsMobEffectBuilder<T, ShHsRegistrate> effect(String name, NonNullSupplier<T> sup) {
        return entry(name, cb -> new ShHsMobEffectBuilder<>(this, this, name, cb, sup));
    }

    public ShHsPotionBuilder<ShHsRegistrate> potion(String name) {
        return entry(name, cb -> new ShHsPotionBuilder<>(this, this, name, cb));
    }

    /**
     * 复刻了l2hostility的trait注册方法，注册时会自动生成对应的tag和物品。
     */
    public <T extends MobTrait> ShHsTraitBuilder<T> trait(String name, NonNullSupplier<T> sup,TraitConfig config) {
        return entry(name, cb -> new ShHsTraitBuilder<>(this, this, name, cb, sup))
                .dataMap(LHTraits.DATA.reg(), config)
                .item().build();
    }
}
