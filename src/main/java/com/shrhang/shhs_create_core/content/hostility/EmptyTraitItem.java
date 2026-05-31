package com.shrhang.shhs_create_core.content.hostility;

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
    private static final int MIN_USE_TICKS = 10;

    public EmptyTraitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCrouching()) return InteractionResultHolder.pass(stack);
        if (findTarget(level, player) == null) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || level.isClientSide) return;
        if (!player.isCrouching()) return;
        if (getUseDuration(stack, entity) - timeLeft < MIN_USE_TICKS) return;

        LivingEntity target = findTarget(level, player);
        if (target == null) return;

        var opt = LHMiscs.MOB.type().getExisting(target);
        if (opt.isEmpty()) return;

        var cap = opt.get();
        if (cap.traits.isEmpty()) return;

        MobTrait trait = selectTraitByWeight(cap, target);
        if (trait == null) return;

        cap.setTrait(trait, cap.getTraitLevel(trait) - 1);
        cap.syncToClient(target);

        ItemStack traitSymbol = new ItemStack(trait.asItem());
        if (!player.isCreative()) stack.shrink(1);
        if (!player.addItem(traitSymbol)) player.drop(traitSymbol, false);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
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
