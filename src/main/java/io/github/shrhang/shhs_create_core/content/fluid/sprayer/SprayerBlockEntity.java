package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllTags;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.tooltipComponentForGoggles;

/**
 * 喷洒器方块实体，管理流体储罐、角度调节（0~270°）和喷洒条件。
 * 角度直接影响消耗速率：0°关闭，270°全开。
 * 同时提供护目镜信息（当前角度、喷洒范围）。
 */
public class SprayerBlockEntity extends KineticBlockEntity implements IFluidHandler, IHaveGoggleInformation {

    private static final int TANK_CAPACITY = 32;
    private static final int MAX_CONSUMPTION = 32;
    public static final float MAX_ANGLE = 270.0f;
    private static final float ANGLE_EPSILON = 1e-6f;

    private FluidTank tank;
    private float angle = MAX_ANGLE;
    private float prevAngle = MAX_ANGLE;
    private boolean frontBlocked = false;
    private boolean consumedSequenceInput = false;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        behaviours.add(new SprayBehaviour(this, tank,
                () -> SprayerHelper.getMaxConsumption(angle, MAX_ANGLE, MAX_CONSUMPTION),
                this::shouldSpray));
    }

    private boolean shouldSpray() {
        return !frontBlocked && angle > ANGLE_EPSILON;
    }

    public FluidTank getTank() {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        return tank;
    }

    @Override
    public void tick() {
        updatePrevAngle();
        if (level != null && !level.isClientSide) {
            updateFrontBlocked();
            updateAngle();
        }
        super.tick();
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

    // ========== 护目镜工具提示 ==========
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(tooltipComponentForGoggles("sprayer.header"));
        tooltip.add(tooltipComponentForGoggles("sprayer.angle", Component.literal(String.format("%.0f", angle)).withStyle(ChatFormatting.AQUA), Component.literal(String.format("%.0f", MAX_ANGLE)).withStyle(ChatFormatting.AQUA)));
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        AABB aabb = SprayerHelper.buildAreaFromAngle(worldPosition, facing, angle, MAX_ANGLE).bounds();
        tooltip.add(tooltipComponentForGoggles("sprayer.range", Component.literal(String.format("%.1f", aabb.getXsize())).withStyle(ChatFormatting.GOLD), Component.literal(String.format("%.1f", aabb.getYsize())).withStyle(ChatFormatting.GOLD), Component.literal(String.format("%.1f", aabb.getZsize())).withStyle(ChatFormatting.GOLD)));
        return true;
    }

    // ========== 流体能力（仅后方可访问） ==========
    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) return null;
        return side == facing.getOpposite() ? this : null;
    }

    // ========== IFluidHandler 实现 ==========
    @Override
    public int getTanks() {
        return getTank().getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return getTank().getFluidInTank(tankIndex);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return getTank().getTankCapacity(tankIndex);
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        return getTank().isFluidValid(tankIndex, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return getTank().fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return getTank().drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return getTank().drain(maxDrain, action);
    }

    // ========== NBT 读写 ==========
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        float previousAngle = angle;
        angle = Mth.clamp(compound.getFloat("Angle"), 0f, MAX_ANGLE);
        prevAngle = clientPacket ? previousAngle : angle;
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        tank.readFromNBT(registries, compound.getCompound("Tank"));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Angle", angle);
        if (tank != null) {
            compound.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        }
    }

    public float getAngle() {
        return angle;
    }
}
