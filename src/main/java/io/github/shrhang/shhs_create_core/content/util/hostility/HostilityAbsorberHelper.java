package io.github.shrhang.shhs_create_core.content.util.hostility;

import dev.xkmc.l2hostility.content.capability.chunk.ChunkCapHolder;
import dev.xkmc.l2hostility.content.capability.chunk.ChunkDifficulty;
import dev.xkmc.l2hostility.content.capability.chunk.SectionDifficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * 恶意吸收器区段操作工具类。
 * <p>
 * 提供基于轴向边界（RangeBoundary）的范围计算、环带遍历和批量清理/恢复能力。
 * 所有方法均为纯函数或静态无状态操作，不持有任何外部上下文。
 */
public final class HostilityAbsorberHelper {

    private HostilityAbsorberHelper() {
        // 工具类禁止实例化
    }

    /**
     * 区段坐标记录，用于标识世界中的一个 16x16x16 区块区段。
     */
    public record SectionPos(int x, int y, int z) {
        public BlockPos toBlockPos() {
            return new BlockPos(x << 4, y << 4, z << 4);
        }
    }

    /**
     * 轴向边界记录，表示一个完整的AABB区段范围。
     */
    public record RangeBoundary(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public boolean contains(SectionPos pos) {
            return pos.x() >= minX && pos.x() <= maxX &&
                    pos.y() >= minY && pos.y() <= maxY &&
                    pos.z() >= minZ && pos.z() <= maxZ;
        }
    }

    // ==================== 底层遍历工具 ====================

    /**
     * 遍历指定的AABB区段范围，对每个区段执行操作。
     */
    private static void forEachInAABB(int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                      Level level, BiConsumer<Level, SectionPos> action) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    action.accept(level, new SectionPos(x, y, z));
                }
            }
        }
    }

    /**
     * 遍历边界内所有区段，对每个区段执行操作。
     */
    public static void forEachInRange(RangeBoundary bound, Level level, BiConsumer<Level, SectionPos> action) {
        forEachInAABB(bound.minX, bound.maxX, bound.minY, bound.maxY, bound.minZ, bound.maxZ, level, action);
    }

    // ==================== 边界计算 ====================

    /**
     * 计算给定中心、半径和世界高度范围内的有效区段边界。
     * Y轴将被钳制到世界高度范围内。
     */
    @Nullable
    public static RangeBoundary computeBoundary(BlockPos center, int halfLength, Level level) {
        if (halfLength < 0) {
            return null;
        }

        int cx = center.getX() >> 4;
        int cy = center.getY() >> 4;
        int cz = center.getZ() >> 4;

        int minY = Math.max(cy - halfLength, level.getMinSection());
        int maxY = Math.min(cy + halfLength, level.getMaxSection() - 1);

        if (minY > maxY) {
            return null;
        }

        return new RangeBoundary(
                cx - halfLength, cx + halfLength,
                minY, maxY,
                cz - halfLength, cz + halfLength
        );
    }

    // ==================== 环带遍历（顺序单轴增量） ====================

    /**
     * 遍历新增环带（外扩部分），对每个区段执行给定操作。
     * 采用顺序单轴增量，依次处理 X、Y、Z 轴的扩增条带，避免全量遍历。
     */
    public static void forEachAddedRing(RangeBoundary oldBound, RangeBoundary newBound,
                                        Level level, BiConsumer<Level, SectionPos> action) {
        if (oldBound == null) {
            forEachInRange(newBound, level, action);
            return;
        }

        // 当前边界初始为旧边界，逐步更新
        int curMinX = oldBound.minX, curMaxX = oldBound.maxX;
        int curMinY = oldBound.minY, curMaxY = oldBound.maxY;
        int curMinZ = oldBound.minZ, curMaxZ = oldBound.maxZ;

        // ---- X轴扩增 ----
        if (newBound.minX < curMinX) {
            forEachInAABB(newBound.minX, curMinX - 1,
                    curMinY, curMaxY,
                    curMinZ, curMaxZ,
                    level, action);
            curMinX = newBound.minX;
        }
        if (newBound.maxX > curMaxX) {
            forEachInAABB(curMaxX + 1, newBound.maxX,
                    curMinY, curMaxY,
                    curMinZ, curMaxZ,
                    level, action);
            curMaxX = newBound.maxX;
        }

        // ---- Y轴扩增 ----
        if (newBound.minY < curMinY) {
            forEachInAABB(curMinX, curMaxX,
                    newBound.minY, curMinY - 1,
                    curMinZ, curMaxZ,
                    level, action);
            curMinY = newBound.minY;
        }
        if (newBound.maxY > curMaxY) {
            forEachInAABB(curMinX, curMaxX,
                    curMaxY + 1, newBound.maxY,
                    curMinZ, curMaxZ,
                    level, action);
            curMaxY = newBound.maxY;
        }

        // ---- Z轴扩增 ----
        if (newBound.minZ < curMinZ) {
            forEachInAABB(curMinX, curMaxX,
                    curMinY, curMaxY,
                    newBound.minZ, curMinZ - 1,
                    level, action);
            // 无需更新后续，因为Z是最后一轴
        }
        if (newBound.maxZ > curMaxZ) {
            forEachInAABB(curMinX, curMaxX,
                    curMinY, curMaxY,
                    curMaxZ + 1, newBound.maxZ,
                    level, action);
        }
    }

    /**
     * 遍历缩减环带（内缩部分），对每个区段执行给定操作。
     * 采用顺序单轴增量，依次处理 X、Y、Z 轴的收缩条带，避免全量遍历。
     */
    public static void forEachRemovedRing(RangeBoundary oldBound, RangeBoundary newBound,
                                          Level level, BiConsumer<Level, SectionPos> action) {
        if (oldBound == null || newBound == null) {
            return;
        }

        int curMinX = oldBound.minX, curMaxX = oldBound.maxX;
        int curMinY = oldBound.minY, curMaxY = oldBound.maxY;
        int curMinZ = oldBound.minZ, curMaxZ = oldBound.maxZ;

        // ---- X轴收缩 ----
        if (newBound.minX > curMinX) {
            forEachInAABB(curMinX, newBound.minX - 1,
                    curMinY, curMaxY,
                    curMinZ, curMaxZ,
                    level, action);
            curMinX = newBound.minX;
        }
        if (newBound.maxX < curMaxX) {
            forEachInAABB(newBound.maxX + 1, curMaxX,
                    curMinY, curMaxY,
                    curMinZ, curMaxZ,
                    level, action);
            curMaxX = newBound.maxX;
        }

        // ---- Y轴收缩 ----
        if (newBound.minY > curMinY) {
            forEachInAABB(curMinX, curMaxX,
                    curMinY, newBound.minY - 1,
                    curMinZ, curMaxZ,
                    level, action);
            curMinY = newBound.minY;
        }
        if (newBound.maxY < curMaxY) {
            forEachInAABB(curMinX, curMaxX,
                    newBound.maxY + 1, curMaxY,
                    curMinZ, curMaxZ,
                    level, action);
            curMaxY = newBound.maxY;
        }

        // ---- Z轴收缩 ----
        if (newBound.minZ > curMinZ) {
            forEachInAABB(curMinX, curMaxX,
                    curMinY, curMaxY,
                    curMinZ, newBound.minZ - 1,
                    level, action);
            // 无需更新，最后一轴
        }
        if (newBound.maxZ < curMaxZ) {
            forEachInAABB(curMinX, curMaxX,
                    curMinY, curMaxY,
                    newBound.maxZ + 1, curMaxZ,
                    level, action);
        }
    }

    // ==================== 批量操作（复用原有逻辑） ====================

    /**
     * 对指定区段集合执行清理操作（setClear），并返回成功清理的区段。
     */
    public static Set<SectionPos> clearSections(Level level, Collection<SectionPos> sections) {
        if (level.isClientSide() || sections.isEmpty()) {
            return Collections.emptySet();
        }
        return applyToSections(level, sections, true);
    }

    /**
     * 对指定区段集合执行恢复操作（setUnclear），并返回成功恢复的区段。
     */
    public static Set<SectionPos> unclearSections(Level level, Collection<SectionPos> sections) {
        if (level.isClientSide() || sections.isEmpty()) {
            return Collections.emptySet();
        }
        return applyToSections(level, sections, false);
    }

    /**
     * 对区段集合执行批量 apply 操作的核心方法。
     * 对于 unclear 操作：若区段已是 INIT，视为成功；若为 CLEARED，调用 setUnclear 并返回结果。
     */
    private static Set<SectionPos> applyToSections(Level level, Collection<SectionPos> sections, boolean clear) {
        Set<SectionPos> succeeded = new HashSet<>();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        Map<Long, List<SectionPos>> grouped = new HashMap<>();
        for (SectionPos pos : sections) {
            long key = ((long) pos.x() << 32) | (pos.z() & 0xFFFFFFFFL);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(pos);
        }
        for (Map.Entry<Long, List<SectionPos>> entry : grouped.entrySet()) {
            long key = entry.getKey();
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            Optional<ChunkCapHolder> optional = ChunkDifficulty.at(level, chunkX, chunkZ);
            if (optional.isEmpty()) {
                continue;
            }
            ChunkCapHolder chunkCap = optional.get();
            for (SectionPos sp : entry.getValue()) {
                int y = sp.y() << 4;
                if (y < minY || y >= maxY) {
                    continue;
                }
                SectionDifficulty section = chunkCap.getSection(y);
                BlockPos pos = sp.toBlockPos();
                boolean success;
                if (clear) {
                    success = section.setClear(chunkCap, pos);
                } else {
                    // 若已是 INIT（即 !isCleared()），无需操作，视为成功；
                    // 若为 CLEARED，调用 setUnclear 使其变为 INIT。
                    if (section.isCleared()) {
                        success = section.setUnclear(chunkCap, pos);
                    } else {
                        success = true; // 已是 INIT，直接成功
                    }
                }
                if (success) {
                    succeeded.add(sp);
                }
            }
        }
        return succeeded;
    }
}