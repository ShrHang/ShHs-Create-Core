package io.github.shrhang.shhs_create_core.content.registries;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tterrag.registrate.util.entry.BlockEntry;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public enum ShHsSkullTypes implements SkullBlock.Type {
    HUSK("husk", mcTexture("zombie/husk")),
    DROWNED("drowned", mcTexture("zombie/drowned"), mcTexture("zombie/drowned_outer_layer"));

    public final ResourceLocation id;
    public final ResourceLocation texture;
    @Nullable
    public final ResourceLocation outerTexture;
    public final ShHsSkullEntry entry;

    ShHsSkullTypes(String name, ResourceLocation texture) {
        this(name, texture, null);
    }

    ShHsSkullTypes(String name, ResourceLocation texture, @Nullable ResourceLocation outerTexture) {
        this.id = ShHsCreateCore.rl(name);
        this.texture = texture;
        this.outerTexture = outerTexture;

        SkullBlock.Type.TYPES.put(id.toString(), this);
        this.entry = REGISTRATE.skull(name, this);
    }

    private static ResourceLocation mcTexture(String path) {
        return ShHsCreateCore.ml("textures/entity/" + path + ".png");
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ShHsSkullTypes::addValidBlocks);
    }

    public static void clientInit(IEventBus modEventBus) {
        Client.init(modEventBus);
    }

    private static void addValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        Block[] blocks = Arrays.stream(values())
                .flatMap(type -> Stream.of(type.entry.head().get(), type.entry.wallHead().get()))
                .toArray(Block[]::new);
        event.modify(BlockEntityType.SKULL, blocks);
    }

    @Override
    public String getSerializedName() {
        return id.toString();
    }

    private static final class Client {
        private static final ModelLayerLocation LAYERED_HEAD = new ModelLayerLocation(
                ShHsCreateCore.rl("layered_skull_head"),
                "main"
        );
        private static final ModelLayerLocation OUTER_HEAD = new ModelLayerLocation(
                ShHsCreateCore.rl("layered_skull_outer_head"),
                "main"
        );

        private static void init(IEventBus modEventBus) {
            modEventBus.addListener(Client::registerLayerDefinitions);
            modEventBus.addListener(Client::createSkullModels);
        }

        private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(LAYERED_HEAD, () -> createHeadLayer(0.0F, 0.5F));
            event.registerLayerDefinition(OUTER_HEAD, () -> createHeadLayer(0.25F, 0.75F));
        }

        private static void createSkullModels(EntityRenderersEvent.CreateSkullModels event) {
            for (ShHsSkullTypes type : values()) {
                SkullModel model = type.outerTexture == null
                        ? new SkullModel(event.getEntityModelSet().bakeLayer(ModelLayers.ZOMBIE_HEAD))
                        : new ShHsLayeredSkullModel(
                                event.getEntityModelSet().bakeLayer(LAYERED_HEAD),
                                event.getEntityModelSet().bakeLayer(OUTER_HEAD),
                                type.outerTexture
                        );

                event.registerSkullModel(type, model);
            }
        }

        private static LayerDefinition createHeadLayer(float headDeformation, float hatDeformation) {
            MeshDefinition mesh = new MeshDefinition();
            var head = mesh.getRoot().addOrReplaceChild(
                    "head",
                    CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(
                                    -4.0F, -8.0F, -4.0F,
                                    8.0F, 8.0F, 8.0F,
                                    new CubeDeformation(headDeformation)
                            ),
                    PartPose.ZERO
            );
            head.addOrReplaceChild(
                    "hat",
                    CubeListBuilder.create()
                            .texOffs(32, 0)
                            .addBox(
                                    -4.0F, -8.0F, -4.0F,
                                    8.0F, 8.0F, 8.0F,
                                    new CubeDeformation(hatDeformation)
                            ),
                    PartPose.ZERO
            );
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static final class ShHsLayeredSkullModel extends SkullModel {
        private final SkullModel outerModel;
        private final ResourceLocation outerTexture;

        private ShHsLayeredSkullModel(ModelPart head, ModelPart outerHead, ResourceLocation outerTexture) {
            super(head);
            this.outerModel = new SkullModel(outerHead);
            this.outerTexture = outerTexture;
        }

        public void renderOuterLayer(
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight,
                float animation,
                float yRot
        ) {
            outerModel.setupAnim(animation, yRot, 0.0F);
            outerModel.renderToBuffer(
                    poseStack,
                    bufferSource.getBuffer(RenderType.entityCutoutNoCullZOffset(outerTexture)),
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );
        }
    }

    public static class ShHsSkullBlock extends SkullBlock {

        public ShHsSkullBlock(Type type, Properties properties) {
            super(type, properties);
        }
    }

    public static class ShHsWallSkullBlock extends WallSkullBlock {

        public ShHsWallSkullBlock(SkullBlock.Type type, Properties properties) {
            super(type, properties);
        }
    }

    public record ShHsSkullEntry(
            BlockEntry<ShHsSkullBlock> head,
            BlockEntry<ShHsWallSkullBlock> wallHead
    ) {}
}
