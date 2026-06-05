package com.shrhang.shhs_create_core.content.hostility;

import com.shrhang.shhs_create_core.ShHsConfig;

import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import static com.shrhang.shhs_create_core.content.util.TraitHelper.WeightedTrait.selectTraitByWeight;

public class EmptyTraitItem extends Item {
    private static final int SHORT_CHARGE_TICKS = 40;

    public EmptyTraitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCrouching()) return InteractionResultHolder.pass(stack);

        int useDuration = getUseDuration(stack, player);
        if (useDuration <= 0) {
            if (!level.isClientSide) extractTrait(stack, level, player);
        } else {
            LivingEntity target = findTarget(level, player);
            if (requiresTargetBeforeUse(useDuration)) {
                if (target == null || !hasExtractableTrait(target)) return InteractionResultHolder.pass(stack);
            }
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (entity instanceof Player player && !player.isCrouching()) {
            player.stopUsingItem();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && player.isCrouching() && !level.isClientSide) {
            extractTrait(stack, level, player);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Math.clamp(ShHsConfig.SERVER.emptyTraitMinUseTicks.get(), 0, 72000);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    private void extractTrait(ItemStack stack, Level level, Player player) {
        LivingEntity target = findTarget(level, player);
        if (target == null) return;

        var opt = LHMiscs.MOB.type().getExisting(target);
        if (opt.isEmpty()) return;

        var cap = opt.get();
        if (cap.traits.isEmpty()) return;

        MobTrait trait = selectTraitByWeight(cap, target);
        if (trait == null || cap.getTraitLevel(trait) <= 0) return;

        cap.setTrait(trait, cap.getTraitLevel(trait) - 1);
        cap.syncToClient(target);

        ItemStack traitSymbol = new ItemStack(trait.asItem());
        if (!player.isCreative()) stack.shrink(1);
        if (!player.addItem(traitSymbol)) player.drop(traitSymbol, false);
    }

    private boolean requiresTargetBeforeUse(int useDuration) {
        return useDuration > 0 && useDuration <= SHORT_CHARGE_TICKS;
    }

    private boolean hasExtractableTrait(LivingEntity target) {
        var opt = LHMiscs.MOB.type().getExisting(target);
        return opt.isPresent() && opt.get().traits.keySet().stream()
                .anyMatch(trait -> opt.get().getTraitLevel(trait) > 0);
    }

    private LivingEntity findTarget(Level level, Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(5.0D));
        AABB box = player.getBoundingBox().expandTowards(look.scale(5.0D)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, start, end, box,
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity != player);
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }
}
