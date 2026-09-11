package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.LevelEvent;

public final class ClientDrillBreakEffects {
    public static void handle(ContraptionDrillBreakEffectPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        ClientDrillEffectContext.run(level, packet.pos(),
                ShHsConfig.CLIENT.disableContraptionDrillBreakParticles.get(), packet.playSound(),
                () -> level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK,
                        packet.pos(), packet.blockStateId()));
    }
}
