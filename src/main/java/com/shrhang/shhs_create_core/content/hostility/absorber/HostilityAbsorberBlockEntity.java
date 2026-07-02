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
 * 行为根据动力学转速动态调整清除范围，并在转速变化时对环带施加 clear/unclear 脉冲。
 * 范围以区块为单位，使用半边长（halfLength）表示：
 * - 半边长 = -1 表示停机（无任何清除）
 * - 半边长 = 0 表示仅中心区块（1×1×1）
 * - 半边长 > 0 表示扩展范围（2*halfLength+1 立方体）
 * 转速映射：0~64 RPM → 半边长 -1（停机），64 RPM → 半边长 0，256 RPM → 配置最大值
 * 定期清扫基于游戏刻取模（每 CLEAR_INTERVAL 刻执行一次），全服务器同步。
 */
public class HostilityAbsorberBlockEntity extends KineticBlockEntity {

    public static final BehaviourType<AbsorptionBehaviour> ABSORPTION_BEHAVIOUR = new BehaviourType<>();

    public HostilityAbsorberBlockEntity(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(@NotNull List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new AbsorptionBehaviour(this));
    }

    public class AbsorptionBehaviour extends BlockEntityBehaviour {

        private static final int CLEAR_INTERVAL = 40;
        private int lastHalfLength = -1; // 初始停机

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

            float speed = getSpeed();
            int currentHalfLength = calculateHalfLength(speed);

            // 范围变化时应用脉冲
            if (currentHalfLength != lastHalfLength) {
                if (currentHalfLength > lastHalfLength) {
                    if (lastHalfLength < 0 && currentHalfLength >= 0) {
                        // 从停机进入工作状态，清除整个范围（包括中心）
                        clearRange(currentHalfLength);
                    } else {
                        // 扩大：清除新增环带
                        clearRing(currentHalfLength, lastHalfLength);
                    }
                } else {
                    if (currentHalfLength < 0 && lastHalfLength >= 0) {
                        // 从工作进入停机，还原整个范围
                        unclearRange(lastHalfLength);
                    } else {
                        // 缩小：还原缩小的环带
                        unclearRing(lastHalfLength, currentHalfLength);
                    }
                }
                lastHalfLength = currentHalfLength;
            }

            // 定期清扫当前范围（仅当工作状态，即 halfLength >= 0）
            // 使用游戏刻取模，全服务器同步
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

        // ==================== 核心脉冲方法 ====================

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

            BlockPos center = getPos();

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

        /**
         * 计算半边长：
         * - 转速 < 64 RPM → 返回 -1（停机）
         * - 64 RPM → 返回 0（中心区块）
         * - 64~256 RPM 线性插值到 [0, maxHalfLength]
         */
        private int calculateHalfLength(float speed) {
            if (speed == 0) return -1;
            float absSpeed = Math.abs(speed);
            if (absSpeed < 64) return -1;

            int maxHalfLength = LHConfig.SERVER.orbRadius.get();
            float clamped = Math.min(absSpeed, 256f);
            float t = (clamped - 64) / (256 - 64); // 0~1
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