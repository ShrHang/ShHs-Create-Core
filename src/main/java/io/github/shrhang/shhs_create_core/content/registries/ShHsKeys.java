package io.github.shrhang.shhs_create_core.content.registries;


import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.CATEGORY_KEY;
import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.keyKey;

public enum ShHsKeys {
    OPEN_PORTABLE_STOCK_TICKER("open_portable_stock_ticker", "Open Portable Stock Ticker", GLFW.GLFW_KEY_B);

    private final KeyMapping keybind;
    private final String langKey;
    private final String translation;

    ShHsKeys(String description, String translation, int key) {
        this.langKey = keyKey(description);
        this.translation = translation;
        this.keybind = new KeyMapping(langKey, key, CATEGORY_KEY);
    }

    public static void provideLang(BiConsumer<String, String> consumer) {
        for (ShHsKeys key :ShHsKeys.values()) consumer.accept(key.langKey, key.translation);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (ShHsKeys key : values()) event.register(key.keybind);
    }

    public KeyMapping getKeybind() {
        return keybind;
    }
}
