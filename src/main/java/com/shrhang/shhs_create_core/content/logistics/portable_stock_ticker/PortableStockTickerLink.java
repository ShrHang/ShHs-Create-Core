package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PortableStockTickerLink(UUID networkId, ResourceKey<Level> dimension, BlockPos sourcePos) {
    public static final Codec<PortableStockTickerLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("network").forGetter(PortableStockTickerLink::networkId),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PortableStockTickerLink::dimension),
            BlockPos.CODEC.fieldOf("source_pos").forGetter(PortableStockTickerLink::sourcePos)
    ).apply(instance, PortableStockTickerLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortableStockTickerLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PortableStockTickerLink::networkId,
            ResourceKey.streamCodec(Registries.DIMENSION), PortableStockTickerLink::dimension,
            BlockPos.STREAM_CODEC, PortableStockTickerLink::sourcePos,
            PortableStockTickerLink::new
    );
}
