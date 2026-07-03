package com.shrhang.shhs_create_core.content.hostility.absorber;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dev.xkmc.l2hostility.content.capability.chunk.ChunkCapHolder;
import dev.xkmc.l2hostility.content.capability.chunk.ChunkDifficulty;
import dev.xkmc.l2hostility.content.capability.chunk.SectionDifficulty;
import dev.xkmc.l2hostility.init.data.LHConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 恶意吸收器方块实体，通过内部 AbsorptionBehaviour 处理区块清除逻辑。
 * <p>
 * 行为根据动力学转速动态调整清除范围，并在转速变化时对环带施加 clear/unclear 脉冲。
 * 支持动态结构移动：移动时立即恢复区块，并暂停所有清除操作。
 * 移动行为由外部类 {@link HostilityAbsorberMovementBehaviour} 实现。
 */
public class HostilityAbsorberBlockEntity extends KineticBlockEntity {

    public static final BehaviourType<AbsorptionBehaviour> ABSORPTION_BEHAVIOUR = new BehaviourType<>();

    private boolean isMoving = false;

    public HostilityAbsorberBlockEntity(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(@NotNull List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new AbsorptionBehaviour(this));
    }

    /**
     * 当方块开始移动时调用。
     * <p>
     * 立即恢复当前清除范围的所有区块，并标记为移动状态，防止后续清除操作。
     */
    public void onStartMoving() {
        if (level == null || level.isClientSide()) return;

        AbsorptionBehaviour behaviour = getBehaviour(ABSORPTION_BEHAVIOUR);
        if (behaviour != null) {
            int halfLength = behaviour.lastHalfLength;
            if (halfLength >= 0) {
                unclearRangeStatic(level, worldPosition, halfLength);
            }
            isMoving = true;
        }
    }

    /**
     * 当方块停止移动时调用。
     * <p>
     * 清除移动标记，恢复正常的区块清除逻辑。
     */
    public void onStopMoving() {
        if (level == null || level.isClientSide()) return;
        isMoving = false;
    }

    /**
     * 静态方法：恢复指定半径内的所有区块。
     * <p>
     * 供移动行为调用，不依赖方块实体实例。
     */
    public static void unclearRangeStatic(@NotNull Level level, @NotNull BlockPos center, int halfLength) {
        if (halfLength < 0) return;
        forEachInRingStatic(level, center, halfLength, -1, false);
    }

    /**
     * 静态方法：遍历环带并执行 clear/unclear。
     */
    public static void forEachInRingStatic(@NotNull Level level, @NotNull BlockPos center, int outer, int inner, boolean clear) {
        if (level.isClientSide()) return;

        for (int dx = -outer; dx <= outer; dx++) {
            for (int dy = -outer; dy <= outer; dy++) {
                for (int dz = -outer; dz <= outer; dz++) {
                    int dist = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    if (dist > outer || (inner >= 0 && dist <= inner)) continue;

                    BlockPos pos = center.offset(dx * 16, dy * 16, dz * 16);
                    if (level.isOutsideBuildHeight(pos)) continue;

                    Optional<ChunkCapHolder> opt = ChunkDifficulty.at(level, pos);
                    if (opt.isEmpty()) continue;

                    ChunkCapHolder chunkCap = opt.get();
                    SectionDifficulty section = chunkCap.getSection(pos.getY());

                    if (clear) {
                        section.setClear(chunkCap, pos);
                    } else {
                        section.setUnclear(chunkCap, pos);
                    }
                }
            }
        }
    }

    public class AbsorptionBehaviour extends BlockEntityBehaviour {
        private static final int CLEAR_INTERVAL = 40;
        private int lastHalfLength = -1;

        public AbsorptionBehaviour(@NotNull SmartBlockEntity be) {
            super(be);
        }

        @Override
        @NotNull
        public BehaviourType<?> getType() {
            return ABSORPTION_BEHAVIOUR;
        }

        @Override
        public void tick() {
            super.tick();
            Level level = getWorld();
            if (level == null || level.isClientSide()) return;

            if (isMoving) return;

            float speed = getSpeed();
            int currentHalfLength = calculateHalfLength(speed);
            if (currentHalfLength != lastHalfLength) {
                if (currentHalfLength > lastHalfLength) {
                    if (lastHalfLength < 0 && currentHalfLength >= 0) {
                        clearRange(currentHalfLength);
                    } else {
                        clearRing(currentHalfLength, lastHalfLength);
                    }
                } else {
                    if (currentHalfLength < 0 && lastHalfLength >= 0) {
                        unclearRange(lastHalfLength);
                    } else {
                        unclearRing(lastHalfLength, currentHalfLength);
                    }
                }
                lastHalfLength = currentHalfLength;
            }
            if (currentHalfLength >= 0) {
                long gameTime = level.getGameTime();
                if (gameTime % CLEAR_INTERVAL == 0) {
                    clearRange(currentHalfLength);
                }
            }
        }

        @Override
        public void destroy() {
            if (getWorld() != null && !getWorld().isClientSide() && lastHalfLength >= 0) {
                unclearRange(lastHalfLength);
            }
            super.destroy();
        }

        @Override
        public void write(@NotNull CompoundTag compound, @NotNull HolderLookup.Provider registries, boolean clientPacket) {
            super.write(compound, registries, clientPacket);
            compound.putInt("LastHalfLength", lastHalfLength);
        }

        @Override
        public void read(@NotNull CompoundTag compound, @NotNull HolderLookup.Provider registries, boolean clientPacket) {
            super.read(compound, registries, clientPacket);
            lastHalfLength = compound.getInt("LastHalfLength");
        }

        private void clearRing(int outer, int inner) {
            if (outer <= inner) return;
            if (inner < 0) inner = -1;
            forEachInRing(outer, inner, true);
        }

        private void unclearRing(int outer, int inner) {
            if (outer <= inner) return;
            if (inner < 0) inner = -1;
            forEachInRing(outer, inner, false);
        }

        private void clearRange(int halfLength) {
            if (halfLength < 0) return;
            forEachInRing(halfLength, -1, true);
        }

        private void unclearRange(int halfLength) {
            if (halfLength < 0) return;
            forEachInRing(halfLength, -1, false);
        }

        private void forEachInRing(int outer, int inner, boolean clear) {
            Level level = getWorld();
            if (level == null || level.isClientSide()) return;
            forEachInRingStatic(level, getPos(), outer, inner, clear);
        }

        private int calculateHalfLength(float speed) {
            if (speed == 0) return -1;
            float absSpeed = Math.abs(speed);
            if (absSpeed < 64) return -1;

            int maxHalfLength = LHConfig.SERVER.orbRadius.get();
            float clamped = Math.min(absSpeed, 256f);
            float t = (clamped - 64) / (256 - 64);
            int halfLength = Math.round(t * maxHalfLength);
            return Math.min(halfLength, maxHalfLength);
        }

        private float getSpeed() {
            return HostilityAbsorberBlockEntity.this.getSpeed();
        }

        public Level getWorld() {
            return HostilityAbsorberBlockEntity.this.level;
        }

        public BlockPos getPos() {
            return HostilityAbsorberBlockEntity.this.worldPosition;
        }
    }
}
