package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import java.util.Set;
import static io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper.RangeBoundary;


/**
 * 恶意吸收器在动态结构中的移动行为。
 * <p>
 * 移动开始时从 blockEntityData 读取边界（RangeBoundary），
 * 遍历边界内所有区段并执行 unclearSections，释放旧位置的清理状态。
 * 移动结束时无需操作，实体在结构停止后重新创建时自动恢复。
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
        if (boundary != null) {
            HostilityAbsorberHelper.forEachInRange(boundary, level, (l, pos) ->
                    HostilityAbsorberHelper.unclearSections(l, Set.of(pos))
            );
        }
    }
    @Override
    public void stopMoving(MovementContext context) {
        // 实体在结构停止后重新创建时自动恢复，无需操作
    }
    /**
     * 从 NBT 数据中读取边界。
     *
     * @param data 上下文中的方块实体 NBT
     * @return 边界，若不存在则返回 null
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
}