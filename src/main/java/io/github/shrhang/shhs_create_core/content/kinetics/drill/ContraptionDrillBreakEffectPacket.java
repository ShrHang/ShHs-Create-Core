package io.github.shrhang.shhs_create_core.content.kinetics.drill;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ContraptionDrillBreakEffectPacket(BlockPos pos, int blockStateId, boolean playSound) implements CustomPacketPayload {
    public static final Type<ContraptionDrillBreakEffectPacket> TYPE =
            new Type<>(ShHsCreateCore.rl("contraption_drill_break_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionDrillBreakEffectPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ContraptionDrillBreakEffectPacket::pos,
                    ByteBufCodecs.VAR_INT, ContraptionDrillBreakEffectPacket::blockStateId,
                    ByteBufCodecs.BOOL, ContraptionDrillBreakEffectPacket::playSound,
                    ContraptionDrillBreakEffectPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContraptionDrillBreakEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDrillBreakEffects.handle(packet));
    }
}
