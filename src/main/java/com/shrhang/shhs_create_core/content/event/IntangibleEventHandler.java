package com.shrhang.shhs_create_core.content.event;

import com.shrhang.shhs_create_core.content.effect.IntangibleState;
import com.shrhang.shhs_create_core.content.registries.Attachments;
import com.shrhang.shhs_create_core.content.registries.Effects;
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

    private static void onEffectAdded(final MobEffectEvent.Added event) {
        if (!event.getEffectInstance().is(Effects.INTANGIBLE)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        IntangibleState state = player.getData(Attachments.INTANGIBLE_STATE);
        if (!state.isActive()) {
            state.capture(player);
        }
    }

    private static void onPlayerTickPost(final PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        IntangibleState state = player.getExistingDataOrNull(Attachments.INTANGIBLE_STATE);
        if (state == null || !state.isActive() || player.hasEffect(Effects.INTANGIBLE)) return;
        restore(player, state);
    }

    private static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        IntangibleState state = player.getExistingDataOrNull(Attachments.INTANGIBLE_STATE);
        if (state != null && state.isActive()) {
            restore(player, state);
        }
    }

    private static void restore(Player player, IntangibleState state) {
        state.restore(player);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
        }
    }
}
