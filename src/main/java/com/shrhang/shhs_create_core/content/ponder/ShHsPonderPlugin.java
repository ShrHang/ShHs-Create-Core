package com.shrhang.shhs_create_core.content.ponder;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.IndexExclusionHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder 插件实现，负责向 Ponder 核心注册本模组的场景、标签和共享文本。
 * 在客户端初始化时通过 PonderIndex.addPlugin() 注册。
 */
public class ShHsPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return ShHsCreateCore.MODID;
    }

    /**
     * 注册所有 Ponder 场景。
     */
    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ShHsPonderScenes.register(helper);
    }

    /**
     * 注册所有 Ponder 标签（分类）。
     * 如无标签可留空实现。
     */
    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // 本模组暂未定义标签，留空
    }

    /**
     * 注册可在多个场景中复用的共享文本。
     * 如无共享文本可留空实现。
     */
    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        // 本模组暂未定义共享文本，留空
    }

    /**
     * 在 Ponder 世界恢复时执行额外的方块实体修复逻辑。
     * 如无需修复可留空实现。
     */
    @Override
    public void onPonderLevelRestore(PonderLevel ponderLevel) {
        // 本模组暂无需修复，留空
    }

    /**
     * 排除某些方块变体在 Ponder 索引中显示。
     * 如无排除项可留空实现。
     */
    @Override
    public void indexExclusions(IndexExclusionHelper helper) {

    }
}