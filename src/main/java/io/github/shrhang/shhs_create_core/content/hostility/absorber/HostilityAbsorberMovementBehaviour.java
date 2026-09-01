package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 恶意吸收器在动态结构中的移动行为。
 * <p>
 * 当方块被装配到移动结构时，立即恢复其当前作用范围内的所有区块，
 * 并阻止移动过程中的区块清除操作，避免将清除状态“携带”到新位置。
 */
public class HostilityAbsorberMovementBehaviour implements MovementBehaviour {

    /**
     * 在方块开始移动时调用。
     * <p>
     * 尝试从世界获取方块实体，若存在则调用 {@link HostilityAbsorberBlockEntity#onStartMoving()}；
     * 否则从 {@link MovementContext#blockEntityData} 读取 "LastHalfLength" 并恢复区块。
     * 世界坐标使用 {@code contraption.anchor + localPos}。
     *
     * @param context 移动上下文
     */
    @Override
    public void startMoving(MovementContext context) {
        Level level = context.world;
        if (level.isClientSide()) return;

        BlockPos worldPos = context.contraption.anchor.offset(context.localPos);

        BlockEntity be = level.getBlockEntity(worldPos);
        if (be instanceof HostilityAbsorberBlockEntity absorber) {
            absorber.onStartMoving();
            return;
        }

        CompoundTag data = context.blockEntityData;
        if (data == null) return;
        int halfLength = data.getInt("LastHalfLength");
        if (halfLength >= 0) {
            HostilityAbsorberBlockEntity.unclearRangeStatic(level, worldPos, halfLength);
        }
    }
    /**
     * 在方块停止移动时调用。
     * <p>
     * 清除移动标志，恢复正常的区块清除逻辑。
     *
     * @param context 移动上下文
     */
    @Override
    public void stopMoving(MovementContext context) {
        Level level = context.world;
        if (level.isClientSide()) return;

        BlockPos worldPos = context.contraption.anchor.offset(context.localPos);
        BlockEntity be = level.getBlockEntity(worldPos);
        if (be instanceof HostilityAbsorberBlockEntity absorber) {
            absorber.onStopMoving();
        }
    }
}