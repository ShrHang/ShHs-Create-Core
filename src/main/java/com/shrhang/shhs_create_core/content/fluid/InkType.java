package com.shrhang.shhs_create_core.content.fluid;

import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.FluidRegistry;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Supplier;

public enum InkType {
    COMMON(SpellRarity.COMMON, FluidRegistry.COMMON_INK),
    UNCOMMON(SpellRarity.UNCOMMON, FluidRegistry.UNCOMMON_INK),
    RARE(SpellRarity.RARE, FluidRegistry.RARE_INK),
    EPIC(SpellRarity.EPIC, FluidRegistry.EPIC_INK),
    LEGENDARY(SpellRarity.LEGENDARY, FluidRegistry.LEGENDARY_INK);

    public final SpellRarity rarity;
    public final Supplier<? extends Fluid> fluid;

    private static final InkType[] VALUES = values();

    InkType(SpellRarity rarity, Supplier<? extends Fluid> fluid) {
        this.rarity = rarity;
        this.fluid = fluid;
    }

    public static Supplier<? extends Fluid> getFluid(SpellRarity rarity) {
        for (InkType type : VALUES) {
            if (type.rarity == rarity) return type.fluid;
        }
        return null;
    }

    public static SpellRarity getRarity(FluidStack stack) {
        if (stack.isEmpty()) return null;
        Fluid target = stack.getFluid();
        for (InkType type : VALUES) {
            if (type.fluid.get() == target)
                return type.rarity;
        }
        return null;
    }
}
