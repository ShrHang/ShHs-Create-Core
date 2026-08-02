package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record LogisticsNetworkLink(UUID networkId) {

    public static final Codec<LogisticsNetworkLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("Freq").forGetter(LogisticsNetworkLink::networkId)
    ).apply(instance, LogisticsNetworkLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsNetworkLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LogisticsNetworkLink::networkId,
            LogisticsNetworkLink::new
    );
}
