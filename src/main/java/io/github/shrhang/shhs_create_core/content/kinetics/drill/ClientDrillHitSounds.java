package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

public final class ClientDrillHitSounds {
    private ClientDrillHitSounds() {}

    public static void handle(ContraptionDrillHitSoundPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        ClientboundSoundPacket sound = packet.sound();
        if (!ShHsConfig.CLIENT.limitContraptionDrillHitSounds.get()
                || ClientDrillSoundLimiter.allow(minecraft.level,
                BlockPos.containing(sound.getX(), sound.getY(), sound.getZ()), sound.getSound().value(),
                sound.getSource(), ClientDrillSoundLimiter.Kind.HIT)) {
            minecraft.getConnection().handleSoundEvent(sound);
        }
    }
}
