package io.github.shrhang.shhs_create_core.api.registrate;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import com.simibubi.create.api.registrate.CreateRegistrateRegistrationCallback;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.FluidBuilder;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.xkmc.l2hostility.content.config.TraitConfig;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.registrate.LHTraits;
import dev.xkmc.l2serial.util.ModContainerHack;
import io.github.shrhang.shhs_create_core.content.registries.ShHsSkullTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

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
    public @NotNull ShHsRegistrate defaultCreativeTab(@NotNull ResourceKey<CreativeModeTab> creativeModeTab) {
        defaultTab = creativeModeTab;
        super.defaultCreativeTab(creativeModeTab);
        return this;
    }

    @Override
    public <T extends Item> @NotNull ShHsItemBuilder<T, CreateRegistrate> item(@NotNull NonNullFunction<Item.Properties, T> factory) {
        return item(this, factory);
    }

    @Override
    public <T extends Item> @NotNull ShHsItemBuilder<T, CreateRegistrate> item(@NotNull String name, @NotNull NonNullFunction<Item.Properties, T> factory) {
        return item(this, name, factory);
    }

    @Override
    public <T extends Item, P> @NotNull ShHsItemBuilder<T, P> item(@NotNull P parent, @NotNull NonNullFunction<Item.Properties, T> factory) {
        return item(parent, currentName(), factory);
    }

    @Override
    public <T extends Item, P> @NotNull ShHsItemBuilder<T, P> item(@NotNull P parent, @NotNull String name, @NotNull NonNullFunction<Item.Properties, T> factory) {
        ItemBuilder<T, P> builder = entry(name, callback -> {
            ShHsItemBuilder<T, P> itemBuilder = ShHsItemBuilder.create(this, parent, name, callback, factory);
            return defaultTab == null ? itemBuilder : itemBuilder.tab(defaultTab);
        });
        return (ShHsItemBuilder<T, P>) builder;
    }

    @Override
    public <T extends Block> @NotNull ShHsBlockBuilder<T, CreateRegistrate> block(@NotNull NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return block(this, factory);
    }

    @Override
    public <T extends Block> @NotNull ShHsBlockBuilder<T, CreateRegistrate> block(@NotNull String name,
                                                                                  @NotNull NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return block(this, name, factory);
    }

    @Override
    public <T extends Block, P> @NotNull ShHsBlockBuilder<T, P> block(@NotNull P parent,
                                                                      @NotNull NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return block(parent, currentName(), factory);
    }

    @Override
    public <T extends Block, P> @NotNull ShHsBlockBuilder<T, P> block(@NotNull P parent, @NotNull String name,
                                                                      @NotNull NonNullFunction<BlockBehaviour.Properties, T> factory) {
        BlockBuilder<T, P> builder = entry(name, callback -> ShHsBlockBuilder.create(this, parent, name, callback, factory));
        return (ShHsBlockBuilder<T, P>) builder;
    }

    @Override
    public FluidBuilder<VirtualFluid, CreateRegistrate> virtualFluid(String name) {
        ShHsAtlases.addFluid(name);
        return super.virtualFluid(name);
    }

    @Override
    public <T extends BaseFlowingFluid> FluidBuilder<T, CreateRegistrate> virtualFluid(String name,
                                                                                       FluidBuilder.FluidTypeFactory typeFactory,
                                                                                       NonNullFunction<BaseFlowingFluid.Properties, T> sourceFactory,
                                                                                       NonNullFunction<BaseFlowingFluid.Properties, T> flowingFactory) {
        ShHsAtlases.addFluid(name);
        return super.virtualFluid(name, typeFactory, sourceFactory, flowingFactory);
    }

    @Override
    public FluidBuilder<BaseFlowingFluid.Flowing, CreateRegistrate> standardFluid(String name) {
        ShHsAtlases.addFluid(name);
        return super.standardFluid(name);
    }

    @Override
    public FluidBuilder<BaseFlowingFluid.Flowing, CreateRegistrate> standardFluid(String name,
                                                                                  FluidBuilder.FluidTypeFactory typeFactory) {
        ShHsAtlases.addFluid(name);
        return super.standardFluid(name, typeFactory);
    }

    public <T extends MobEffect> ShHsMobEffectBuilder<T, ShHsRegistrate> effect(String name, NonNullSupplier<T> sup) {
        return entry(name, cb -> new ShHsMobEffectBuilder<>(this, this, name, cb, sup));
    }

    public ShHsPotionBuilder<ShHsRegistrate> potion(String name) {
        return entry(name, cb -> new ShHsPotionBuilder<>(this, this, name, cb));
    }

    public ShHsSkullTypes.ShHsSkullEntry skull(String name, SkullBlock.Type type) {
        AtomicReference<BlockEntry<ShHsSkullTypes.ShHsSkullBlock>> headRef = new AtomicReference<>();

        var wallHead = block(name + "_wall_head", properties -> new ShHsSkullTypes.ShHsWallSkullBlock(type, properties))
                .initialProperties(() -> Blocks.ZOMBIE_WALL_HEAD)
                .noLang()
                .blockTags(Tags.Blocks.SKULLS)
                .blockstate((ctx, prov) -> prov.simpleBlock(
                        ctx.getEntry(),
                        prov.models().getExistingFile(ResourceLocation.withDefaultNamespace("block/skull"))
                ))
                .loot((prov, block) -> prov.dropOther(block, headRef.get().get()))
                .register();

        var head = block(name + "_head", properties -> new ShHsSkullTypes.ShHsSkullBlock(type, properties))
                .initialProperties(() -> Blocks.ZOMBIE_HEAD)
                .blockTags(Tags.Blocks.SKULLS)
                .blockstate((ctx, prov) -> prov.simpleBlock(
                        ctx.getEntry(),
                        prov.models().getExistingFile(ResourceLocation.withDefaultNamespace("block/skull"))
                ))
                .item(
                        (block, properties) -> new StandingAndWallBlockItem(block, wallHead.get(), properties, Direction.DOWN),
                        item -> item.model((ctx, prov) -> prov.withExistingParent(
                                ctx.getName(),
                                ResourceLocation.withDefaultNamespace("item/template_skull")
                        )).properties(p -> p.rarity(Rarity.UNCOMMON)).noLang().tag(ItemTags.SKULLS)
                )
                .register();

        headRef.set(head);
        return new ShHsSkullTypes.ShHsSkullEntry(head, wallHead);
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
