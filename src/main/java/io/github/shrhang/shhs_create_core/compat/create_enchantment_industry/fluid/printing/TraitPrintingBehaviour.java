package io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.fluid.printing;

import com.mojang.serialization.DataResult;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.xkmc.l2hostility.content.item.traits.TraitSymbol;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import io.github.shrhang.shhs_create_core.content.registries.ShHsFluids;
import io.github.shrhang.shhs_create_core.content.registries.ShHsItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrinterBlockEntity;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviour;
import plus.dragons.createenchantmentindustry.config.CEIConfig;
import plus.dragons.createenchantmentindustry.util.CEILang;

import java.util.List;
import java.util.Optional;

public record TraitPrintingBehaviour(SmartFluidTankBehaviour tank, ItemStack template, int cost)
        implements PrintingBehaviour {
    private static final int MILLIBUCKETS_PER_COST = 10;

    public static Optional<DataResult<PrintingBehaviour>> create(Level level, SmartFluidTankBehaviour tank, ItemStack stack) {
        if (!(stack.getItem() instanceof TraitSymbol symbol)) return Optional.empty();

        MobTrait trait = symbol.get();
        return Optional.of(DataResult.success(new TraitPrintingBehaviour(
                tank, stack.copyWithCount(1), getCost(level.registryAccess(), trait))));
    }

    public static int getCost(RegistryAccess registryAccess, MobTrait trait) {
        return trait.getConfig(registryAccess).cost() * MILLIBUCKETS_PER_COST;
    }

    @Override
    public int getRequiredItemCount(Level level, ItemStack stack) {
        return stack.is(ShHsItems.EMPTY_TRAIT.get()) ? 1 : 0;
    }

    @Override
    public int getRequiredFluidAmount(Level level, ItemStack stack, FluidStack fluidStack) {
        return ShHsFluids.HOSTILITY.is(fluidStack.getFluid()) ? cost : 0;
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
        if (amount > 0) {
            CEILang.translate("gui.goggles.printing.cost",
                            CEILang.number(amount)
                                    .add(CreateLang.translate("generic.unit.millibuckets"))
                                    .style(amount <= CEIConfig.fluids().printerFluidCapacity.get()
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        } else if (!tank.getPrimaryHandler().getFluid().isEmpty()) {
            CEILang.translate("gui.goggles.printing.incorrect_liquid").style(ChatFormatting.RED).forGoggles(tooltip);
        }
        return true;
    }
}
