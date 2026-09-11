package io.github.shrhang.shhs_create_core.mixin.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.shrhang.shhs_create_core.content.registries.ShHsSkullTypes;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SkullBlock;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockRendererMixin {

    @Shadow
    @Final
    public static Map<SkullBlock.Type, ResourceLocation> SKIN_BY_TYPE;

    @Inject(method = "createSkullRenderers", at = @At("HEAD"))
    private static void shhs$registerSkins(
            EntityModelSet modelSet,
            CallbackInfoReturnable<Map<SkullBlock.Type, SkullModelBase>> cir
    ) {
        for (ShHsSkullTypes type : ShHsSkullTypes.values()) {
            SKIN_BY_TYPE.put(type, type.texture);
        }
    }

    @Inject(
            method = "renderSkull",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"
            )
    )
    private static void shhs$renderOuterLayer(
            @Nullable Direction direction,
            float yRot,
            float animation,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            SkullModelBase model,
            RenderType renderType,
            CallbackInfo ci
    ) {
        if (model instanceof ShHsSkullTypes.ShHsLayeredSkullModel layeredModel) {
            layeredModel.renderOuterLayer(poseStack, bufferSource, packedLight, animation, yRot);
        }
    }
}
