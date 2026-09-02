package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllTags;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.tooltipComponentForGoggles;

/**
 * 喷洒器方块实体，管理流体储罐、角度调节（0~270°）和喷洒条件。
 * 角度直接影响消耗速率：0°关闭，270°全开。
 * 同时提供护目镜信息（当前角度、喷洒范围）。
 */
public class SprayerBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    private static final int TANK_CAPACITY = 32;
    private static final int MAX_CONSUMPTION = 32;
    public static final float MAX_ANGLE = 270.0f;
    private static final float ANGLE_EPSILON = 1e-6f;

    private SmartFluidTankBehaviour tank;
    private float angle = MAX_ANGLE;
    private float prevAngle = MAX_ANGLE;
    private boolean frontBlocked = false;
    private boolean consumedSequenceInput = false;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(tank = SmartFluidTankBehaviour.single(this, TANK_CAPACITY));
    }

    private boolean shouldSpray() {
        return !frontBlocked && angle > ANGLE_EPSILON;
    }

    @Override
    public void tick() {
        updatePrevAngle();
        if (level != null && !level.isClientSide) {
            updateFrontBlocked();
            updateAngle();
        }
        super.tick();
        if (level != null && !level.isClientSide) {
            tickSpraying();
        }
    }

    private void updatePrevAngle() {
        prevAngle = angle;
    }

    private void updateFrontBlocked() {
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
                setAngle(angle + signedAngle);
                consumedSequenceInput = true;
            }
            return;
        }

        consumedSequenceInput = false;

        float speed = getTheoreticalSpeed();
        if (speed != 0) {
            setAngle(angle + KineticBlockEntity.convertToAngular(speed));
        }
    }

    private void setAngle(float newAngle) {
        newAngle = Mth.clamp(newAngle, 0f, MAX_ANGLE);
        if (newAngle != angle) {
            angle = newAngle;
            setChanged();
            sendData();
        }
    }

    public float getRenderedAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevAngle, angle);
    }

    private void tickSpraying() {
        if (level != null && level.getGameTime() % 5 != 0) return;
        if (!shouldSpray()) return;
        trySpray();
    }

    /**
     * 尝试执行喷洒：从后方抽取流体，应用效果并生成粒子。
     */
    private void trySpray() {
        IFluidHandler tankHandler = tank.getPrimaryHandler();

        if (tankHandler.getFluidInTank(0).isEmpty()) {
            Direction facing = getBlockState().getValue(SprayerBlock.FACING);
            BlockPos behind = worldPosition.relative(facing.getOpposite());
            IFluidHandler source = null;
            if (level != null) {
                source = level.getCapability(Capabilities.FluidHandler.BLOCK, behind, facing);
            }
            if (source != null) {
                int space = tankHandler.getTankCapacity(0) - tankHandler.getFluidInTank(0).getAmount();
                if (space > 0) {
                    int toExtract = Math.min(MAX_CONSUMPTION, space);
                    FluidStack extracted = source.drain(toExtract, IFluidHandler.FluidAction.EXECUTE);
                    if (!extracted.isEmpty()) {
                        tankHandler.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
        }

        FluidStack fluid = tankHandler.getFluidInTank(0);
        if (fluid.isEmpty()) return;

        OpenPipeEffectHandler effectHandler = SprayerHelper.getEffectHandler(fluid);
        if (effectHandler == null) return;

        int maxAllowed = SprayerHelper.getMaxConsumption(angle, MAX_ANGLE, MAX_CONSUMPTION);
        if (maxAllowed <= 0) return;

        int toDrain = Math.min(maxAllowed, fluid.getAmount());
        if (toDrain <= 0) return;

        FluidStack drained = tankHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        float ratio = SprayerHelper.getFluidRatio(drained.getAmount(), MAX_CONSUMPTION);
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        Vec3 center = SprayerHelper.getSprayCenter(worldPosition, facing);
        AABB aabb = SprayerHelper.buildAABB(center, facing, ratio);
        SprayerHelper.applyEffect(effectHandler, level, aabb, drained);

        if (level instanceof ServerLevel serverLevel) {
            SprayerHelper.spawnParticles(serverLevel, center, facing, ratio, drained);
        }
    }

    // ========== 护目镜工具提示 ==========
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(tooltipComponentForGoggles("sprayer.header"));
        tooltip.add(tooltipComponentForGoggles("sprayer.angle", Component.literal(String.format("%.0f", angle)).withStyle(ChatFormatting.AQUA), Component.literal(String.format("%.0f", MAX_ANGLE)).withStyle(ChatFormatting.AQUA)));
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        AABB aabb = SprayerHelper.buildAABBFromAngle(worldPosition, facing, angle, MAX_ANGLE);
        tooltip.add(tooltipComponentForGoggles("sprayer.range", Component.literal(String.format("%.1f", aabb.getXsize())).withStyle(ChatFormatting.GOLD), Component.literal(String.format("%.1f", aabb.getYsize())).withStyle(ChatFormatting.GOLD), Component.literal(String.format("%.1f", aabb.getZsize())).withStyle(ChatFormatting.GOLD)));
        return true;
    }

    // ========== 流体能力（仅后方可访问） ==========
    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) return null;
        return side == facing.getOpposite() ? tank.getCapability() : null;
    }

    // ========== NBT 读写 ==========
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        float previousAngle = angle;
        angle = Mth.clamp(compound.getFloat("Angle"), 0f, MAX_ANGLE);
        prevAngle = clientPacket ? previousAngle : angle;
        if (compound.contains("Tank") && !compound.contains("Tanks")) {
            tank.getPrimaryHandler().readFromNBT(registries, compound.getCompound("Tank"));
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Angle", angle);
    }

    public float getAngle() {
        return angle;
    }
}
