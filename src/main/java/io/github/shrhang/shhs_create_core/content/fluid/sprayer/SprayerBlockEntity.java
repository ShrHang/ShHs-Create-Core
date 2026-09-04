package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllTags;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.tooltipComponentForGoggles;

public class SprayerBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    private static final int TANK_CAPACITY = 64;
    private static final int MAX_CONSUMPTION = 32;
    public static final float MAX_ANGLE = 270.0f;
    private static final float ANGLE_EPSILON = 1e-6f;

    private SmartFluidTankBehaviour tank;
    private final LerpedFloat openness = LerpedFloat.linear()
            .startWithValue(1)
            .chase(1, 0, Chaser.LINEAR);
    private boolean frontBlocked = false;
    private boolean consumedSequenceInput = false;

    // 客户端缓存：玩家距离缩放因子，每10 tick更新
    private float cachedCountScale = 1.0f;
    private int lastPlayerCalcTick = -10;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(tank = SmartFluidTankBehaviour.single(this, TANK_CAPACITY));
    }

    private boolean shouldSpray() {
        float effectiveAngle = getEffectiveAngle();
        return !frontBlocked && effectiveAngle > ANGLE_EPSILON;
    }

    public float getAngle() {
        return openness.getValue() * MAX_ANGLE;
    }

    public float getRenderedAngle(float partialTicks) {
        return openness.getValue(partialTicks) * MAX_ANGLE;
    }

    private float getEffectiveAngle() {
        if (level == null) return getAngle();
        return level.isClientSide ? getRenderedAngle(1.0f) : getAngle();
    }

    @Override
    public void tick() {
        super.tick();
        openness.tickChaser();
        if (level == null) return;
        // 服务端逻辑：更新阻塞状态和角度
        if (!level.isClientSide) {
            updateFrontBlocked();
            updateAngle();
        }
        // 客户端：每帧生成粒子（无帧限制）
        if (level.isClientSide && shouldSpray()) {
            spawnClientParticles();
        }
        // 服务端逻辑：每5 tick消耗流体并应用效果
        if (!level.isClientSide && shouldSpray() && ( level.getGameTime() % 5 == 0)) {
            performServerSpray();
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void tickAudio() {
        super.tickAudio();
    }

    public void updateFrontBlocked() {
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        BlockState frontState = level != null ? level.getBlockState(frontPos) : null;
        boolean newBlocked = frontState != null && isBlocking(frontState, frontPos);
        if (newBlocked != frontBlocked) {
            frontBlocked = newBlocked;
            setChanged();
            sendData();
        }
    }

    private boolean isBlocking(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(AllTags.AllBlockTags.FAN_TRANSPARENT.tag)) return false;
        if (level == null) return true;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private void updateAngle() {
        if (sequenceContext != null && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE) {
            if (!consumedSequenceInput) {
                float speed = getTheoreticalSpeed();
                float signedAngle = (float) sequenceContext.getEffectiveValue(speed) * Math.signum(speed);
                setAngle(getAngle() + signedAngle);
                consumedSequenceInput = true;
            }
            return;
        }
        consumedSequenceInput = false;
        float speed = getTheoreticalSpeed();
        if (speed != 0) {
            setAngle(getAngle() + KineticBlockEntity.convertToAngular(speed));
        }
    }

    public void setAngle(float newAngle) {
        newAngle = Mth.clamp(newAngle, 0f, MAX_ANGLE);
        float newOpenness = newAngle / MAX_ANGLE;
        if (!Mth.equal(newOpenness, openness.getValue())) {
            openness.chase(newOpenness, getChaseSpeed(), Chaser.LINEAR);
            setChanged();
            sendData();
        }
    }

    private float getChaseSpeed() {
        float divisor = (MAX_ANGLE / 90f) * 16f * 20f;
        return Mth.clamp(Math.abs(getSpeed()) / divisor, 0.01f, 1f);
    }

    // ----- 客户端粒子生成（每帧） -----
    @OnlyIn(Dist.CLIENT)
    private void spawnClientParticles() {
        // 获取当前插值角度
        float angle = getRenderedAngle(1.0f);
        int maxAllowed = SprayerHelper.getMaxConsumption(angle, MAX_ANGLE, MAX_CONSUMPTION);
        if (maxAllowed <= 0) return;
        // 优先从自身 tank 获取流体
        FluidStack tankFluid = tank.getPrimaryHandler().getFluidInTank(0);
        FluidStack fluid = FluidStack.EMPTY;
        if (!tankFluid.isEmpty()) {
            // 模拟抽取至多 maxAllowed，仅用于获取类型和数量比例
            fluid = tank.getPrimaryHandler().drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
        }
        // 若 tank 为空，尝试从背后容器模拟抽取
        if (fluid.isEmpty()) {
            fluid = trySimulateDrainFromBack(maxAllowed);
        }
        if (fluid.isEmpty()) return;
        float ratio = SprayerHelper.getFluidRatio(Math.min(fluid.getAmount(), maxAllowed), MAX_CONSUMPTION);
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        Vec3 center = SprayerHelper.getSprayCenter(worldPosition, facing);
        // 玩家距离缩放：每10 tick更新一次，虚拟模式固定0.2
        if (isVirtual()) {
            cachedCountScale = 0.2f;
        } else {
            long gameTime = 0;
            if (level != null) {
                gameTime = level.getGameTime();
            }
            if (gameTime - lastPlayerCalcTick >= 10) {
                cachedCountScale = calculateCountScale(level, worldPosition);
                lastPlayerCalcTick = (int) gameTime;
            }
        }
        if (level != null) {
            SprayerHelper.spawnParticles(level, center, facing, ratio, fluid, cachedCountScale);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private FluidStack trySimulateDrainFromBack(int maxAllowed) {
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        BlockPos behind = worldPosition.relative(facing.getOpposite());
        IFluidHandler source = null;
        if (level != null) {
            source = level.getCapability(Capabilities.FluidHandler.BLOCK, behind, facing);
        }
        if (source == null) return FluidStack.EMPTY;
        return source.drain(maxAllowed, IFluidHandler.FluidAction.SIMULATE);
    }

    @OnlyIn(Dist.CLIENT)
    private float calculateCountScale(Level level, BlockPos pos) {
        Player nearest = null;
        if (level != null) {
            nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 32.0, null);
        }
        if (nearest == null) return 1.0f;
        double dx = Math.abs(nearest.getX() - (pos.getX() + 0.5));
        double dy = Math.abs(nearest.getY() - (pos.getY() + 0.5));
        double dz = Math.abs(nearest.getZ() - (pos.getZ() + 0.5));
        double manhattan = dx + dy + dz;
        return (float) Mth.clamp(1.0 - (manhattan / 32.0) * 0.8, 0.2, 1.0);
    }

    // ----- 服务端逻辑（每5 tick） -----
    private void performServerSpray() {
        float effectiveAngle = getEffectiveAngle();
        int maxAllowed = SprayerHelper.getMaxConsumption(effectiveAngle, MAX_ANGLE, MAX_CONSUMPTION);
        if (maxAllowed <= 0 || level == null) return;
        // 尝试从背后或自身tank抽取（实际消耗）
        FluidStack fluid = tryDrainFromBack(maxAllowed);
        if (fluid.isEmpty()) {
            fluid = tryDrainFromTank(maxAllowed);
        }
        if (fluid.isEmpty()) return;
        applyEffect(fluid);
    }

    private FluidStack tryDrainFromBack(int maxAllowed) {
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        BlockPos behind = worldPosition.relative(facing.getOpposite());
        if (level == null) return FluidStack.EMPTY;
        IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, behind, facing);
        if (source == null) return FluidStack.EMPTY;
        return source.drain(maxAllowed, IFluidHandler.FluidAction.EXECUTE);
    }

    private FluidStack tryDrainFromTank(int maxAllowed) {
        IFluidHandler tankHandler = tank.getPrimaryHandler();
        FluidStack fluid = tankHandler.getFluidInTank(0);
        if (fluid.isEmpty()) return FluidStack.EMPTY;
        int toDrain = Math.min(maxAllowed, fluid.getAmount());
        if (toDrain <= 0) return FluidStack.EMPTY;
        return tankHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
    }

    private void applyEffect(FluidStack drained) {
        OpenPipeEffectHandler effectHandler = SprayerHelper.getEffectHandler(drained);
        if (effectHandler == null) return;
        float ratio = SprayerHelper.getFluidRatio(drained.getAmount(), MAX_CONSUMPTION);
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        Vec3 center = SprayerHelper.getSprayCenter(worldPosition, facing);
        AABB aabb = SprayerHelper.buildAABB(center, facing, ratio);
        SprayerHelper.applyEffect(effectHandler, level, aabb, drained);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        float currentAngle = getAngle();
        tooltip.add(tooltipComponentForGoggles("sprayer.header"));
        tooltip.add(tooltipComponentForGoggles("sprayer.angle",
                Component.literal(String.format("%.0f", currentAngle)).withStyle(ChatFormatting.AQUA),
                Component.literal(String.format("%.0f", MAX_ANGLE)).withStyle(ChatFormatting.AQUA)));
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        AABB aabb = SprayerHelper.buildAABBFromAngle(worldPosition, facing, currentAngle, MAX_ANGLE);
        tooltip.add(tooltipComponentForGoggles("sprayer.range",
                Component.literal(String.format("%.1f", aabb.getXsize())).withStyle(ChatFormatting.GOLD),
                Component.literal(String.format("%.1f", aabb.getYsize())).withStyle(ChatFormatting.GOLD),
                Component.literal(String.format("%.1f", aabb.getZsize())).withStyle(ChatFormatting.GOLD)));
        return true;
    }

    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) return null;
        return side == facing.getOpposite() ? tank.getCapability() : null;
    }

    public IFluidHandler getTankInventory() {
        return tank.getPrimaryHandler();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Openness", openness.writeNBT());
        if (clientPacket) {
            compound.putBoolean("FrontBlocked", frontBlocked);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        openness.readNBT(compound.getCompound("Openness"), clientPacket);
        if (clientPacket) {
            frontBlocked = compound.getBoolean("FrontBlocked");
        }
    }
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (level instanceof VirtualRenderWorld) {
            if (tag.contains("Openness", Tag.TAG_COMPOUND)) {
                openness.readNBT(tag.getCompound("Openness"), false);
            }
            frontBlocked = tag.getBoolean("FrontBlocked");
            return;
        }
        super.handleUpdateTag(tag, registries);
    }
}