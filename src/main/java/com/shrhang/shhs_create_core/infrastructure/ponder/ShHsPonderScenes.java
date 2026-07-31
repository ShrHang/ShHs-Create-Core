package com.shrhang.shhs_create_core.infrastructure.ponder;

import com.shrhang.shhs_create_core.content.registries.ShHsBlocks;
import com.shrhang.shhs_create_core.infrastructure.ponder.scenes.BrassEnderChestScenes;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组所有 Ponder 场景的注册入口。
 * 负责将物品/方块与对应的场景方法绑定。
 * 与机械动力的 AllCreatePonderScenes 结构对齐。
 */
public class ShHsPonderScenes {

    /**
     * 注册所有场景绑定。
     *
     * @param helper Ponder 场景注册辅助器，由 Ponder 核心传入
     */
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 将通用注册器转换为能处理 ItemProviderEntry 的专用注册器
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> HELPER =
                helper.withKeyFunction(RegistryEntry::getId);

        // 黄铜末影箱
        HELPER.forComponents(ShHsBlocks.BRASS_ENDER_CHEST)
                .addStoryBoard("brass_ender_chest/introduce", BrassEnderChestScenes::introduce)
//                .addStoryBoard("brass_ender_chest/transit", BrassEnderChestScenes::transit)
                ;

        // 喷洒器
        HELPER.forComponents(ShHsBlocks.SPRAYER)
                .addStoryBoard("sprayer/intro", ShHsPonderScenes::sprayerIntro);

        // 恶意吸收器
        HELPER.forComponents(ShHsBlocks.HOSTILITY_ABSORBER)
                .addStoryBoard("hostility_absorber/intro", ShHsPonderScenes::hostilityAbsorberIntro);
    }

    /**
     * 黄铜末影箱的空白思索场景。
     */
    public static void brassEnderChestIntro(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("brass_ender_chest", "Brass Ender Chest");
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(5);
    }

    /**
     * 喷洒器的空白思索场景。
     */
    public static void sprayerIntro(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("scene.shhs_create_core.sprayer.intro", "Sprayer");
    }

    /**
     * 恶意吸收器的空白思索场景。
     */
    public static void hostilityAbsorberIntro(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("scene.shhs_create_core.hostility_absorber.intro", "Hostility Absorber");
    }
}