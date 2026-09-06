package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;
import static io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper.RangeBoundary;
import static io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper.SectionPos;

/**
 * 恶意吸收器在动态结构中的移动行为。
 * <p>
 * 移动开始时从 blockEntityData 读取边界和 failedClear 集合，
 * 释放所有实际持有的区段（跳过从未持有的 failedClear），
 * 释放结果不重试，因为实体即将消失。
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

        Set<SectionPos> failedClear = readSectionSet(data, "FailedClear");

        HostilityAbsorberHelper.forEachInRange(boundary, level, (l, pos) -> {
            if (failedClear.contains(pos)) {
                return; // 跳过从未持有的区段
            }
            HostilityAbsorberHelper.unclearSections(l, Set.of(pos));
            // 忽略返回值，移动后实体消失，无需重试
        });
    }

    @Override
    public void stopMoving(MovementContext context) {
        // 实体在结构停止后重新创建时自动恢复，无需操作
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
}