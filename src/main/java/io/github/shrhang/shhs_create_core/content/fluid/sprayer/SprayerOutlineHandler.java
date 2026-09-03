package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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

import java.util.HashSet;
import java.util.Set;

public class SprayerOutlineHandler {

    private static final Object PREVIEW_SLOT = new Object();
    private static final Object ACTUAL_SLOT = new Object();
    private static final int GREY_COLOR = 0xa0a0a0;
    private static final int CYAN_COLOR = 0x00d4a8;
    private static final int RED_COLOR = 0xa43e3e;
    private static final float EPSILON = 1e-6f;

    private static final Set<BlockPos> FLASHED_RED = new HashSet<>();
    private static OutlineState previewState;
    private static OutlineState actualState;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            clearAll();
            return;
        }

        Level level = mc.level;
        if (level == null) {
            clearAll();
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        SprayerBlock heldSprayerBlock = getHeldSprayerBlock(mainHand);
        boolean holdingSprayer = heldSprayerBlock != null;
        boolean holdingWrench = AllItems.WRENCH.isIn(mainHand);
        if (!holdingSprayer && !holdingWrench) {
            clearAll();
            return;
        }

        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);

        HitResult hitResult = player.pick(reach, 1.0f, false);
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            removeSprayerOutline();
            removePlacementPreview();
            return;
        }

        boolean sprayerRendered = holdingWrench && renderLookedAtSprayer(level, blockHit);
        boolean previewRendered = holdingSprayer && renderPlacementPreview(player, mainHand, heldSprayerBlock, blockHit);

        if (!sprayerRendered) {
            removeSprayerOutline();
        }

        if (!previewRendered) {
            removePlacementPreview();
        }
    }

    private static SprayerBlock getHeldSprayerBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        return blockItem.getBlock() instanceof SprayerBlock sprayerBlock ? sprayerBlock : null;
    }

    private static boolean renderLookedAtSprayer(Level level, BlockHitResult blockHit) {
        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SprayerBlockEntity sprayer)) {
            return false;
        }

        float angle = sprayer.getAngle();
        Direction facing = sprayer.getBlockState().getValue(SprayerBlock.FACING);

        if (angle <= EPSILON) {
            if (!FLASHED_RED.contains(pos)) {
                FLASHED_RED.add(pos);
                AABB aabb = SprayerHelper.buildAABB(pos, facing, 1.0f);
                showSprayAABB(ACTUAL_SLOT, aabb, RED_COLOR);
            }
            return true;
        }

        FLASHED_RED.remove(pos);
        AABB aabb = SprayerHelper.buildAABBFromAngle(pos, facing, angle, SprayerBlockEntity.MAX_ANGLE);
        showSprayAABB(ACTUAL_SLOT, aabb, CYAN_COLOR);
        return true;
    }

    private static boolean renderPlacementPreview(Player player, ItemStack stack, SprayerBlock sprayerBlock,
                                                  BlockHitResult blockHit) {
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, blockHit);
        if (!context.canPlace()) {
            return false;
        }

        BlockState state = sprayerBlock.getStateForPlacement(context);
        if (state == null) {
            return false;
        }

        BlockPos placePos = context.getClickedPos();
        Direction facing = state.getValue(SprayerBlock.FACING);
        AABB aabb = SprayerHelper.buildAABB(placePos, facing, 1.0f);
        showSprayAABB(PREVIEW_SLOT, aabb, GREY_COLOR);
        return true;
    }

    private static void removeSprayerOutline() {
        removeOutline(ACTUAL_SLOT);
        FLASHED_RED.clear();
    }

    private static void removePlacementPreview() {
        removeOutline(PREVIEW_SLOT);
    }

    private static void clearAll() {
        removePlacementPreview();
        removeSprayerOutline();
    }

    private static void showSprayAABB(Object slot, AABB aabb, int color) {
        OutlineState state = getCachedState(slot);
        if (state != null && state.color == color && aabbEquals(state.aabb, aabb)) {
            Outliner.getInstance().keep(slot);
            return;
        }

        setCachedState(slot, new OutlineState(aabb, color));
        Outliner.getInstance().chaseAABB(slot, aabb)
                .colored(color)
                .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                .lineWidth(1 / 16f);
    }

    private static OutlineState getCachedState(Object slot) {
        if (slot == PREVIEW_SLOT) return previewState;
        if (slot == ACTUAL_SLOT) return actualState;
        return null;
    }

    private static void setCachedState(Object slot, OutlineState state) {
        if (slot == PREVIEW_SLOT) {
            previewState = state;
            return;
        }
        if (slot == ACTUAL_SLOT) {
            actualState = state;
        }
    }

    private static void removeOutline(Object slot) {
        Outliner.getInstance().remove(slot);
        setCachedState(slot, null);
    }

    private static boolean aabbEquals(AABB first, AABB second) {
        return first != null
                && first.minX == second.minX
                && first.minY == second.minY
                && first.minZ == second.minZ
                && first.maxX == second.maxX
                && first.maxY == second.maxY
                && first.maxZ == second.maxZ;
    }

    private record OutlineState(AABB aabb, int color) {
    }
}
