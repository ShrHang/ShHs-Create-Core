package com.shrhang.shhs_create_core.content.event.effect;

import com.shrhang.shhs_create_core.ShHsConfig;
import com.shrhang.shhs_create_core.content.effect.intangible.IntangibleState;
import com.shrhang.shhs_create_core.api.registries.ShHsAttachments;
import com.shrhang.shhs_create_core.api.registries.ShHsEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import static com.shrhang.shhs_create_core.api.registries.ShHsEffects.INTANGIBLE;

public class IntangibleEventHandler {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onInvulnerabilityCheck);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onPreDamage);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(IntangibleEventHandler::onPlayerLoggedOut);
    }

    private static void onInvulnerabilityCheck(final EntityInvulnerabilityCheckEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.hasEffect(INTANGIBLE)) return;
        var source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) return;
        if (source.is(DamageTypes.IN_WALL)) {
            event.setInvulnerable(true);
        }
    }

    private static void onPreDamage(final LivingDamageEvent.Pre event) {
        if (!ShHsConfig.SERVER.isSlayTheSpire.get()) return;

        var entity = event.getEntity();
        var effect = entity.getEffect(INTANGIBLE);
        if (effect == null) return;

        var source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) return;
        if (event.getNewDamage() <= 1.0f) return;

        event.setNewDamage(1.0F);

        int duration = effect.getDuration();
        int amplifier = effect.getAmplifier();

        if (amplifier > 0) {
            entity.removeEffectNoUpdate(INTANGIBLE);
            entity.addEffect(new MobEffectInstance(
                    INTANGIBLE,
                    duration,
                    amplifier - 1,
                    effect.isAmbient(),
                    effect.isVisible(),
                    effect.showIcon()
            ));
        } else {
            entity.removeEffect(INTANGIBLE);
        }
    }

    /**
     * 在效果被添加时捕获玩家的当前状态，如果是无实体则保存状态以便后续恢复。
     */
    private static void onEffectAdded(final MobEffectEvent.Added event) {
        if (event.getEffectInstance().is(INTANGIBLE) && event.getEntity() instanceof Player player) {
            IntangibleState state = player.getData(ShHsAttachments.INTANGIBLE_STATE);
            if (!state.isActive()) state.captureBeforeEffect(player);
        }
    }

    /**
     * 在每个玩家的tick结束时检查无实体状态，如果玩家没有无实体效果但具体效果还在，则复原玩家的能力。
     */
    private static void onPlayerTickPost(final PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        IntangibleState state = player.getExistingDataOrNull(ShHsAttachments.INTANGIBLE_STATE);
        if (state != null && state.isActive() && !player.hasEffect(INTANGIBLE)) restore(player, state);
    }

    /**
     * 在玩家登录时检查无实体状态，如果玩家处于无实体状态则重新捕获玩家的状态以防止数据错误，并进行服务端数据同步。
     */
    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.hasEffect(INTANGIBLE)) return;

        IntangibleState state = player.getData(ShHsAttachments.INTANGIBLE_STATE);
        state.recaptureAfterLogin(player);
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.onUpdateAbilities();
    }

    /**
     * 在玩家登出时检查无实体状态，如果玩家处于无实体状态则复原玩家的能力以防止数据丢失。
     */
    private static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        IntangibleState state = player.getExistingDataOrNull(ShHsAttachments.INTANGIBLE_STATE);
        if (state != null && state.isActive()) restore(player, state);
    }

    /**
     * 用于复原玩家状态的辅助方法，并进行服务端数据同步。
     */
    private static void restore(Player player, IntangibleState state) {
        state.restoreBeforeEffect(player);
        if (player instanceof ServerPlayer) player.onUpdateAbilities();
    }
}
