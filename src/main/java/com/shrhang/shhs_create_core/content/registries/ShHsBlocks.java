package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlock;
import com.shrhang.shhs_create_core.content.hostility.absorber.HostilityAbsorberBlock;
import com.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.material.MapColor;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

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
            .properties(p -> p
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5f, 600.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            )
            .blockstate((ctx, prov) -> {}) // TODO 喷洒器的模型
            .blockTags(BlockTags.MINEABLE_WITH_PICKAXE)
            .item(item -> item
                    .model((ctx, prov) -> {})
                    .tooltipSummary("A sprayer that sprays fluid forward, with rate controlled by rotational input.")
                    .tooltipBehaviour(1, "Connect rotational power to the face",
                            "Rotational speed controls a valve angle from 0 to 180 degrees, linearly adjusting the spray rate.")
                    .tooltipBehaviour(2, "Place facing direction",
                            "Sprays forward every 0.25 seconds, consuming up to 32 mB per spray when fully open.")
            )
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
            .blockstate((ctx, prov) ->{}) // TODO 石磨的模型
            .blockTags(BlockTags.MINEABLE_WITH_PICKAXE)
            .stressImpact(4.0)
            .item(item -> item
                    .model((ctx, prov) -> {})
                    .tooltipSummary("A dynamic absorber that clears hostile traits from chunks based on rotational speed.")
                    .tooltipBehaviour(1, "Connect rotational power",
                            "Higher speed increases the absorption radius up to config limit.")
                    .tooltipBehaviour(2, "When speed decreases",
                            "Restores previously cleared chunks automatically.")
                    .tooltipBehaviour(3, "When broken",
                            "Restores all cleared chunks within the last radius.")
            )
            .register();

    public static void register() {
    }
}