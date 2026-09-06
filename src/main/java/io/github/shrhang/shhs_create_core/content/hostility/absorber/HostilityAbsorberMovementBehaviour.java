package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.shrhang.shhs_create_core.content.util.hostility.HostilityAbsorberHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

import static io.github.shrhang.shhs_create_core.content.util.hostility.HostilityAbsorberHelper.RangeBoundary;
import static io.github.shrhang.shhs_create_core.content.util.hostility.HostilityAbsorberHelper.SectionPos;

/**
 * 恶意吸收器在动态结构中的移动行为。
 * <p>
 * 移动开始时，直接修改方块实体 NBT 数据：
 * - 清空 failedClear（移除键）。
 * - 遍历边界内所有区段，尝试释放（unclear），
 *   成功则从 failedUnclear 中移除，失败则加入 failedUnclear。
 * - 移除所有边界键，使实体加载后重新初始化。
 * 完全避免临时键或特殊标记，实体加载时自然处于重启状态。
 */
public class HostilityAbsorberMovementBehaviour implements MovementBehaviour {

    @Override
    public void startMoving(MovementContext context) {
        Level level = context.world;
        if (level.isClientSide()) {
            return;
        }

        CompoundTag data = context.blockEntityData;
        if (data == null) {
            return;
        }

        RangeBoundary boundary = readBoundaryFromNbt(data);
        if (boundary == null) {
            return;
        }

        // 读取原有的 failedClear（用于跳过从未持有的区段），然后移除该键（清空）
        Set<SectionPos> failedClear = readSectionSet(data, "FailedClear");
        data.remove("FailedClear");

        // 读取现有的 failedUnclear（保留原有失败区段，将在此过程中更新）
        Set<SectionPos> failedUnclear = readSectionSet(data, "FailedUnclear");

        // 遍历边界内所有区段，尝试释放
        HostilityAbsorberHelper.forEachInRange(boundary, level, (l, pos) -> {
            if (failedClear.contains(pos)) {
                return; // 跳过从未持有的区段
            }
            // 尝试释放该区段
            Set<SectionPos> result = HostilityAbsorberHelper.unclearSections(l, Set.of(pos));
            if (!result.isEmpty()) {
                // 释放失败：确保该区段存在于 failedUnclear 中
                failedUnclear.add(pos);
            } else {
                // 释放成功：从 failedUnclear 中移除（如果存在）
                failedUnclear.remove(pos);
            }
        });

        // 将更新后的 failedUnclear 写回 NBT
        if (!failedUnclear.isEmpty()) {
            writeSectionSet(data, "FailedUnclear", failedUnclear);
        } else {
            data.remove("FailedUnclear");
        }

        // 移除所有边界键，使实体加载后边界为 null，触发重新初始化
        data.remove("MinX");
        data.remove("MaxX");
        data.remove("MinY");
        data.remove("MaxY");
        data.remove("MinZ");
        data.remove("MaxZ");
    }

    /**
     * 从 NBT 数据中读取边界。
     */
    private RangeBoundary readBoundaryFromNbt(CompoundTag data) {
        if (!data.contains("MinX")) {
            return null;
        }
        return new RangeBoundary(
                data.getInt("MinX"),
                data.getInt("MaxX"),
                data.getInt("MinY"),
                data.getInt("MaxY"),
                data.getInt("MinZ"),
                data.getInt("MaxZ")
        );
    }

    /**
     * 从 NBT 读取 SectionPos 集合（与静态实体序列化格式一致）。
     */
    private Set<SectionPos> readSectionSet(CompoundTag compound, String key) {
        Set<SectionPos> set = new HashSet<>();
        ListTag list = compound.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            set.add(new SectionPos(
                    entry.getInt("X"),
                    entry.getInt("Y"),
                    entry.getInt("Z")
            ));
        }
        return set;
    }

    /**
     * 将 SectionPos 集合写入 NBT（格式与静态实体一致）。
     */
    private void writeSectionSet(CompoundTag compound, String key, Set<SectionPos> set) {
        ListTag list = new ListTag();
        for (SectionPos pos : set) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", pos.x());
            entry.putInt("Y", pos.y());
            entry.putInt("Z", pos.z());
            list.add(entry);
        }
        compound.put(key, list);
    }
}