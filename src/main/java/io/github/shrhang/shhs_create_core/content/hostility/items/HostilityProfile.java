package io.github.shrhang.shhs_create_core.content.hostility.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.l2hostility.content.capability.player.PlayerDifficulty;
import dev.xkmc.l2hostility.content.logic.DifficultyLevel;
import dev.xkmc.l2hostility.content.logic.TraitManager;
import io.github.shrhang.shhs_create_core.mixin.l2hostility.content.capability.player.PlayerDifficultyAccessor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.TreeSet;

/**
 * 可由恶意储蓄卡持久化和交换的玩家恶意档案。
 */
public record HostilityProfile(int version, int baseLevel, int extraLevel, int maxRankKilled,
                               int rewardCount, List<ResourceLocation> dimensions) {
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_DIMENSIONS = 1024;
    public static final HostilityProfile EMPTY = new HostilityProfile(CURRENT_VERSION, 0, 0, 0, 0, List.of());

    public static final Codec<HostilityProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("Version", CURRENT_VERSION).forGetter(HostilityProfile::version),
            Codec.INT.fieldOf("BaseLevel").forGetter(HostilityProfile::baseLevel),
            Codec.INT.fieldOf("ExtraLevel").forGetter(HostilityProfile::extraLevel),
            Codec.INT.fieldOf("MaxRankKilled").forGetter(HostilityProfile::maxRankKilled),
            Codec.INT.fieldOf("RewardCount").forGetter(HostilityProfile::rewardCount),
            ResourceLocation.CODEC.listOf().optionalFieldOf("Dimensions", List.of())
                    .forGetter(HostilityProfile::dimensions)
    ).apply(instance, HostilityProfile::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HostilityProfile> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** 去重并固定维度列表，避免档案被外部修改。 */
    public HostilityProfile {
        dimensions = List.copyOf(new TreeSet<>(dimensions));
    }

    /** 从玩家当前的 L2Hostility 能力中创建档案快照。 */
    public static HostilityProfile capture(PlayerDifficulty capability) {
        DifficultyLevel difficulty = ((PlayerDifficultyAccessor) capability).shhsc$getDifficulty();
        return new HostilityProfile(
                CURRENT_VERSION,
                difficulty.level,
                difficulty.extraLevel,
                capability.maxRankKilled,
                capability.rewardCount,
                List.copyOf(capability.dimensions)
        );
    }

    /** 检查档案版本和各字段是否可安全加载。 */
    public boolean isSupported() {
        return version == CURRENT_VERSION
                && baseLevel >= 0
                && maxRankKilled >= 0
                && rewardCount >= 0
                && dimensions.size() <= MAX_DIMENSIONS;
    }

    /** 返回与恶意信息页一致的持久等级表达式。 */
    public String persistentLevelString() {
        if (extraLevel == 0) {
            return Integer.toString(baseLevel);
        }
        return extraLevel > 0 ? baseLevel + "+" + extraLevel : baseLevel + Integer.toString(extraLevel);
    }

    /** 根据已击杀的最高词条阶级计算解锁的词条阶级上限。 */
    public int traitRankCap() {
        DifficultyLevel difficulty = new DifficultyLevel();
        difficulty.level = baseLevel;
        difficulty.extraLevel = extraLevel;
        return TraitManager.getTraitCap(maxRankKilled, difficulty);
    }

    /** 将档案数据恢复到指定玩家的 L2Hostility 能力。 */
    public void applyTo(Player player, PlayerDifficulty capability) {
        if (!isSupported()) {
            throw new IllegalStateException("Unsupported hostility profile version or values");
        }

        capability.getLevelEditor(player).setBase(baseLevel);
        DifficultyLevel difficulty = ((PlayerDifficultyAccessor) capability).shhsc$getDifficulty();
        difficulty.extraLevel = extraLevel;
        capability.maxRankKilled = maxRankKilled;
        capability.rewardCount = rewardCount;
        capability.dimensions.clear();
        capability.dimensions.addAll(dimensions);
        capability.prevChunk = null;
    }
}
