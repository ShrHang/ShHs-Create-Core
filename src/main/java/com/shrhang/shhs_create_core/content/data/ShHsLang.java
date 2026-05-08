package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.util.SpellToleranceHelper;
import net.minecraft.network.chat.MutableComponent;

public class ShHsLang {
    private static final ShHsRegistrate REGISTRATE = ShHsCreateCore.createRegistrate;
    public static final MutableComponent ToleranceNeeded;

    static {
        ToleranceNeeded = REGISTRATE.addRawLang(SpellToleranceHelper.lang, "At least %s Spell Tolerance is required to cast this spell.");
    }

    public static void init() {}
}
