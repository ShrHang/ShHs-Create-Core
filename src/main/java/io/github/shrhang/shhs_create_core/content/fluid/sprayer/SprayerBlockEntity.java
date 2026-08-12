package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 喷洒器方块实体，管理流体储罐、角度调节（0~270°）和喷洒条件。
 * 角度直接影响消耗速率：0°关闭，270°全开。
 */
public class SprayerBlockEntity extends KineticBlockEntity implements IFluidHandler {

    private static final int TANK_CAPACITY = 32;
    private static final int MAX_CONSUMPTION = 32;
    private static final float MAX_ANGLE = 270.0f;
    private static final float ANGLE_SPEED_SCALE = 0.3f;
    private static final float ANGLE_EPSILON = 1e-6f;

    private FluidTank tank;
    private float angle = MAX_ANGLE;
    private float prevAngle = MAX_ANGLE;
    private boolean frontBlocked = false;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        behaviours.add(new SprayBehaviour(this, tank, () -> {
            // 消耗因子 = angle / 270，0~1 线性
            float factor = Mth.clamp(angle / MAX_ANGLE, 0f, 1f);
            return (int) (MAX_CONSUMPTION * factor);
        }, this::shouldSpray));
    }

    /**
     * 喷洒条件：前方无阻挡且角度大于阈值。
     */
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
        prevAngle = angle;
        if (level != null && !level.isClientSide) {
            updateFrontBlocked();
            updateAngle();
        }
        super.tick();
    }

    /**
     * 供渲染使用的插值角度。
     */
    public float getRenderedAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevAngle, angle);
    }

    // ===== 阻风检测 =====
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
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }

    // ===== 角度更新（范围 0~270） =====
    private void updateAngle() {
        float speed = getTheoreticalSpeed();
        if (speed != 0) {
            float delta = speed * ANGLE_SPEED_SCALE;
            float newAngle = Mth.clamp(angle + delta, 0f, MAX_ANGLE);
            if (newAngle != angle) {
                angle = newAngle;
                setChanged();
                sendData();
            }
        }
    }

    // ===== 流体能力（仅后方可访问） =====
    @Nullable
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        if (side == null) return null;
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        if (side.getAxis() != facing.getAxis()) return null;
        if (side == facing.getOpposite()) {
            return this;
        }
        return null;
    }

    // ===== IFluidHandler 实现 =====
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

    // ===== NBT 读写 =====
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        float previousAngle = angle;
        angle = compound.getFloat("Angle");
        // 确保角度在有效范围内
        angle = Mth.clamp(angle, 0f, MAX_ANGLE);
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
}