package com.shrhang.shhs_create_core.content.magic;

import com.shrhang.shhs_create_core.Config;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;

import static com.shrhang.shhs_create_core.content.util.SpellCastHelper.entityCastSpell;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

public class MobMagicManager {
    public static final int MANA_REGEN_TICKS = 10;
    public static final int CONTINUOUS_CAST_TICK_INTERVAL = 10;

    public static boolean regenMana(LivingEntity entity, MagicData magicData) {
        int maxMana = (int) entity.getAttributeValue(MAX_MANA); // 获取最大法力值
        var mana = magicData.getMana(); // 获取当前法力值
        if (mana != maxMana) {
            var increment = maxMana * entity.getAttributeValue(MANA_REGEN) * .05f * Config.SERVER.mobManaRegenMultiplier.get().floatValue(); // 计算法力值增量
            magicData.setMana((float) Mth.clamp(magicData.getMana() + increment, 0, maxMana)); // 更新法力值，并确保不超过最大值
            return true;
        } else {
            return false;
        }
    }

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        Level level = entity.level();
        boolean doManaRegen = Objects.requireNonNull(level.getServer()).getTickCount() % MANA_REGEN_TICKS == 0;

        MagicData magicData = MagicData.getPlayerMagicData(entity);

        // 冷却相关逻辑
        magicData.getPlayerCooldowns().tick(1);
        magicData.getPlayerRecasts().tick(2);

        if (magicData.isCasting()) {
            var spell = SpellRegistry.getSpell(magicData.getCastingSpellId());
            if ((spell.getCastType() == CastType.LONG && !entity.isUsingItem()) || spell.getCastType() == CastType.INSTANT) {
                if (magicData.getCastDurationRemaining() <= 0) {
                    entityCastSpell(spell, level, magicData.getCastingSpellLevel(), entity, magicData.getCastSource(), true);
// TODO 暂时没实现区分施法源
//                    if (magicData.getCastSource() == CastSource.SCROLL) {
//                        removeMobsScroll(entity);
//                    }
                    spell.onServerCastComplete(level, magicData.getCastingSpellLevel(), entity, magicData, false);
                }
            } else if (spell.getCastType() == CastType.CONTINUOUS) {
                if ((magicData.getCastDurationRemaining()) % CONTINUOUS_CAST_TICK_INTERVAL == 0) {
                    if (magicData.getCastDurationRemaining() <= 0 || (magicData.getCastSource().consumesMana() && magicData.getMana() - spell.getManaCost(magicData.getCastingSpellLevel()) * 2 < 0)) {
                        entityCastSpell(spell, level, magicData.getCastingSpellLevel(), entity, magicData.getCastSource(), true);
// TODO 同上
//                        if (magicData.getCastSource() == CastSource.SCROLL) {
//                            removeMobsScroll(entity);
//                        }
                        spell.onServerCastComplete(level, magicData.getCastingSpellLevel(), entity, magicData, false);

                    } else {
                        entityCastSpell(spell, level, magicData.getCastingSpellLevel(), entity, magicData.getCastSource(), false);
                    }
                }
            }
            magicData.handleCastDuration();
            if (magicData.isCasting()) {
                spell.onServerCastTick(level, magicData.getCastingSpellLevel(), entity, magicData);
            }
        }

        if (doManaRegen) {
            if (regenMana(entity, magicData)) {
// TODO 暂时不知道有什么用，可能以后会写客户端同步怪物的法力值
//                if (entity instanceof ServerPlayer serverPlayer) {
//                    PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
//                }
            }
        }
        
    }

    /**
     * 由于没区分施法源，理所当然地这个方法不会被调用。
     */
    public static void removeMobsScroll(LivingEntity entity) {
        ItemStack potentialScroll = MagicData.getPlayerMagicData(entity).getPlayerCastingItem();
        if (potentialScroll.getItem() instanceof Scroll) {
            potentialScroll.shrink(1);
        }
    }

    public static void addCooldown(LivingEntity entity, AbstractSpell spell, CastSource castSource) {
        int effectiveCooldown = getEffectiveSpellCooldown(spell, entity, castSource);
//        var pre = NeoForge.EVENT_BUS.post(new SpellCooldownAddedEvent.Pre(effectiveCooldown, spell, entity, castSource));
//
//        if (castSource == CastSource.SCROLL || pre.isCanceled()) {
//            return;
//        }
//
//        effectiveCooldown = pre.getEffectiveCooldown();

        MagicData.getPlayerMagicData(entity).getPlayerCooldowns().addCooldown(spell, effectiveCooldown);
//        PacketDistributor.sendToPlayer(entity, new SyncCooldownPacket(spell.getSpellId(), effectiveCooldown));

//        NeoForge.EVENT_BUS.post(new SpellCooldownAddedEvent.Post(effectiveCooldown, spell, entity, castSource));
    }

    public static int getEffectiveSpellCooldown(AbstractSpell spell, LivingEntity entity, CastSource castSource) {
        double cooldownModifier = entity.getAttributeValue(COOLDOWN_REDUCTION);

        float itemCoolDownModifer = 1;
        if (castSource == CastSource.SWORD) {
            itemCoolDownModifer = ServerConfigs.SWORDS_CD_MULTIPLIER.get().floatValue();
        }
        return (int) (spell.getSpellCooldown() * (2 - Utils.softCapFormula(cooldownModifier)) * itemCoolDownModifer);
    }
}
