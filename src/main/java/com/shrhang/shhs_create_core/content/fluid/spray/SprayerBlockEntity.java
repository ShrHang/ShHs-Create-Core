package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SprayerBlockEntity extends KineticBlockEntity implements IFluidHandler {

    private static final int TANK_CAPACITY = 5;
    private static final int MAX_CONSUMPTION = 4;
    private static final float ANGLE_SPEED_SCALE = 0.3f;

    private FluidTank tank;
    private float angle = 180f;
    public LerpedFloat pointer;

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        pointer = LerpedFloat.linear()
                .startWithValue(0)
                .chase(0, 0, Chaser.LINEAR);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        behaviours.add(new SprayBehaviour(this, tank, () -> {
            float factor = Mth.clamp(angle / 180f, 0f, 1f);
            return (int) (MAX_CONSUMPTION * factor);
        }));
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        float target = angle > 0 ? 1 : 0;
        pointer.chase(target, getPointerChaseSpeed(), Chaser.LINEAR);
        sendData();
    }

    private float getPointerChaseSpeed() {
        return Mth.clamp(Math.abs(getSpeed()) / 16f / 20f, 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        pointer.tickChaser();

        if (level != null && !level.isClientSide) {
            float speed = getTheoreticalSpeed();
            if (speed != 0) {
                float delta = speed * ANGLE_SPEED_SCALE;
                float newAngle = Mth.clamp(angle + delta, 0f, 180f);
                if (newAngle != angle) {
                    angle = newAngle;
                    boolean enabled = angle > 0.001f;
                    BlockState state = getBlockState();
                    if (state.getValue(SprayerBlock.ENABLED) != enabled) {
                        level.setBlock(worldPosition, state.setValue(SprayerBlock.ENABLED, enabled), 3);
                    }
                    setChanged();
                    sendData();
                    float target = angle / 180f;
                    pointer.chase(target, getPointerChaseSpeed(), Chaser.LINEAR);
                }
            }
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        angle = compound.getFloat("Angle");
        pointer.readNBT(compound.getCompound("Pointer"), clientPacket);
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        tank.readFromNBT(registries, compound.getCompound("Tank"));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Angle", angle);
        compound.put("Pointer", pointer.writeNBT());
        if (tank != null) {
            compound.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        }
    }

    // ===== 侧边感知的流体能力 =====

    /**
     * 获取指定侧的流体处理器。
     * 禁止 facing 面（喷洒出口）和传动杆轴方向（两个端面）注入，
     * 其他所有侧面（包括背面、仪表面）允许输入。
     */
    public IFluidHandler getHandlerForSide(Direction side) {
        if (side == null) {
            return this;
        }
        Direction facing = getBlockState().getValue(SprayerBlock.FACING);
        Axis driveAxis = SprayerBlock.getDriveAxis(getBlockState()); // 使用传动杆轴
        if (side == facing || side.getAxis() == driveAxis) {
            return new RestrictedFluidHandler(getTank());
        }
        return this;
    }

    private FluidTank getTank() {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        return tank;
    }

    // ===== IFluidHandler 委托 =====

    @Override
    public int getTanks() {
        return getTank().getTanks();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tankIndex) {
        return getTank().getFluidInTank(tankIndex);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return getTank().getTankCapacity(tankIndex);
    }

    @Override
    public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
        return getTank().isFluidValid(tankIndex, stack);
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return getTank().fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return getTank().drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        return getTank().drain(maxDrain, action);
    }

    // ===== 限制处理器（禁止注入） =====
    private static class RestrictedFluidHandler implements IFluidHandler {
        private final FluidTank tank;

        public RestrictedFluidHandler(FluidTank tank) {
            this.tank = tank;
        }

        @Override
        public int getTanks() {
            return tank.getTanks();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            return tank.getFluidInTank(tankIndex);
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tank.getTankCapacity(tankIndex);
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return tank.isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
            return 0; // 禁止填充
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    }
}