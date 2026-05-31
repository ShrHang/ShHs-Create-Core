package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.content.util.SpellToleranceHelper;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import static com.shrhang.shhs_create_core.ShHsCreateCore.MODID;
import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsLang {

    public static String text(String string) {
        return "text." + MODID + "." + string;
    }

    public static void init() {
        REGISTRATE.addRawLang(SpellToleranceHelper.lang, "At least %s Spell Tolerance is required to cast this spell.");
    }
}
