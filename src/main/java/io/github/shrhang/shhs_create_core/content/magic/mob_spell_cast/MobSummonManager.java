package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class MobSummonManager {
    // PlayerRecasts -> MagicData 映射
    private static final WeakHashMap<PlayerRecasts, MagicData> recastToMagicData = new WeakHashMap<>();
    // MagicData -> 施法者实体 映射
    private static final WeakHashMap<MagicData, LivingEntity> magicDataToCaster = new WeakHashMap<>();

    public static void registerRecasts(PlayerRecasts recasts, MagicData magicData) {
        if (recasts != null && magicData != null) {
            recastToMagicData.put(recasts, magicData);
        }
    }

    public static MagicData getMagicData(PlayerRecasts recasts) {
        return recastToMagicData.get(recasts);
    }

    public static void registerCaster(MagicData magicData, LivingEntity caster) {
        if (magicData != null && caster != null) {
            magicDataToCaster.put(magicData, caster);
        }
    }

    public static LivingEntity getCaster(MagicData magicData) {
        return magicDataToCaster.get(magicData);
    }

    /**
     * 清理该法术产生的召唤物（使用施法者获取世界）。
     */
    public static void onRecastFinished(LivingEntity caster, String spellId, RecastInstance recastInstance) {
        ICastDataSerializable castData = recastInstance.getCastData();
        if (!(castData instanceof SummonedEntitiesCastData summonedData)) {
            return;
        }
        Level level = caster.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (UUID uuid : summonedData.getSummons()) {
            Entity summon = serverLevel.getEntity(uuid);
            if (summon != null) {
                if (summon instanceof IMagicSummon magicSummon) {
                    magicSummon.onUnSummon();
                } else {
                    summon.discard();
                }
                SummonManager.removeSummon(summon);
            }
        }
    }

    /**
     * 清除某个实体的所有召唤物。
     */
    public static void clearAllSummons(LivingEntity entity) {
        Set<UUID> summons = SummonManager.getSummons(entity);
        if (summons == null || summons.isEmpty()) {
            return;
        }
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (UUID uuid : summons) {
            Entity summon = serverLevel.getEntity(uuid);
            if (summon != null) {
                SummonManager.removeSummon(summon);
            }
        }
    }
}