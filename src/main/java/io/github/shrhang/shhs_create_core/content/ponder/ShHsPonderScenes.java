package io.github.shrhang.shhs_create_core.content.ponder;

import io.github.shrhang.shhs_create_core.content.registries.ShHsBlocks;
import io.github.shrhang.shhs_create_core.content.ponder.scenes.BrassEnderChestScenes;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class ShHsPonderScenes {
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> HELPER =
                helper.withKeyFunction(RegistryEntry::getId);

        // 黄铜末影箱
        HELPER.forComponents(ShHsBlocks.BRASS_ENDER_CHEST)
                .addStoryBoard("brass_ender_chest/transit", BrassEnderChestScenes::transit, AllCreatePonderTags.LOGISTICS)
                .addStoryBoard("brass_ender_chest/feature", BrassEnderChestScenes::feature);

        // 喷洒器
//        HELPER.forComponents(ShHsBlocks.SPRAYER)
//                .addStoryBoard("sprayer/intro", SprayerScenes::intro, AllCreatePonderTags.FLUIDS);

        // 恶意吸收器
//        HELPER.forComponents(ShHsBlocks.HOSTILITY_ABSORBER)
//                .addStoryBoard("hostility_absorber/intro", HostilityAbsorberScenes::intro);
    }
}