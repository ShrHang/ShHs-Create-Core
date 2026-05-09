package com.shrhang.shhs_create_core.mixin.curios.common.data;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.common.data.CuriosEntityManager;

/**
 * Fixes a bug in Curios where entity tags that start with '#' are not parsed correctly.
 * This mixin redirects the call to ResourceLocation.parse and removes the '#' if it is present.
 */
@Mixin(CuriosEntityManager.class)
public abstract class CuriosEntityManagerMixin {
    @Redirect(method = "getSlotsForEntities(Lcom/google/gson/JsonObject;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Map;", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;parse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"
    ))
    private static ResourceLocation fixEntityTagParsing(String entity) {
        if (entity.startsWith("#")) {
            return ResourceLocation.parse(entity.substring(1));
        }
        return ResourceLocation.parse(entity);
    }
}
