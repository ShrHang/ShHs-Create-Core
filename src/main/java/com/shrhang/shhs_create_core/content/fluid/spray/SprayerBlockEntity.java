package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 喷洒器方块实体：持有 5mB 微型流体容器，每 tick 消耗至多 5mB，
 * 模拟即时水压喷射，不储存多余流体。
 */
public class SprayerBlockEntity extends SmartBlockEntity implements IFluidHandler {

    private static final int TANK_CAPACITY = 5;
    private FluidTank tank; // 延迟初始化，避免父类构造时调用 addBehaviours 时尚未初始化

    public SprayerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 确保 tank 在首次使用时创建（父类构造中调用时子类字段尚未初始化）
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        behaviours.add(new SprayBehaviour(this, tank));
    }

    /**
     * 显式提供流体能力访问器，返回类型明确为 IFluidHandler，
     * 与 BrassEnderChestBlockEntity#getInventory() 的模式对称。
     */
    public IFluidHandler getFluidHandler() {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        return this;
    }

    // ===== IFluidHandler 委托（确保 tank 非空） =====
    private FluidTank getTank() {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        return tank;
    }

    @Override
    public int getTanks() {
        return getTank().getTanks();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return getTank().getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return getTank().getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return getTank().isFluidValid(tank, stack);
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
}