package com.shrhang.shhs_create_core.content.magic;

import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.network.casting.OnCastFinishedPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.shrhang.shhs_create_core.Config.COMMON;
import static com.shrhang.shhs_create_core.content.util.SpellToleranceHelper.*;

public class MagicEventHandler {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(MagicEventHandler::preCast);
    }

    @SubscribeEvent
    public static void preCast(SpellPreCastEvent event) {
        if (!COMMON.isToleranceRequired.get()) return;

        var spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) return;

        int level = event.getSpellLevel();
        double requiredTolerance = calculateRequiredTolerance(level, spell, event.getCastSource());

        Player player = event.getEntity();
        double spellTolerance = getSpellTolerance(player);

        if (spellTolerance < requiredTolerance) {
            event.setCanceled(true);
            MagicData.getPlayerMagicData(player).resetCastingState();
            if (player instanceof ServerPlayer serverPlayer)
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                        new OnCastFinishedPacket(serverPlayer.getUUID(), event.getSpellId(), true)
                );
            player.displayClientMessage(Component.translatable(lang, requiredTolerance).withStyle(ChatFormatting.RED), true );
        }
    }
}
