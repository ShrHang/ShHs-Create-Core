package io.github.shrhang.shhs_create_core.content.event;

import io.github.shrhang.shhs_create_core.content.hostility.attacklistener.ShHsAttackListener;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.ServerDrillSoundLimiter;
import io.github.shrhang.shhs_create_core.content.magic.MagicEventHandler;

public class ServerEvents {
    public static void init() {
        MagicEventHandler.init();
        ShHsAttackListener.init();
        ServerDrillSoundLimiter.init();
    }
}
