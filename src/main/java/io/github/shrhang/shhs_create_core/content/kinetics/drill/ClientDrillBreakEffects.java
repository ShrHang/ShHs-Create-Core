package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.LevelEvent;

public final class ClientDrillBreakEffects {
    private ClientDrillBreakEffects() {}

    public static void handle(ContraptionDrillBreakEffectPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ClientDrillEffectContext.run(minecraft.level, packet.pos(),
                ShHsConfig.CLIENT.disableContraptionDrillBreakParticles.get(),
                () -> minecraft.level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK,
                        packet.pos(), packet.blockStateId()));
    }
}
