package io.github.shrhang.shhs_create_core.content.fluid.sprayer;

import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * 喷洒行为，控制流体消耗、效果应用与粒子生成。
 * 最大消耗量已提升至 32 mB/tick，储罐容量相应提升至 32 mB。
 * 喷洒频率改为每 5 tick 一次（通过 gameTime 控制）。
 */
public class SprayBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<SprayBehaviour> TYPE = new BehaviourType<>();
    private static final int MAX_CONSUMPTION = 32;

    protected final FluidTank tank;
    private final IntSupplier maxConsumptionSupplier;
    private final BooleanSupplier shouldSpraySupplier;

    public SprayBehaviour(SmartBlockEntity be, FluidTank tank, IntSupplier maxConsumptionSupplier,
                          BooleanSupplier shouldSpraySupplier) {
        super(be);
        this.tank = tank;
        this.maxConsumptionSupplier = maxConsumptionSupplier;
        this.shouldSpraySupplier = shouldSpraySupplier;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getWorld();
        if (level == null || level.isClientSide()) return;
        if (level.getGameTime() % 5 != 0) return;
        if (!shouldSpraySupplier.getAsBoolean()) return;
        trySpray(level);
    }

    /**
     * 尝试执行喷洒：从后方抽取流体，应用效果并生成粒子。
     */
    private void trySpray(Level level) {
        if (tank == null) return;

        // 若储罐为空，尝试从后方抽取
        if (tank.getFluid().isEmpty()) {
            Direction facing = blockEntity.getBlockState().getValue(SprayerBlock.FACING);
            BlockPos behind = blockEntity.getBlockPos().relative(facing.getOpposite());
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, behind, facing);
            if (handler != null) {
                int space = tank.getCapacity() - tank.getFluidAmount();
                if (space > 0) {
                    int toExtract = Math.min(MAX_CONSUMPTION, space);
                    FluidStack extracted = handler.drain(toExtract, IFluidHandler.FluidAction.EXECUTE);
                    if (!extracted.isEmpty()) {
                        tank.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
        }

        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) return;

        OpenPipeEffectHandler effectHandler = OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
        if (effectHandler == null) return;

        int maxAllowed = maxConsumptionSupplier.getAsInt();
        if (maxAllowed <= 0) return;

        int toDrain = Math.min(maxAllowed, fluid.getAmount());
        if (toDrain <= 0) return;

        FluidStack drained = tank.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        float ratio = SprayerHelper.getFluidRatio(drained.getAmount(), MAX_CONSUMPTION);
        Direction facing = blockEntity.getBlockState().getValue(SprayerBlock.FACING);
        BlockPos pos = blockEntity.getBlockPos();
        SprayerHelper.SprayArea area = SprayerHelper.buildArea(pos, facing, ratio);
        SprayerHelper.applyEffect(level, area.bounds(), drained);

        if (level instanceof ServerLevel serverLevel) {
            SprayerHelper.spawnParticles(serverLevel, area.center(), facing, ratio, drained);
        }
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}
