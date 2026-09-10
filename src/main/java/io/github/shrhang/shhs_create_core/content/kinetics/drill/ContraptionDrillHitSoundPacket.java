package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ContraptionDrillHitSoundPacket(ClientboundSoundPacket sound) implements CustomPacketPayload {
    public static final Type<ContraptionDrillHitSoundPacket> TYPE =
            new Type<>(ShHsCreateCore.rl("contraption_drill_hit_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionDrillHitSoundPacket> STREAM_CODEC =
            ClientboundSoundPacket.STREAM_CODEC.map(ContraptionDrillHitSoundPacket::new, ContraptionDrillHitSoundPacket::sound);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContraptionDrillHitSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDrillHitSounds.handle(packet));
    }
}
