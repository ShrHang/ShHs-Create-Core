package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class SprayBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<SprayBehaviour> TYPE = new BehaviourType<>();

    private static final int MAX_CONSUMPTION = 4;

    private static final double UP_HORIZONTAL = 8.0;
    private static final double UP_UP = 2.0;
    private static final double UP_DOWN = 0.0;

    private static final double DOWN_HORIZONTAL = 8.0;
    private static final double DOWN_UP = 0.0;
    private static final double DOWN_DOWN = 10.5;

    private static final double HORIZONTAL_HORIZONTAL = 8.0;
    private static final double HORIZONTAL_UP = 2.0;
    private static final double HORIZONTAL_DOWN = 6.0;

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
        if (!shouldSpraySupplier.getAsBoolean()) return;
        trySpray(level);
    }

    private void trySpray(Level level) {
        // 如果储罐为空，尝试从后方抽取
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

        float ratio = (float) drained.getAmount() / MAX_CONSUMPTION;
        Direction facing = blockEntity.getBlockState().getValue(SprayerBlock.FACING);

        applyEffect(level, facing, ratio, drained, effectHandler);
        if (level instanceof ServerLevel serverLevel) {
            spawnParticles(serverLevel, facing, ratio, drained);
        }
    }

    protected void applyEffect(Level level, Direction facing, float ratio, FluidStack drained, OpenPipeEffectHandler handler) {
        AABB aabb = buildAABB(blockEntity.getBlockPos(), facing, ratio);
        handler.apply(level, aabb, drained);
    }

    protected void spawnParticles(ServerLevel serverLevel, Direction facing, float ratio, FluidStack drained) {
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 origin = Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));

        double distanceFactor = 0.0;
        Player nearestPlayer = serverLevel.getNearestPlayer(origin.x, origin.y, origin.z, 32.0, false);
        if (nearestPlayer != null) {
            double dist = Math.sqrt(nearestPlayer.distanceToSqr(origin.x, origin.y, origin.z));
            if (dist <= 8.0) {
                distanceFactor = 1.0;
            } else if (dist < 32.0) {
                distanceFactor = 1.0 - (dist - 8.0) / (32.0 - 8.0);
            }
        }

        int baseCount = Math.max(4, (int) (ratio * 20));
        int count = (int) (baseCount * distanceFactor);
        if (count < 2) count = 2;

        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 up = Math.abs(dir.dot(new Vec3(0, 1, 0))) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize();
        up = right.cross(dir).normalize();

        FluidParticleData particleData = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), drained);

        for (int i = 0; i < count; i++) {
            double theta = Math.random() * Math.PI / 2;
            double phi = Math.random() * 2 * Math.PI;

            Vec3 randomDir = dir.scale(Math.cos(theta))
                    .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                    .add(up.scale(Math.sin(theta) * Math.sin(phi)));

            double speed = 0.1 + ratio * 0.5;
            Vec3 velocity = randomDir.scale(speed);

            serverLevel.sendParticles(
                    particleData,
                    origin.x, origin.y, origin.z,
                    0,
                    velocity.x, velocity.y, velocity.z,
                    1.0
            );
        }
    }

    protected AABB buildAABB(BlockPos pos, Direction dir, float ratio) {
        Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(dir.getNormal()).scale(0.5));
        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());

        Vec3 upAxis, rightAxis;
        if (dir.getAxis() == Direction.Axis.Y) {
            rightAxis = new Vec3(1, 0, 0);
            upAxis = new Vec3(0, 0, 1);
        } else {
            upAxis = new Vec3(0, 1, 0);
            rightAxis = normal.cross(upAxis).normalize();
            if (rightAxis.lengthSqr() < 1e-6) {
                rightAxis = new Vec3(0, 0, 1);
            }
            upAxis = rightAxis.cross(normal).normalize();
        }

        double lenForward, lenBack, lenUp, lenDown, lenRight, lenLeft;
        if (dir == Direction.UP) {
            lenRight = lenLeft = UP_HORIZONTAL;
            lenUp = UP_UP;
            lenDown = UP_DOWN;
            lenForward = lenBack = 0;
        } else if (dir == Direction.DOWN) {
            lenRight = lenLeft = DOWN_HORIZONTAL;
            lenUp = DOWN_UP;
            lenDown = DOWN_DOWN;
            lenForward = lenBack = 0;
        } else {
            lenForward = HORIZONTAL_HORIZONTAL;
            lenBack = 0;
            lenRight = HORIZONTAL_HORIZONTAL;
            lenLeft = HORIZONTAL_HORIZONTAL;
            lenUp = HORIZONTAL_UP;
            lenDown = HORIZONTAL_DOWN;
        }

        lenForward *= ratio;
        lenBack *= ratio;
        lenRight *= ratio;
        lenLeft *= ratio;
        lenUp *= ratio;
        lenDown *= ratio;

        double minX = center.x - lenLeft * rightAxis.x - lenBack * normal.x - lenDown * upAxis.x;
        double maxX = center.x + lenRight * rightAxis.x + lenForward * normal.x + lenUp * upAxis.x;
        double minY = center.y - lenLeft * rightAxis.y - lenBack * normal.y - lenDown * upAxis.y;
        double maxY = center.y + lenRight * rightAxis.y + lenForward * normal.y + lenUp * upAxis.y;
        double minZ = center.z - lenLeft * rightAxis.z - lenBack * normal.z - lenDown * upAxis.z;
        double maxZ = center.z + lenRight * rightAxis.z + lenForward * normal.z + lenUp * upAxis.z;

        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        AABB near = new AABB(center, center).expandTowards(0.5, 0.5, 0.5);
        return aabb.minmax(near);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}