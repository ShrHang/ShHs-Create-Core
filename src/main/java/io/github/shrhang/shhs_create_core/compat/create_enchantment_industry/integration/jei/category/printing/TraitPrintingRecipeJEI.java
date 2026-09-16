package io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.integration.jei.category.printing;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.l2hostility.content.item.traits.TraitSymbol;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.registrate.LHTraits;
import io.github.shrhang.shhs_create_core.content.registries.ShHsFluids;
import io.github.shrhang.shhs_create_core.content.registries.ShHsItems;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.recipe.IRecipeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import plus.dragons.createenchantmentindustry.integration.jei.category.printing.PrintingRecipeJEI;

import java.util.List;
import java.util.Objects;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.rl;
import static io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.fluid.printing.TraitPrintingBehaviour.getCost;

public class TraitPrintingRecipeJEI implements PrintingRecipeJEI {
    public static final PrintingRecipeJEI.Type TYPE = PrintingRecipeJEI
            .register(rl("trait"), TraitPrintingRecipeJEI::createCodec);

    private final ResourceLocation id;
    private final MobTrait trait;

    public TraitPrintingRecipeJEI(MobTrait trait) {
        ResourceLocation traitId = trait.getRegistryName();
        this.id = PrintingRecipeJEI.super.getRegistryName().withSuffix("/" + traitId.getNamespace() + "/" + traitId.getPath());
        this.trait = trait;
    }

    public static MapCodec<TraitPrintingRecipeJEI> createCodec(ICodecHelper codecHelper, IRecipeManager recipeManager) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("trait")
                                .forGetter((TraitPrintingRecipeJEI recipe) -> recipe.trait.getRegistryName()))
                .apply(instance, id -> new TraitPrintingRecipeJEI(LHTraits.TRAITS.get().get(id))));
    }

    public static List<PrintingRecipeJEI> listAll() {
        return LHTraits.TRAITS.get().stream()
                .filter(trait -> trait.asItem() instanceof TraitSymbol)
                .map(TraitPrintingRecipeJEI::new)
                .map(PrintingRecipeJEI.class::cast)
                .toList();
    }

    private ItemStack getTraitStack() {
        return new ItemStack(trait.asItem());
    }

    @Override
    public void setBase(IRecipeSlotBuilder slot) {
        slot.addItemLike(ShHsItems.EMPTY_TRAIT.get());
    }

    @Override
    public void setTemplate(IRecipeSlotBuilder slot) {
        slot.addItemStack(getTraitStack());
    }

    @Override
    public void setFluid(IRecipeSlotBuilder slot) {
        var level = Objects.requireNonNull(Minecraft.getInstance().level, "minecraft.level");
        slot.addFluidStack(ShHsFluids.HOSTILITY.getSource(), getCost(level.registryAccess(), trait));
    }

    @Override
    public void setOutput(IRecipeSlotBuilder slot) {
        slot.addItemStack(getTraitStack());
    }

    @Override
    public Type getType() {
        return TYPE;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return id;
    }
}
