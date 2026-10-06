package io.github.shrhang.shhs_create_core.mixin.minecraft.server.players;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.server.IntegratedPlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedPlayerList.class)
public interface IntegratedPlayerDataAccessor {
    @Mutable
    @Accessor("playerData")
    void shhs$setPlayerData(CompoundTag playerData);
}
