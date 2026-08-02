package io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.integration.jei.category.printing;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import plus.dragons.createenchantmentindustry.integration.jei.category.printing.PrintingRecipeJEI;

import java.util.List;

import static io.github.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.github.shrhang.shhs_create_core.ShHsCreateCore.rl;
import static io.github.shrhang.shhs_create_core.content.fluid.InkType.getFluid;
import static io.github.shrhang.shhs_create_core.content.util.SpellToleranceHelper.getRelativeLevel;
import static io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL;

public class ScollPrintingRecipeJEI implements PrintingRecipeJEI {
    public static final PrintingRecipeJEI.Type TYPE = PrintingRecipeJEI
            .register(rl("scroll"), ScollPrintingRecipeJEI::createCodec);

    private final ResourceLocation id;
    private final AbstractSpell spell;

    public ScollPrintingRecipeJEI(AbstractSpell spell) {
        this.id = PrintingRecipeJEI.super.getRegistryName().withSuffix("/" +
                spell.getSpellResource().getNamespace() + "/" +
                spell.getSpellResource().getPath());
        this.spell = spell;
    }

    public static MapCodec<ScollPrintingRecipeJEI> createCodec(ICodecHelper codecHelper, IRecipeManager recipeManager) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("spell")
                                .forGetter((ScollPrintingRecipeJEI recipe) -> recipe.spell.getSpellResource()))
                .apply(instance, spell -> new ScollPrintingRecipeJEI(SpellRegistry.getSpell(spell))));
    }

    public static List<PrintingRecipeJEI> listAll() {
        return SpellRegistry.REGISTRY
                .stream()
                .filter(spell -> spell != SpellRegistry.none())
                .filter(AbstractSpell::isEnabled)
                .map(ScollPrintingRecipeJEI::new)
                .map(PrintingRecipeJEI.class::cast)
                .toList();
    }

    private ItemStack createScroll(int level) {
        ItemStack stack = new ItemStack(SCROLL.get());
        ISpellContainer.createScrollContainer(spell, level, stack);
        return stack;
    }

    @Nullable
    private net.minecraft.world.level.material.Fluid getInk(int level) {
        var fluid = getFluid(spell.getRarity(level));
        return fluid == null ? null : fluid.get();
    }

    private int getCost(int level) {
        return SERVER.scrollPrintingCost.get() * getRelativeLevel(level, spell);
    }

    private void addFluidStack(IRecipeSlotBuilder slot, int level) {
        var ink = getInk(level);
        if (ink != null) {
            slot.addFluidStack(ink, getCost(level));
        }
    }

    private void addAllLevels(IRecipeSlotBuilder slot) {
        for (int level = spell.getMinLevel(); level <= spell.getMaxLevel(); level++) {
            slot.addItemStack(createScroll(level));
        }
    }

    private int getDisplayedLevel(IRecipeSlotDrawable slot) {
        ItemStack displayed = slot.getDisplayedItemStack().orElse(ItemStack.EMPTY);
        var container = ISpellContainer.get(displayed);
        if (container == null || container.isEmpty()) {
            return spell.getMinLevel();
        }
        return container.getActiveSpells().getFirst().spellData().getLevel();
    }

    private FluidStack createFluidStack(int level) {
        var ink = getInk(level);
        return ink == null ? FluidStack.EMPTY : new FluidStack(ink, getCost(level));
    }

    @Override
    public void setBase(IRecipeSlotBuilder slot) {
        slot.addItemLike(Items.PAPER);
    }

    @Override
    public void setTemplate(IRecipeSlotBuilder slot) {
        addAllLevels(slot);
    }

    @Override
    public void setFluid(IRecipeSlotBuilder slot) {
        for (int level = spell.getMinLevel(); level <= spell.getMaxLevel(); level++) {
            addFluidStack(slot, level);
        }
    }

    @Override
    public void setOutput(IRecipeSlotBuilder slot) {
        addAllLevels(slot);
    }

    @Override
    public Type getType() {
        return TYPE;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return id;
    }

    @Override
    public void onDisplayedIngredientsUpdate(IRecipeSlotDrawable baseSlot, IRecipeSlotDrawable templateSlot, IRecipeSlotDrawable fluidSlot, IRecipeSlotDrawable outputSlot, IFocusGroup focuses) {
        boolean hasOutputFocus = focuses.getFocuses(RecipeIngredientRole.OUTPUT).findAny().isPresent();
        int level = getDisplayedLevel(hasOutputFocus ? outputSlot : templateSlot);
        var stack = createScroll(level);
        var fluid = createFluidStack(level);
        if (hasOutputFocus) {
            templateSlot.createDisplayOverrides().addItemStack(stack);
        } else {
            outputSlot.createDisplayOverrides().addItemStack(stack);
        }
        if (!fluid.isEmpty()) {
            fluidSlot.createDisplayOverrides().addIngredient(NeoForgeTypes.FLUID_STACK, fluid);
        }
    }
}
