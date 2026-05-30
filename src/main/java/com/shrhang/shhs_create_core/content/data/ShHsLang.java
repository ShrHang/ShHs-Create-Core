package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.content.util.SpellToleranceHelper;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsLang {
    public static void init() {
        REGISTRATE.addRawLang(SpellToleranceHelper.lang, "At least %s Spell Tolerance is required to cast this spell.");
    }
}
