package io.github.shrhang.shhs_create_core.content.data;

import io.github.shrhang.shhs_create_core.content.registries.ShHsKeys;
import io.github.shrhang.shhs_create_core.content.ponder.ShHsPonderPlugin;
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

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.MODID;
import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;
import static net.createmod.catnip.lang.LangBuilder.DEFAULT_SPACE_WIDTH;

public class ShHsLang {

    public static final String CATEGORY_KEY = "key.categories." + MODID;

    public static String key(String type, String key) {
        return type + "." + MODID + "." + key;
    }
    
    public static MutableComponent component(String type, String key, Object... args) {
        return Component.translatable(key(type, key), args);
    }

    public static MutableComponent textComponent(String key, Object... args) {
        return component("text", key, args);
    }

    public static MutableComponent titleComponent(String key, Object... args) {
        return component("title", key, args);
    }


    public static MutableComponent tooltipComponent(String key, Object... args) {
        return component("tooltip", key, args);
    }

    public static MutableComponent tooltipComponentForGoggles(String key, Object... args) {
        return Component.literal(Strings.repeat(' ', getIndents(Minecraft.getInstance().font))).append(tooltipComponent(key, args));
    }

    private static int getIndents(Font font) {
        int spaceWidth = font.width(" ");
        if (DEFAULT_SPACE_WIDTH == spaceWidth) {
            return 4;
        }
        return Mth.ceil(DEFAULT_SPACE_WIDTH * 4 / spaceWidth);
    }

    public static void init() {
        REGISTRATE.addRawLang(key("text","no_enough_spell_tolerance"), "At least %s Spell Tolerance is required to cast this spell.");
        REGISTRATE.addRawLang(key("text","need_tolerance"), "Need Spell Tolerance: %s");
        REGISTRATE.addRawLang(key("text","empty_trait_no_target"), "No valid target in sight.");
        REGISTRATE.addRawLang(key("text","empty_trait_no_traits"), "No traits can be extracted from this target.");

        REGISTRATE.addRawLang(key("title", "container.endchest"), "%s's %s");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.header"), "Ender Chest Info");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.owner"), "Owner: %s");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.owner_unknown"), "Cannot find owner %s");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.locked"), "Locked: Only the owner can open.");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.unlocked"), "Unlocked: Anyone can open.");

        REGISTRATE.addRawLang(key("text","portable_stock_ticker.tooltip.linked"), "Linked.");
        REGISTRATE.addRawLang(key("text","portable_stock_ticker.no_data"), "Not Linked to a Logistics Network");
        REGISTRATE.addRawLang(key("text","portable_stock_ticker.no_network"), "Linked Logistics Network no exists.");
        REGISTRATE.addRawLang(key("text","portable_stock_ticker.unloaded"), "Linked Logistics Network is unloaded.");

        REGISTRATE.addRawLang(key("tooltip","sprayer.header"), "Sprayer Info");
        REGISTRATE.addRawLang(key("tooltip","sprayer.angle"), "Angle: %s / %s°");
        REGISTRATE.addRawLang(key("tooltip","sprayer.range"), "Range: %s x %s x %s");

        REGISTRATE.addRawLang(key("title", "fan_miracle"), "Fan Miracle");
        REGISTRATE.addRawLang(key("text", "fan_miracle.fan"), "Encased Fan with Miracle");

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
