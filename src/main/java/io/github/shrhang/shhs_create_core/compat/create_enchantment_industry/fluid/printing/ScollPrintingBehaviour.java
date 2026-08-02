package io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.fluid.printing;

import com.mojang.serialization.DataResult;
import io.github.shrhang.shhs_create_core.content.fluid.InkType;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrinterBlockEntity;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviour;
import plus.dragons.createenchantmentindustry.config.CEIConfig;
import plus.dragons.createenchantmentindustry.util.CEILang;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static io.github.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.github.shrhang.shhs_create_core.content.util.SpellToleranceHelper.getRelativeLevel;
import static io.redspace.ironsspellbooks.registries.ComponentRegistry.SPELL_CONTAINER;

public record ScollPrintingBehaviour(SmartFluidTankBehaviour tank, ItemStack template, SpellRarity rarity, int cost) implements PrintingBehaviour {

    public static Optional<DataResult<PrintingBehaviour>> create(Level level, SmartFluidTankBehaviour tank, ItemStack stack) {
        if (!stack.is(ItemRegistry.SCROLL))
            return Optional.empty();

        var spellContainer = stack.get(SPELL_CONTAINER);
        if (spellContainer == null || spellContainer.isEmpty())
            return Optional.of(DataResult.error(() -> CEILang.translate("gui.printer.copy.invalid").string()));

        int perCost = SERVER.scrollPrintingCost.get();

        SpellSlot spellSlot = spellContainer.getActiveSpells().getFirst();
        SpellRarity rarity = spellSlot.spellData().getRarity();
        int spellLevel = spellSlot.spellData().getLevel();
        int relativeLevel = getRelativeLevel(spellLevel, spellSlot.getSpell());
        return Optional.of(DataResult.success(new ScollPrintingBehaviour(tank, stack, rarity, perCost * relativeLevel)));
    }

    @Override
    public int getRequiredItemCount(Level level, ItemStack stack) {
        if (stack.is(Items.PAPER)) return 1;
        return 0;
    }

    @Override
    public int getRequiredFluidAmount(Level level, ItemStack stack, FluidStack fluidStack) {
        Supplier<? extends Fluid> inkFluid = InkType.getFluid(this.rarity);
        if (inkFluid != null && fluidStack.is(inkFluid.get())) return cost;
        return 0;
    }

    @Override
    public ItemStack getResult(Level level, ItemStack stack, FluidStack fluidStack) {
        return template.copy();
    }

    @Override
    public void onFinished(Level level, BlockPos pos, PrinterBlockEntity printer) {
        level.levelEvent(1043, pos.below(), 0);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CEILang.translate("gui.goggles.printing.copy").forGoggles(tooltip);
        CEILang.item(template).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        int amount = getRequiredFluidAmount(null, ItemStack.EMPTY, tank.getPrimaryHandler().getFluid());
        if (amount > 0)
            CEILang.translate("gui.goggles.printing.cost",
                            CEILang.number(amount)
                                    .add(CreateLang.translate("generic.unit.millibuckets"))
                                    .style(amount <= CEIConfig.fluids().printerFluidCapacity.get()
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        else if (!tank.getPrimaryHandler().getFluid().isEmpty()) {
            CEILang.translate("gui.goggles.printing.incorrect_liquid").style(ChatFormatting.RED).forGoggles(tooltip);
        }
        return true;
    }
}
