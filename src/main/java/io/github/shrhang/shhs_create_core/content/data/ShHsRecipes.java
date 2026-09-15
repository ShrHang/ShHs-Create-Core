package io.github.shrhang.shhs_create_core.content.data;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.*;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import com.tterrag.registrate.providers.ProviderType;
import dev.xkmc.l2hostility.init.registrate.LHItems;
import io.github.shrhang.shhs_create_core.content.kinetics.fan.processing.MiracleFanProcessingRecipe;
import io.github.shrhang.shhs_create_core.content.registries.ShHsFluids;
import io.github.shrhang.shhs_create_core.content.registries.ShHsItems;
import io.github.shrhang.shhs_create_core.content.registries.ShHsRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.MODID;
import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

@SuppressWarnings("unused")
public class ShHsRecipes {

    public static void init() {
        REGISTRATE.addDataGenerator(ProviderType.GENERIC_SERVER, provider -> {
            provider.add(data -> new Emptying(data.output(), data.registries()));
            provider.add(data -> new Filling(data.output(), data.registries()));
            provider.add(data -> new Mixing(data.output(), data.registries()));
            provider.add(data -> new Miracle(data.output(), data.registries()));
        });
    }

    public static class Compacting extends CompactingRecipeGen {
        GeneratedRecipe
                EMPTY_TRAIT = create("empty_trait", b -> b
                .require(LHItems.MIRACLE_POWDER)
                .require(Items.BOWL)
                .output(ShHsItems.EMPTY_TRAIT));

        public Compacting(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, MODID);
        }
    }

    public static class Emptying extends EmptyingRecipeGen {
        GeneratedRecipe
                HOSTILITY = create("hostility", b -> b
                .require(LHItems.BOTTLE_CURSE)
                .output(Items.GLASS_BOTTLE)
                .output(ShHsFluids.HOSTILITY.value(), 100));

        public Emptying(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, MODID);
        }
    }

    public static class Filling extends FillingRecipeGen {
        GeneratedRecipe
                BOTTLE_CURSE = create("bottle_curse", b -> b
                        .require(Items.GLASS_BOTTLE)
                        .require(ShHsFluids.HOSTILITY.value(), 100)
                        .output(LHItems.BOTTLE_CURSE.get()));

        public Filling(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, MODID);
        }
    }

    public static class Mixing extends MixingRecipeGen {
        GeneratedRecipe
                LIQUID_FERTILIZER = create("liquid_fertilizer", b -> b
                        .require(AllItems.TREE_FERTILIZER)
                        .require(Fluids.WATER, 1000)
                        .output(ShHsFluids.LIQUID_FERTILIZER.value(), 1000)),

                MIRACLE = create("miracle", b -> b
                        .require(LHItems.MIRACLE_POWDER.get())
                        .require(Fluids.WATER, 250)
                        .output(ShHsFluids.MIRACLE.value(), 250));

        public Mixing(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, MODID);
        }
    }



    public static class Miracle extends StandardProcessingRecipeGen<MiracleFanProcessingRecipe> {
        GeneratedRecipe
                MIRACLE_POWDER_FROM_POWDERED_OBSIDIAN = create("miracle_powder_from_dusts", b -> b
                        .require(Tags.Items.DUSTS)
                        .output(.0823f, LHItems.MIRACLE_POWDER));

        public Miracle(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, MODID);
        }

        protected GeneratedRecipe create(String name,
                                         UnaryOperator<StandardProcessingRecipe.Builder<MiracleFanProcessingRecipe>> transform) {
            return super.create(ResourceLocation.fromNamespaceAndPath(MODID, name), transform);
        }

        @Override
        protected IRecipeTypeInfo getRecipeType() {
            return ShHsRecipeTypes.MIRACLE;
        }
    }
}
