package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.content.util.SpellToleranceHelper;

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
