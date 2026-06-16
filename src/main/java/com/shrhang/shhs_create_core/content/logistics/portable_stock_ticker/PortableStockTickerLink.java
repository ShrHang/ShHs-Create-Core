package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record PortableStockTickerLink(UUID networkId) {

    public static final Codec<PortableStockTickerLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("network").forGetter(PortableStockTickerLink::networkId)
    ).apply(instance, PortableStockTickerLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortableStockTickerLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PortableStockTickerLink::networkId,
            PortableStockTickerLink::new
    );
}
