package io.github.shrhang.shhs_create_core.content.util.hostility;

import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public class TraitHelper {
    public record WeightedTrait(MobTrait trait, int weight, int cumulativeWeight) {
        /**
         * 根据权重随机选择 trait，
         * 权重越高，被选中的概率越大，
         * 自动剔除0级词条。
         */
        public static MobTrait selectTraitByWeight(MobTraitCap cap, LivingEntity target) {
            List<MobTrait> traits = new ArrayList<>(cap.traits.keySet());
            if (traits.isEmpty()) return null;
            List<WeightedTrait> weightedTraits = new ArrayList<>();
            int totalWeight = 0;
            for (MobTrait trait : traits) {
                if (cap.getTraitLevel(trait) <= 0) continue;
                int weight = trait.getConfig(target.level().registryAccess()).weight();
                if (weight > 0) {
                    weightedTraits.add(new WeightedTrait(trait, weight, totalWeight));
                    totalWeight += weight;
                }
            }
            if (totalWeight > 0) {
                int randomValue = target.level().random.nextInt(totalWeight);
                for (WeightedTrait wt : weightedTraits) {
                    if (randomValue < wt.cumulativeWeight + wt.weight) return wt.trait;
                }
            }
            return null;
        }
    }
}
