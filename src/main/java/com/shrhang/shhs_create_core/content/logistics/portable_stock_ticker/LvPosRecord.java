package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record LvPosRecord(ResourceKey<Level> dimension, BlockPos pos) {
    public static final LvPosRecord EMPTY = new LvPosRecord(null, BlockPos.ZERO);

    public static final Codec<LvPosRecord> CODEC = RecordCodecBuilder.create(instance ->instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(LvPosRecord::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(LvPosRecord::pos)
    ).apply(instance, LvPosRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LvPosRecord> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), LvPosRecord::dimension,
            BlockPos.STREAM_CODEC, LvPosRecord::pos,
            LvPosRecord::new
    );
}
