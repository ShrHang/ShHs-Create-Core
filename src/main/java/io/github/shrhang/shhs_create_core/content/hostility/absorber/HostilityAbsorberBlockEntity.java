package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.xkmc.l2hostility.init.data.LHConfig;
import io.github.shrhang.shhs_create_core.content.util.hostility.HostilityAbsorberHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import static io.github.shrhang.shhs_create_core.content.util.hostility.HostilityAbsorberHelper.RangeBoundary;
import static io.github.shrhang.shhs_create_core.content.util.hostility.HostilityAbsorberHelper.SectionPos;

/**
 * 恶意吸收器方块实体。
 * <p>
 * 使用轴向边界（RangeBoundary）存储理想范围，并维护两个异常集合
 * （failedClear / failedUnclear）处理未能当场操作的区段。
 * 边界更新时直接执行 clear / unclear，失败的区段进入异常集合。
 * 清除修复扫描只针对 failedClear 中的区段进行尝试，成功则移除。
 * 恢复操作忽略 failedClear 中的区段（这些区段不属于该吸收器）。
 * 边界收缩时，移出范围的 failedClear 区段会被清除。
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

        private RangeBoundary boundary;
        // 清除异常集合：边界更新时清理失败的区段
        private final Set<SectionPos> failedClear = new LinkedHashSet<>();
        // 恢复异常集合：边界缩减时恢复失败的区段
        private final Set<SectionPos> failedUnclear = new LinkedHashSet<>();
        // 清除扫描迭代器（运行时游标）
        private Iterator<SectionPos> clearIterator;
        // 恢复扫描迭代器（运行时游标）
        private Iterator<SectionPos> unclearIterator;

        public AbsorptionBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public BehaviourType<?> getType() {
            return ABSORPTION_BEHAVIOUR;
        }

        @Override
        public void initialize() {
            super.initialize();
            if (getWorld() == null || getWorld().isClientSide()) {
                return;
            }
            if (boundary == null) {
                float speed = getSpeed();
                int halfLength = calculateHalfLength(speed);
                RangeBoundary target = HostilityAbsorberHelper.computeBoundary(getPos(), halfLength, getWorld());
                if (target != null) {
                    updateBoundary(getWorld(), null, target);
                }
            }
        }

        @Override
        public void tick() {
            super.tick();
            Level level = getWorld();
            if (level == null || level.isClientSide()) {
                return;
            }

            // 委托子方法处理边界更新和修复
            updateBoundaryIfNeeded(level);
            processClearRepair(level);
            processUnclearRepair(level);
        }

        // ==================== 边界更新委托 ====================

        /**
         * 根据当前转速计算目标边界，若变化则更新。
         */
        private void updateBoundaryIfNeeded(Level level) {
            float speed = getSpeed();
            int currentHalfLength = calculateHalfLength(speed);
            RangeBoundary target = HostilityAbsorberHelper.computeBoundary(getPos(), currentHalfLength, level);

            if (target != null) {
                if (!target.equals(boundary)) {
                    updateBoundary(level, boundary, target);
                }
            } else {
                if (boundary != null) {
                    releaseAll(level);
                }
            }
        }

        // ==================== 迭代器管理 ====================

        /**
         * 重置清除修复迭代器。
         */
        private void resetClearIterator() {
            clearIterator = null;
        }

        /**
         * 重置恢复修复迭代器。
         */
        private void resetUnclearIterator() {
            unclearIterator = null;
        }

        /**
         * 重置所有迭代器。
         */
        private void resetAllIterators() {
            clearIterator = null;
            unclearIterator = null;
        }

        /**
         * 获取清除修复迭代器，若为空或无下一个则重新创建。
         *
         * @return 迭代器
         */
        private Iterator<SectionPos> getClearIterator() {
            if (clearIterator == null || !clearIterator.hasNext()) {
                clearIterator = failedClear.iterator();
            }
            return clearIterator;
        }

        /**
         * 获取恢复修复迭代器，若为空或无下一个则重新创建。
         *
         * @return 迭代器
         */
        private Iterator<SectionPos> getUnclearIterator() {
            if (unclearIterator == null || !unclearIterator.hasNext()) {
                unclearIterator = failedUnclear.iterator();
            }
            return unclearIterator;
        }

        // ==================== 边界管理 ====================

        /**
         * 更新边界。
         * <p>
         * - 若 oldBound 为 null（即从 0 边界扩增）：对整个 newBound 执行 clear，失败的加入 failedClear。
         * - 若 oldBound 非 null：扩增环带 clear，缩减环带 unclear。
         */
        private void updateBoundary(Level level, RangeBoundary oldBound, RangeBoundary newBound) {
            if (oldBound == null) {
                // 从 0 边界扩增到 newBound
                HostilityAbsorberHelper.forEachInRange(newBound, level, (l, pos) -> {
                    Set<SectionPos> result = HostilityAbsorberHelper.clearSections(l, Set.of(pos));
                    if (result.isEmpty()) {
                        failedClear.add(pos);
                        resetClearIterator();
                    }
                });
                boundary = newBound;
                resetAllIterators();
                return;
            }
            // 扩增环带：直接 clear，失败的加入 failedClear
            HostilityAbsorberHelper.forEachAddedRing(oldBound, newBound, level, (l, pos) -> {
                Set<SectionPos> result = HostilityAbsorberHelper.clearSections(l, Set.of(pos));
                if (result.isEmpty()) {
                    failedClear.add(pos);
                    resetClearIterator();
                }
            });
            // 缩减环带：移出作用范围的区段
            HostilityAbsorberHelper.forEachRemovedRing(oldBound, newBound, level, (l, pos) -> {
                if (failedClear.contains(pos)) {
                    failedClear.remove(pos);
                    resetClearIterator();
                    return;
                }
                Set<SectionPos> result = HostilityAbsorberHelper.unclearSections(l, Set.of(pos));
                if (result.isEmpty()) {
                    failedUnclear.add(pos);
                    resetUnclearIterator();
                }
            });
            boundary = newBound;
            failedUnclear.removeIf(pos -> boundary.contains(pos));
            resetAllIterators();
        }

        /**
         * 停转时释放全部持有。
         * <p>
         * - failedClear：清空（这些区段从未被此吸收器成功持有，停转后无需追踪）
         * - failedUnclear：保留（这些区段需要继续尝试恢复）
         */
        private void releaseAll(Level level) {
            if (boundary != null) {
                HostilityAbsorberHelper.forEachInRange(boundary, level, (l, pos) -> {
                    if (failedClear.contains(pos)) {
                        return;
                    }
                    Set<SectionPos> result = HostilityAbsorberHelper.unclearSections(l, Set.of(pos));
                    if (result.isEmpty()) {
                        failedUnclear.add(pos);
                        resetUnclearIterator();
                    }
                });
            }
            boundary = null;
            failedClear.clear();
            resetClearIterator();
        }

        // ==================== 清除修复扫描 ====================

        /**
         * 清除修复扫描：只针对 failedClear 集合中的区段进行清除尝试。
         * <p>
         * 每次处理集合大小的 1/40（至少 1 个），成功清除的区段从集合中移除。
         */
        private void processClearRepair(Level level) {
            if (failedClear.isEmpty()) {
                clearIterator = null;
                return;
            }

            int size = failedClear.size();
            int toProcess = Math.max(1, size / 40);
            Iterator<SectionPos> iterator = getClearIterator();
            int processed = 0;

            while (processed < toProcess && iterator.hasNext()) {
                SectionPos pos = iterator.next();
                Set<SectionPos> result = HostilityAbsorberHelper.clearSections(level, Set.of(pos));
                if (!result.isEmpty()) {
                    iterator.remove();
                }
                processed++;
            }

            if (!iterator.hasNext()) {
                clearIterator = null;
            }
        }

        // ==================== 恢复修复扫描 ====================

        /**
         * 恢复修复扫描：平摊处理 failedUnclear 集合。
         * <p>
         * 每次处理集合大小的 1/40（至少 1 个）。
         */
        private void processUnclearRepair(Level level) {
            if (failedUnclear.isEmpty()) {
                unclearIterator = null;
                return;
            }

            failedUnclear.removeIf(failedClear::contains);

            if (failedUnclear.isEmpty()) {
                unclearIterator = null;
                return;
            }

            int size = failedUnclear.size();
            int toProcess = Math.max(1, size / 40);
            Iterator<SectionPos> iterator = getUnclearIterator();
            int processed = 0;

            while (processed < toProcess && iterator.hasNext()) {
                SectionPos pos = iterator.next();

                if (boundary != null && boundary.contains(pos)) {
                    iterator.remove();
                } else {
                    Set<SectionPos> result = HostilityAbsorberHelper.unclearSections(level, Set.of(pos));
                    if (!result.isEmpty()) {
                        iterator.remove();
                    }
                }
                processed++;
            }

            if (!iterator.hasNext()) {
                unclearIterator = null;
            }
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

            resetAllIterators();
        }

        /**
         * 将区段集合写入 NBT。
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

        /**
         * 从 NBT 读取区段集合。
         */
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
         * 根据转速计算半边长。
         *
         * @param speed 转速
         * @return 半边长，若无效则返回 -1
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

        /**
         * 获取方块实体的转速。
         *
         * @return 转速
         */
        private float getSpeed() {
            return HostilityAbsorberBlockEntity.this.getSpeed();
        }

        /**
         * 获取世界。
         *
         * @return 世界实例
         */
        public Level getWorld() {
            return HostilityAbsorberBlockEntity.this.level;
        }

        /**
         * 获取方块位置。
         *
         * @return 方块位置
         */
        public BlockPos getPos() {
            return HostilityAbsorberBlockEntity.this.worldPosition;
        }
    }
}