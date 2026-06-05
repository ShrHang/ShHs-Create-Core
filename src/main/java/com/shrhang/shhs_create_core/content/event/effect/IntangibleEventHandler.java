package com.shrhang.shhs_create_core.content.event.effect;

import com.shrhang.shhs_create_core.content.effect.intangible.IntangibleState;
import com.shrhang.shhs_create_core.api.registries.ShHsAttachments;
import com.shrhang.shhs_create_core.api.registries.ShHsEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class IntangibleEventHandler {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onPlayerLoggedOut);
    }

    /**
     * 在效果被添加时捕获玩家的当前状态，如果是无实体则保存状态以便后续恢复。
     */
    private static void onEffectAdded(final MobEffectEvent.Added event) {
        if (event.getEffectInstance().is(ShHsEffects.INTANGIBLE) && event.getEntity() instanceof Player player) {
            IntangibleState state = player.getData(ShHsAttachments.INTANGIBLE_STATE);
            if (!state.isActive()) state.capture(player);
        }
    }

    /**
     * 在每个玩家的tick结束时检查无实体状态，如果玩家没有无实体效果但具体效果还在，则复原玩家的能力。
     */
    private static void onPlayerTickPost(final PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        IntangibleState state = player.getExistingDataOrNull(ShHsAttachments.INTANGIBLE_STATE);
        if (state != null && state.isActive() && !player.hasEffect(ShHsEffects.INTANGIBLE)) restore(player, state);
    }

    /**
     * 在玩家登出时检查无实体状态，如果玩家处于无实体状态则复原玩家的能力以防止数据丢失。
     */
    private static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        IntangibleState state = player.getExistingDataOrNull(ShHsAttachments.INTANGIBLE_STATE);
        if (state != null && state.isActive()) restore(player, state);
    }

    /**
     * 用于复原玩家状态的辅助方法，并进行服务端数据同步。
     */
    private static void restore(Player player, IntangibleState state) {
        state.restore(player);
        if (player instanceof ServerPlayer) player.onUpdateAbilities();
    }
}
