package io.github.shrhang.shhs_create_core.mixin.minecraft.server.level;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.shrhang.shhs_create_core.content.kinetics.drill.DrillHitSoundContext;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    // Tag the final packet after NeoForge sound hooks, preserving broadcast range and exclusions.
    @WrapOperation(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"))
    private void shhsc_c$sendDrillHit(PlayerList players, Player excluded, double x, double y, double z,
                                     double radius, ResourceKey<Level> dimension, Packet<?> packet, Operation<Void> original) {
        if (packet instanceof ClientboundSoundPacket) {
            DrillHitSoundContext.consume((ServerLevel) (Object) this, x, y, z);
        }
        original.call(players, excluded, x, y, z, radius, dimension, packet);
    }
}
