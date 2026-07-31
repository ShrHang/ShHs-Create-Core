package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.content.registries.ShHsKeys;
import com.shrhang.shhs_create_core.infrastructure.ponder.ShHsPonderPlugin;
import com.tterrag.registrate.providers.ProviderType;
import joptsimple.internal.Strings;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.DefaultSharedTextRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.function.BiConsumer;

import static com.shrhang.shhs_create_core.ShHsCreateCore.MODID;
import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;
import static net.createmod.catnip.lang.LangBuilder.DEFAULT_SPACE_WIDTH;

public class ShHsLang {

    public static final String CATEGORY_KEY = "key.categories." + MODID;
    public static String keyKey(String key) {
        return "key." + MODID + "." + key;
    }

    public static String textKey(String key) {
        return "text." + MODID + "." + key;
    }

    public static MutableComponent textComponent(String key, Object... args) {
        return Component.translatable(textKey(key), args);
    }

    public static String titleKey(String key) {
        return "title." + MODID + "." + key;
    }

    public static MutableComponent titleComponent(String key, Object... args) {
        return Component.translatable(titleKey(key), args);
    }

    public static String tooltipKey(String key) {
        return "tooltip." + MODID + "." + key;
    }

    public static MutableComponent tooltipComponent(String key, Object... args) {
        return Component.translatable(tooltipKey(key), args);
    }

    public static MutableComponent tooltipComponentForGoggles(String key,  Object... args) {
        return Component.translatable(Strings.repeat(' ', getIndents(Minecraft.getInstance().font)) + "%s", tooltipComponent(key, args));
    }

    private static int getIndents(Font font) {
        int spaceWidth = font.width(" ");
        if (DEFAULT_SPACE_WIDTH == spaceWidth) {
            return 4;
        }
        return Mth.ceil(DEFAULT_SPACE_WIDTH * 4 / spaceWidth);
    }

    public static void init() {
        REGISTRATE.addRawLang(textKey("no_enough_spell_tolerance"), "At least %s Spell Tolerance is required to cast this spell.");
        REGISTRATE.addRawLang(textKey("empty_trait_no_target"), "No valid target in sight.");
        REGISTRATE.addRawLang(textKey("empty_trait_no_traits"), "No traits can be extracted from this target.");

        REGISTRATE.addRawLang(titleKey("container.endchest"), "%s's %s");
        REGISTRATE.addRawLang(tooltipKey("brass_ender_chest.header"), "Ender Chest Info");
        REGISTRATE.addRawLang(tooltipKey("brass_ender_chest.owner"), "Owner: %s");
        REGISTRATE.addRawLang(tooltipKey("brass_ender_chest.owner_unknown"), "Cannot find owner %s");
        REGISTRATE.addRawLang(tooltipKey("brass_ender_chest.locked"), "Locked: Only the owner can open.");
        REGISTRATE.addRawLang(tooltipKey("brass_ender_chest.unlocked"), "Unlocked: Anyone can open.");

        REGISTRATE.addRawLang(textKey("portable_stock_ticker.tooltip.linked"), "Linked.");
        REGISTRATE.addRawLang(textKey("portable_stock_ticker.no_data"), "Not Linked to a Logistics Network");
        REGISTRATE.addRawLang(textKey("portable_stock_ticker.no_network"), "Linked Logistics Network no exists.");
        REGISTRATE.addRawLang(textKey("portable_stock_ticker.unloaded"), "Linked Logistics Network is unloaded.");

        REGISTRATE.addRawLang(CATEGORY_KEY, "ShH's Create Core");
        REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> {
            ShHsKeys.provideLang(provider::add);
            providePonderLang(provider::add);
        });
    }

    private static void providePonderLang(BiConsumer<String, String> consumer) {
        ShHsPonderPlugin plugin = new ShHsPonderPlugin();
        PonderLocalization localization = new PonderLocalization();
        PonderSceneRegistry scenes = new PonderSceneRegistry(localization);

        plugin.registerSharedText(new DefaultSharedTextRegistrationHelper(MODID, localization));
        plugin.registerScenes(new DefaultPonderSceneRegistrationHelper(MODID, scenes));

        scenes.getRegisteredEntries()
                .forEach(entry -> PonderSceneRegistry.compileScene(localization, entry.getValue(), null));

        localization.shared.forEach((key, value) ->
                consumer.accept(ponderSharedLangKey(key), value));
        localization.specific.entrySet()
                .stream()
                .filter(entry -> MODID.equals(entry.getKey().getNamespace()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue()
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(text -> consumer.accept(ponderSpecificLangKey(entry.getKey(), text.getKey()), text.getValue())));
    }

    private static String ponderSharedLangKey(ResourceLocation key) {
        return key.getNamespace() + ".ponder.shared." + key.getPath();
    }

    private static String ponderSpecificLangKey(ResourceLocation sceneId, String key) {
        return sceneId.getNamespace() + ".ponder." + sceneId.getPath() + "." + key;
    }
}
