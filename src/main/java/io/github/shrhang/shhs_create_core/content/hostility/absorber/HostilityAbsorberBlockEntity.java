package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.xkmc.l2hostility.init.data.LHConfig;
import io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper.RangeBoundary;
import static io.github.shrhang.shhs_create_core.content.util.hostility_absorber.HostilityAbsorberHelper.SectionPos;

/**
 * 恶意吸收器方块实体。
 * <p>
 * 使用轴向边界（RangeBoundary）存储理想范围，并维护两个异常集合
 * （failedClear / failedUnclear）处理未能当场操作的区段。
 * 范围变化时通过环带增量更新，避免重建完整集合。
 */
public class HostilityAbsorberBlockEntity extends KineticBlockEntity {

    public static final BehaviourType<AbsorptionBehaviour> ABSORPTION_BEHAVIOUR = new BehaviourType<>();

    public HostilityAbsorberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new AbsorptionBehaviour(this));
    }

    public class AbsorptionBehaviour extends BlockEntityBehaviour {

        private static final int CLEAR_INTERVAL = 40;

        private RangeBoundary boundary;
        private final Set<SectionPos> failedClear = new HashSet<>();
        private final Set<SectionPos> failedUnclear = new HashSet<>();

        public AbsorptionBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public BehaviourType<?> getType() {
            return ABSORPTION_BEHAVIOUR;
        }

        @Override
        public void tick() {
            super.tick();

            Level level = getWorld();
            if (level == null || level.isClientSide()) {
                return;
            }

            float speed = getSpeed();
            int currentHalfLength = calculateHalfLength(speed);
            RangeBoundary target = HostilityAbsorberHelper.computeBoundary(getPos(), currentHalfLength, level);

            if (target != null) {
                if (boundary == null) {
                    initializeBoundary(level, target);
                } else if (!boundary.equals(target)) {
                    updateBoundary(level, boundary, target);
                }

                if (level.getGameTime() % CLEAR_INTERVAL == 0) {
                    refreshBoundary(level, boundary);
                }
            } else {
                if (boundary != null) {
                    releaseAll(level);
                }
            }

            processFailedClear(level);
            processFailedUnclear(level);
        }

        /**
         * 首次初始化边界，清理整个范围，并将失败的区段加入 failedClear。
         */
        private void initializeBoundary(Level level, RangeBoundary target) {
            boundary = target;
            HostilityAbsorberHelper.forEachInRange(target, level, (l, pos) -> {
                Set<SectionPos> result = HostilityAbsorberHelper.clearSections(l, Set.of(pos));
                if (result.isEmpty()) {
                    failedClear.add(pos);
                }
            });
        }

        /**
         * 边界变化时，分别处理新增环带和缩减环带。
         */
        private void updateBoundary(Level level, RangeBoundary oldBound, RangeBoundary newBound) {
            HostilityAbsorberHelper.forEachAddedRing(oldBound, newBound, level, (l, pos) -> {
                Set<SectionPos> result = HostilityAbsorberHelper.clearSections(l, Set.of(pos));
                if (result.isEmpty()) {
                    failedClear.add(pos);
                }
            });

            HostilityAbsorberHelper.forEachRemovedRing(oldBound, newBound, level, (l, pos) -> {
                Set<SectionPos> result = HostilityAbsorberHelper.unclearSections(l, Set.of(pos));
                if (result.isEmpty()) {
                    failedUnclear.add(pos);
                }
            });

            boundary = newBound;
            failedUnclear.removeIf(pos -> boundary.contains(pos));
        }

        /**
         * 释放全部持有：对整个旧边界执行 unclear，并清空异常集合。
         */
        private void releaseAll(Level level) {
            if (boundary != null) {
                HostilityAbsorberHelper.forEachInRange(boundary, level, (l, pos) ->
                        HostilityAbsorberHelper.unclearSections(l, Set.of(pos))
                );
            }
            boundary = null;
            failedClear.clear();
            failedUnclear.clear();
        }

        /**
         * 周期刷新：对边界内所有区段重新执行 clear，并处理异常集合。
         */
        private void refreshBoundary(Level level, RangeBoundary currentBound) {
            HostilityAbsorberHelper.forEachInRange(currentBound, level, (l, pos) -> {
                Set<SectionPos> result = HostilityAbsorberHelper.clearSections(l, Set.of(pos));
                if (result.isEmpty()) {
                    failedClear.add(pos);
                }
            });
        }

        /**
         * 处理 failedClear 集合：重新尝试清理，成功则移除。
         */
        private void processFailedClear(Level level) {
            if (failedClear.isEmpty()) {
                return;
            }
            Set<SectionPos> toRemove = new HashSet<>();
            for (SectionPos pos : failedClear) {
                if (boundary != null && boundary.contains(pos)) {
                    Set<SectionPos> result = HostilityAbsorberHelper.clearSections(level, Set.of(pos));
                    if (!result.isEmpty()) {
                        toRemove.add(pos);
                    }
                } else {
                    toRemove.add(pos);
                }
            }
            failedClear.removeAll(toRemove);
        }

        /**
         * 处理 failedUnclear 集合：重新尝试恢复，成功则移除；若区段重新落入边界内，则移除。
         */
        private void processFailedUnclear(Level level) {
            if (failedUnclear.isEmpty()) {
                return;
            }
            Set<SectionPos> toRemove = new HashSet<>();
            for (SectionPos pos : failedUnclear) {
                if (boundary != null && boundary.contains(pos)) {
                    toRemove.add(pos);
                } else {
                    Set<SectionPos> result = HostilityAbsorberHelper.unclearSections(level, Set.of(pos));
                    if (!result.isEmpty()) {
                        toRemove.add(pos);
                    }
                }
            }
            failedUnclear.removeAll(toRemove);
        }

        @Override
        public void destroy() {
            Level level = getWorld();
            if (level != null && !level.isClientSide() && boundary != null) {
                releaseAll(level);
            }
            super.destroy();
        }

        // ========== 序列化 ==========

        @Override
        public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
            super.write(compound, registries, clientPacket);

            if (boundary != null) {
                compound.putInt("MinX", boundary.minX());
                compound.putInt("MaxX", boundary.maxX());
                compound.putInt("MinY", boundary.minY());
                compound.putInt("MaxY", boundary.maxY());
                compound.putInt("MinZ", boundary.minZ());
                compound.putInt("MaxZ", boundary.maxZ());
            }

            writeSectionSet(compound, "FailedClear", failedClear);
            writeSectionSet(compound, "FailedUnclear", failedUnclear);
        }

        @Override
        public void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
            super.read(compound, registries, clientPacket);

            if (compound.contains("MinX")) {
                boundary = new RangeBoundary(
                        compound.getInt("MinX"),
                        compound.getInt("MaxX"),
                        compound.getInt("MinY"),
                        compound.getInt("MaxY"),
                        compound.getInt("MinZ"),
                        compound.getInt("MaxZ")
                );
            } else {
                boundary = null;
            }

            readSectionSet(compound, "FailedClear", failedClear);
            readSectionSet(compound, "FailedUnclear", failedUnclear);
        }

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

        private void readSectionSet(CompoundTag compound, String key, Set<SectionPos> set) {
            set.clear();
            ListTag list = compound.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                set.add(new SectionPos(
                        entry.getInt("X"),
                        entry.getInt("Y"),
                        entry.getInt("Z")
                ));
            }
        }

        // ========== 辅助方法 ==========

        /**
         * 根据转速计算有效半径（区块数）。
         */
        private int calculateHalfLength(float speed) {
            if (speed == 0) {
                return -1;
            }
            float absSpeed = Math.abs(speed);
            if (absSpeed < 64) {
                return -1;
            }

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