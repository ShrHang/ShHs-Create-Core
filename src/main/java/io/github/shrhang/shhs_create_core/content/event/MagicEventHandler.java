package io.github.shrhang.shhs_create_core.content.event;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.network.casting.OnCastFinishedPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import static io.github.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;
import static io.github.shrhang.shhs_create_core.content.util.magic.SpellToleranceHelper.*;

public class MagicEventHandler {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(MagicEventHandler::preCast);
        NeoForge.EVENT_BUS.addListener(MagicEventHandler::onCast);
    }

    private static void preCast(final SpellPreCastEvent event) {
        if (!SERVER.isToleranceRequired.get()) return;

        var spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) return;

        int level = event.getSpellLevel();
        double requiredTolerance = getRequiredTolerance(level, spell, event.getCastSource());

        Player player = event.getEntity();
        double spellTolerance = getSpellTolerance(player);

        if (spellTolerance < requiredTolerance) {
            if (player.isCreative()) return;
            event.setCanceled(true);
            MagicData.getPlayerMagicData(player).resetCastingState();
            if (player instanceof ServerPlayer serverPlayer)
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                        new OnCastFinishedPacket(serverPlayer.getUUID(), event.getSpellId(), true)
                );
            player.displayClientMessage(
                    textComponent("no_enough_spell_tolerance", requiredTolerance)
                            .withStyle(ChatFormatting.RED), true );
        }
    }

    private static void onCast(final SpellOnCastEvent event) {
        if (!SERVER.isToleranceRequired.get()) return;
        var spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) return;

        int level = event.getSpellLevel();
        double requiredTolerance = getRequiredTolerance(level, spell, event.getCastSource());

        Player player = event.getEntity();
        double spellTolerance = getSpellTolerance(player);

        var overallTolerance = spellTolerance - requiredTolerance;
        if  (overallTolerance > 0) {
            if (event.getCastSource().consumesMana()) {
                event.setManaCost((int) Math.ceil(event.getManaCost() * getManaCostReduction(overallTolerance)));
            }
        }
    }
}
