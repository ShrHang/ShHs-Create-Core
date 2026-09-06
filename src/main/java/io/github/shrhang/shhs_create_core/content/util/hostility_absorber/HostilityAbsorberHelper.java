package io.github.shrhang.shhs_create_core.content.util.hostility_absorber;

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

    /**
     * 遍历 traverseBound 内的所有区段，但跳过那些在 excludeBound 内的区段。
     * 用于处理多轴变化的真环情况。
     */
    private static void forEachExcluding(RangeBoundary traverseBound, RangeBoundary excludeBound,
                                         Level level, BiConsumer<Level, SectionPos> action) {
        for (int x = traverseBound.minX; x <= traverseBound.maxX; x++) {
            for (int y = traverseBound.minY; y <= traverseBound.maxY; y++) {
                for (int z = traverseBound.minZ; z <= traverseBound.maxZ; z++) {
                    // 仅当区段不在 excludeBound 内时执行
                    if (x < excludeBound.minX || x > excludeBound.maxX ||
                            y < excludeBound.minY || y > excludeBound.maxY ||
                            z < excludeBound.minZ || z > excludeBound.maxZ) {
                        action.accept(level, new SectionPos(x, y, z));
                    }
                }
            }
        }
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

    // ==================== 环带遍历 ====================

    /**
     * 遍历新增环带（外扩部分），对每个区段执行给定操作。
     * 单轴变化时直接遍历AABB，多轴变化时用 forEachExcluding 处理真环。
     */
    public static void forEachAddedRing(RangeBoundary oldBound, RangeBoundary newBound,
                                        Level level, BiConsumer<Level, SectionPos> action) {
        if (oldBound == null) {
            forEachInRange(newBound, level, action);
            return;
        }

        boolean xChanged = oldBound.minX != newBound.minX || oldBound.maxX != newBound.maxX;
        boolean yChanged = oldBound.minY != newBound.minY || oldBound.maxY != newBound.maxY;
        boolean zChanged = oldBound.minZ != newBound.minZ || oldBound.maxZ != newBound.maxZ;

        int changeCount = (xChanged ? 1 : 0) + (yChanged ? 1 : 0) + (zChanged ? 1 : 0);

        if (changeCount == 1) {
            // 单轴变化：增量是AABB
            if (xChanged) {
                if (newBound.minX < oldBound.minX) {
                    forEachInAABB(newBound.minX, oldBound.minX - 1,
                            newBound.minY, newBound.maxY,
                            newBound.minZ, newBound.maxZ,
                            level, action);
                }
                if (newBound.maxX > oldBound.maxX) {
                    forEachInAABB(oldBound.maxX + 1, newBound.maxX,
                            newBound.minY, newBound.maxY,
                            newBound.minZ, newBound.maxZ,
                            level, action);
                }
            } else if (yChanged) {
                if (newBound.minY < oldBound.minY) {
                    forEachInAABB(newBound.minX, newBound.maxX,
                            newBound.minY, oldBound.minY - 1,
                            newBound.minZ, newBound.maxZ,
                            level, action);
                }
                if (newBound.maxY > oldBound.maxY) {
                    forEachInAABB(newBound.minX, newBound.maxX,
                            oldBound.maxY + 1, newBound.maxY,
                            newBound.minZ, newBound.maxZ,
                            level, action);
                }
            } else { //三个轴变化有且只有一个为真，到这里时前面两个已经为假
                if (newBound.minZ < oldBound.minZ) {
                    forEachInAABB(newBound.minX, newBound.maxX,
                            newBound.minY, newBound.maxY,
                            newBound.minZ, oldBound.minZ - 1,
                            level, action);
                }
                if (newBound.maxZ > oldBound.maxZ) {
                    forEachInAABB(newBound.minX, newBound.maxX,
                            newBound.minY, newBound.maxY,
                            oldBound.maxZ + 1, newBound.maxZ,
                            level, action);
                }
            }
        } else {
            // 多轴变化：真环，遍历新边界并排除旧边界内的区段
            forEachExcluding(newBound, oldBound, level, action);
        }
    }

    /**
     * 遍历缩减环带（内缩部分），对每个区段执行给定操作。
     * 单轴变化时直接遍历AABB，多轴变化时用 forEachExcluding 处理真环。
     */
    public static void forEachRemovedRing(RangeBoundary oldBound, RangeBoundary newBound,
                                          Level level, BiConsumer<Level, SectionPos> action) {
        if (oldBound == null || newBound == null) {
            return;
        }

        boolean xChanged = oldBound.minX != newBound.minX || oldBound.maxX != newBound.maxX;
        boolean yChanged = oldBound.minY != newBound.minY || oldBound.maxY != newBound.maxY;
        boolean zChanged = oldBound.minZ != newBound.minZ || oldBound.maxZ != newBound.maxZ;

        int changeCount = (xChanged ? 1 : 0) + (yChanged ? 1 : 0) + (zChanged ? 1 : 0);

        if (changeCount == 1) {
            // 单轴变化：缩小的部分也是AABB
            if (xChanged) {
                if (newBound.minX > oldBound.minX) {
                    forEachInAABB(oldBound.minX, newBound.minX - 1,
                            oldBound.minY, oldBound.maxY,
                            oldBound.minZ, oldBound.maxZ,
                            level, action);
                }
                if (newBound.maxX < oldBound.maxX) {
                    forEachInAABB(newBound.maxX + 1, oldBound.maxX,
                            oldBound.minY, oldBound.maxY,
                            oldBound.minZ, oldBound.maxZ,
                            level, action);
                }
            } else if (yChanged) {
                if (newBound.minY > oldBound.minY) {
                    forEachInAABB(oldBound.minX, oldBound.maxX,
                            oldBound.minY, newBound.minY - 1,
                            oldBound.minZ, oldBound.maxZ,
                            level, action);
                }
                if (newBound.maxY < oldBound.maxY) {
                    forEachInAABB(oldBound.minX, oldBound.maxX,
                            newBound.maxY + 1, oldBound.maxY,
                            oldBound.minZ, oldBound.maxZ,
                            level, action);
                }
            } else {
                if (newBound.minZ > oldBound.minZ) {
                    forEachInAABB(oldBound.minX, oldBound.maxX,
                            oldBound.minY, oldBound.maxY,
                            oldBound.minZ, newBound.minZ - 1,
                            level, action);
                }
                if (newBound.maxZ < oldBound.maxZ) {
                    forEachInAABB(oldBound.minX, oldBound.maxX,
                            oldBound.minY, oldBound.maxY,
                            newBound.maxZ + 1, oldBound.maxZ,
                            level, action);
                }
            }
        } else {
            // 多轴变化：真环，遍历旧边界并排除新边界内的区段
            forEachExcluding(oldBound, newBound, level, action);
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
                    success = section.setUnclear(chunkCap, pos);
                }
                if (success) {
                    succeeded.add(sp);
                }
            }
        }
        return succeeded;
    }
}