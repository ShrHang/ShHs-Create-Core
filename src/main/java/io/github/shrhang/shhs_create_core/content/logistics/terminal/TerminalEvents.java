package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class TerminalEvents {
    private TerminalEvents() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TerminalEvents::commands);
        NeoForge.EVENT_BUS.addListener(TerminalEvents::stopped);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shh").then(Commands.literal("dlt").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            DimensionLogisticsTerminalMenu.open(player, net);
            return 1;
        })));
    }

    private static void stopped(ServerStoppedEvent event) { TerminalStock.clearCache(); }
}
