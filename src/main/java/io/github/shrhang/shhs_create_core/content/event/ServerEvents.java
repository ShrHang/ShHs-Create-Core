package io.github.shrhang.shhs_create_core.content.event;

import io.github.shrhang.shhs_create_core.content.kinetics.drill.ServerDrillSoundLimiter;
import io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station.DimensionParcelStationBindingIndex;
import io.github.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestEvents;
import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalStock;
import io.github.shrhang.shhs_create_core.content.magic.MagicEventHandler;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;

public class ServerEvents {
    public static void init() {
        MagicEventHandler.init();
        MobMagicManager.init();
        ServerDrillSoundLimiter.init();
        DimensionParcelStationBindingIndex.init();
        BrassEnderChestEvents.init();
        TerminalStock.init();
    }
}
