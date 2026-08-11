package io.github.shrhang.shhs_create_core.content.registries;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlock;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerModel;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerMovementBehaviour;
import io.github.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberBlock;
import io.github.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberMovementBehaviour;
import io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.material.MapColor;

import static com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsBlocks {

    public static final BlockEntry<BrassEnderChestBlock> BRASS_ENDER_CHEST = REGISTRATE
            .block("brass_ender_chest", BrassEnderChestBlock::new)
            .lang("Brass Ender Chest")
            .properties(p -> p
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 600.0f)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops()
            )
            .blockTags(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE)
            .blockstate((ctx, prov) ->
                    prov.horizontalBlock(ctx.getEntry(), prov.models().getExistingFile(ctx.getId()))
            )
            .item(item -> item
                    .tooltipSummary("An Ender Chest that can interact with _funnels_, _chutes_, _packagers_, and other _logistics components_. It can only access the Ender Chest _Inventory_ of its _owner_. If the owner is _offline_, the Brass Ender Chest _cannot be interacted with_.")
                    .tooltipBehaviour(1, "When R-Clicked while Sneaking", "Toggle the _lock state_.")
                    .tooltipBehaviour(2, "When interacting with a clipboard", "_Copy_/_Paste_ the _owner_ and _lock state_ of the Brass Ender Chest.")
            )
            .register();

    public static final BlockEntry<SprayerBlock> SPRAYER = REGISTRATE
            .block("sprayer", SprayerBlock::new)
            .lang("Sprayer")
            .initialProperties(SharedProperties::copperMetal)
            .item(item -> item
                    .model(AssetLookup::customItemModel)
                    .tooltipSummary("A sprayer that sprays fluid forward, with rate controlled by rotational input.")
                    .tooltipBehaviour(1, "Connect rotational power to the face",
                            "Rotational speed controls a valve angle from 0 to 180 degrees, linearly adjusting the spray rate.")
                    .tooltipBehaviour(2, "Place facing direction",
                            "Sprays forward every 0.25 seconds, consuming up to 32 mB per spray when fully open.")
            )
            .transform(pickaxeOnly())
            .blockstate((ctx, prov) -> BlockStateGen.directionalAxisBlock(ctx, prov,
                    (state, vertical) -> AssetLookup.partialBaseModel(ctx, prov,
                            vertical ? "vertical" : "horizontal")))
            .onRegister(CreateRegistrate.blockModel(() -> SprayerModel::withAO))
            .onRegister(movementBehaviour(new SprayerMovementBehaviour()))
            .register();

    /**
     * 恶意吸收器方块，属于动力学方块，固定旋转轴为 Y 轴，复用石磨齿轮模型。
     */
    public static final BlockEntry<HostilityAbsorberBlock> HOSTILITY_ABSORBER = REGISTRATE
            .block("hostility_absorber", HostilityAbsorberBlock::new)
            .lang("Hostility Absorber")
            .properties(p -> p
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 600.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            )
            .blockstate((ctx, prov) -> {})
            .blockTags(BlockTags.MINEABLE_WITH_PICKAXE)
            .item(item -> item
                    .model((ctx, prov) -> {})
                    .tooltipSummary("A dynamic absorber that clears hostility from chunks based on rotational speed.")
                    .tooltipBehaviour(1, "Connect rotational power",
                            "Higher speed increases the absorption radius up to config limit.")
                    .tooltipBehaviour(2, "When speed decreases",
                            "Restores previously cleared chunks automatically.")
                    .tooltipBehaviour(3, "When broken",
                            "Restores all cleared chunks within the last radius.")
            )
            .stressImpact(4.0)
            .onRegister(movementBehaviour(new HostilityAbsorberMovementBehaviour()))
            .register();

    public static void register() {
    }
}