package com.shrhang.shhs_create_core.content.data;

import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsTagKey {
    public static final Map<ResourceLocation, Consumer<RegistrateTagsProvider.IntrinsicImpl<EntityType<?>>>> ENTITY_TAG_BUILDER = new TreeMap<>();
    public static void onEntityTagGen(RegistrateTagsProvider.IntrinsicImpl<EntityType<?>> provider) {
        ENTITY_TAG_BUILDER.values().forEach(e -> e.accept(provider));
    }

    public static void init() {
        REGISTRATE.addDataGenerator(ProviderType.ENTITY_TAGS, ShHsTagKey::onEntityTagGen);
    }
}
