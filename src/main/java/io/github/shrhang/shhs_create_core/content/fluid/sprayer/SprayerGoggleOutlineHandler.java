package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.createmod.catnip.outliner.Outliner;
import io.github.shrhang.shhs_create_core.content.util.SprayHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * 佩戴护目镜且手持喷洒器或扳手时：
 * - 若注视已放置的喷洒器且其角度 > 0，显示实际喷洒范围（绿色框）。
 * - 若注视的喷洒器角度为 0（关闭），仅闪红一次（红色框自动淡出），不显示绿色框。
 * - 若手持喷洒器并看向有效放置位置，显示最大范围预览（橙色框），且该位置必须可放置方块。
 * 最大观察距离为玩家可及距离 + 4。
 */
public class SprayerGoggleOutlineHandler {

    private static final Object ORANGE_SLOT = new Object();
    private static final Object GREEN_SLOT = new Object();
    private static final Object RED_SLOT = new Object();
    private static final int ORANGE_COLOR = 0xFFFF8800;
    private static final int GREEN_COLOR = 0x00FF00;
    private static final int RED_COLOR = 0xFF0000;
    private static final float EPSILON = 1e-6f;

    private final Set<BlockPos> flashedRed = new HashSet<>();

    @Nullable
    private BlockPos lastOrangePos;
    @Nullable
    private Direction lastOrangeFacing;
    @Nullable
    private BlockPos lastGreenPos;
    @Nullable
    private Float lastGreenAngle;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            clearAll();
            return;
        }

        Level level = mc.level;
        if (level == null) {
            clearAll();
            return;
        }

        if (!GogglesItem.isWearingGoggles(player)) {
            clearAll();
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        boolean holdingSprayer = !mainHand.isEmpty() && mainHand.getItem() instanceof BlockItem &&
                ((BlockItem) mainHand.getItem()).getBlock() instanceof SprayerBlock;
        boolean holdingWrench = AllItems.WRENCH.isIn(mainHand);
        if (!holdingSprayer && !holdingWrench) {
            clearAll();
            return;
        }

        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        double maxDist = reach + 4.0;

        HitResult hitResult = player.pick(maxDist, 1.0f, false);
        boolean hitBlock = hitResult instanceof BlockHitResult;
        BlockHitResult blockHit = hitBlock ? (BlockHitResult) hitResult : null;

        // 处理注视的喷洒器（绿色/红色框）
        boolean sprayerRendered = false;
        if (hitBlock && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SprayerBlockEntity sprayer) {
                float angle = sprayer.getAngle();
                float ratio = angle / SprayerBlockEntity.MAX_ANGLE;

                if (angle <= EPSILON) {
                    // 关闭状态：闪红一次
                    if (!flashedRed.contains(pos)) {
                        flashedRed.add(pos);
                        Direction facing = sprayer.getBlockState().getValue(SprayerBlock.FACING);
                        Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
                        AABB aabb = SprayHelper.buildAABB(center, facing, 1.0f);
                        Outliner.getInstance().showAABB(RED_SLOT, aabb)
                                .colored(RED_COLOR)
                                .lineWidth(1 / 16f)
                                .withFaceTextures(null, null)
                                .disableLineNormals();
                    }
                    Outliner.getInstance().remove(GREEN_SLOT);
                    lastGreenPos = null;
                    lastGreenAngle = null;
                    sprayerRendered = true;
                } else {
                    // 运行状态：绘制绿色框
                    flashedRed.remove(pos);
                    Direction facing = sprayer.getBlockState().getValue(SprayerBlock.FACING);
                    Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
                    AABB aabb = SprayHelper.buildAABB(center, facing, ratio);

                    if (!pos.equals(lastGreenPos) || !Float.valueOf(angle).equals(lastGreenAngle)) {
                        lastGreenPos = pos;
                        lastGreenAngle = angle;
                    }
                    Outliner.getInstance().showAABB(GREEN_SLOT, aabb)
                            .colored(GREEN_COLOR)
                            .lineWidth(1 / 16f)
                            .withFaceTextures(null, null)
                            .disableLineNormals();
                    sprayerRendered = true;
                }
            }
        }

        if (!sprayerRendered) {
            Outliner.getInstance().remove(GREEN_SLOT);
            lastGreenPos = null;
            lastGreenAngle = null;
            flashedRed.clear();
        }

        // 处理手持喷洒器预览（橙色框）
        boolean orangeRendered = false;
        if (holdingSprayer && hitBlock && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, mainHand, blockHit);
            if (context.canPlace()) {
                BlockState state = ((SprayerBlock) ((BlockItem) mainHand.getItem()).getBlock())
                        .getStateForPlacement(context);
                if (state != null) {
                    BlockPos placePos = blockHit.getBlockPos().relative(blockHit.getDirection());
                    Direction facing = state.getValue(SprayerBlock.FACING);
                    Vec3 center = Vec3.atCenterOf(placePos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
                    float ratio = 1.0f;
                    AABB aabb = SprayHelper.buildAABB(center, facing, ratio);

                    if (!placePos.equals(lastOrangePos) || facing != lastOrangeFacing) {
                        lastOrangePos = placePos;
                        lastOrangeFacing = facing;
                    }
                    Outliner.getInstance().showAABB(ORANGE_SLOT, aabb)
                            .colored(ORANGE_COLOR)
                            .lineWidth(1 / 16f)
                            .withFaceTextures(null, null)
                            .disableLineNormals();
                    orangeRendered = true;
                }
            }
        }

        if (!orangeRendered) {
            Outliner.getInstance().remove(ORANGE_SLOT);
            lastOrangePos = null;
            lastOrangeFacing = null;
        }
    }

    private void clearAll() {
        Outliner.getInstance().remove(ORANGE_SLOT);
        Outliner.getInstance().remove(GREEN_SLOT);
        lastOrangePos = null;
        lastOrangeFacing = null;
        lastGreenPos = null;
        lastGreenAngle = null;
        flashedRed.clear();
    }
}