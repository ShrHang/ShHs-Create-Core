package io.github.shrhang.shhs_create_core;

import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerGoggleOutlineHandler;
import io.github.shrhang.shhs_create_core.content.registries.ShHsKeys;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 模组客户端专用入口，负责注册客户端事件、模型、按键等。
 * 仅在客户端环境下加载。
 */
@Mod(value = ShHsCreateCore.MODID, dist = Dist.CLIENT)
public class ShHsClient {

    public static final SprayerGoggleOutlineHandler SPRAYER_GOGGLE_HANDLER = new SprayerGoggleOutlineHandler();

    public ShHsClient(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(SPRAYER_GOGGLE_HANDLER);
        modEventBus.addListener(ShHsKeys::register);
    }
}