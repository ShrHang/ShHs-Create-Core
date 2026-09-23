package io.github.shrhang.shhs_create_core.content.hostility.items;

import com.mojang.logging.LogUtils;
import dev.xkmc.l2hostility.content.capability.player.PlayerDifficulty;
import dev.xkmc.l2hostility.content.logic.TraitManager;
import dev.xkmc.l2hostility.init.data.LangData;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import io.github.shrhang.shhs_create_core.ShHsConfig;
import io.github.shrhang.shhs_create_core.content.registries.ShHsComponentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.List;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.textComponent;

public class HostilityDebitCardItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DustParticleOptions SWAP_PARTICLE =
            new DustParticleOptions(new Vector3f(0.58f, 0.12f, 0.82f), 1.25f);

    public HostilityDebitCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getUseDuration(stack, player) <= 0) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                swapProfile(stack, serverPlayer);
            }
        } else {
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            swapProfile(stack, player);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return ShHsConfig.SERVER.hostilityDebitCardUseDurationTicks.get();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        HostilityProfile profile = stack.getOrDefault(ShHsComponentTypes.HOSTILITY_PROFILE, HostilityProfile.EMPTY);
        tooltip.add(LangData.INFO_PLAYER_LEVEL.get(profile.persistentLevelString())
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        if (Screen.hasShiftDown()) {
            tooltip.add(indent(LangData.INFO_PLAYER_ADAPTIVE_LEVEL.get(profile.baseLevel())
                    .withStyle(ChatFormatting.GRAY)));
            tooltip.add(indent(LangData.INFO_PLAYER_EXT_LEVEL.get(profile.extraLevel())
                    .withStyle(ChatFormatting.GRAY)));
            tooltip.add(indent(textComponent("hostility_debit_card.dimensions", profile.dimensions().size())
                    .withStyle(ChatFormatting.GRAY)));

            int traitRankCap = profile.traitRankCap();
            Object traitRankDisplay = traitRankCap > TraitManager.getMaxLevel()
                    ? LangData.TOOLTIP_LEGENDARY.get().withStyle(ChatFormatting.DARK_PURPLE)
                    : traitRankCap;
            tooltip.add(LangData.INFO_PLAYER_CAP.get(traitRankDisplay).withStyle(ChatFormatting.GRAY));
            tooltip.add(LangData.INFO_REWARD.get(profile.rewardCount()).withStyle(ChatFormatting.GRAY));
            tooltip.add(textComponent("hostility_debit_card.dynamic_warning").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static Component indent(Component component) {
        return Component.literal("  ").append(component);
    }

    private void swapProfile(ItemStack stack, ServerPlayer player) {
        HostilityProfile stored = stack.getOrDefault(ShHsComponentTypes.HOSTILITY_PROFILE, HostilityProfile.EMPTY);
        if (!stored.isSupported()) {
            player.displayClientMessage(textComponent("hostility_debit_card.invalid_profile")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        PlayerDifficulty capability = LHMiscs.PLAYER.type().getOrCreate(player);
        HostilityProfile current = HostilityProfile.capture(capability);
        try {
            stored.applyTo(player, capability);
            stack.set(ShHsComponentTypes.HOSTILITY_PROFILE, current);
            capability.sync(player);
        } catch (RuntimeException exception) {
            current.applyTo(player, capability);
            capability.sync(player);
            LOGGER.error("Failed to swap hostility profile for {}", player.getGameProfile().getName(), exception);
            player.displayClientMessage(textComponent("hostility_debit_card.swap_failed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        int cooldownTicks = ShHsConfig.SERVER.hostilityDebitCardCooldownTicks.get();
        if (cooldownTicks > 0) {
            player.getCooldowns().addCooldown(this, cooldownTicks);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.displayClientMessage(textComponent("hostility_debit_card.swapped",
                current.persistentLevelString(), stored.persistentLevelString(),
                stored.persistentLevelString(), current.persistentLevelString())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);

        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.8f, 1.15f);
        if (ShHsConfig.SERVER.hostilityDebitCardGenerateParticles.get()
                && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(SWAP_PARTICLE, player.getX(), player.getY() + 1.0, player.getZ(),
                    32, 0.4, 0.6, 0.4, 0.02);
        }
    }
}
