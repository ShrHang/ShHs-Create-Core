package com.shrhang.shhs_create_core.content.fluid.spray;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Predicate;

/**
 * 喷洒器的流体传输行为，控制管道连接与流体抽取权限。
 * <p>
 * 注：该行为混合了基础管道功能与喷口动态控制，未来可能将管道与滴灌能力拆分为独立的行为模块。
 */
public class SprayerFluidTransportBehaviour extends FluidTransportBehaviour {
    private final Predicate<Direction> spoutPredicate;

    /**
     * 构造行为实例。
     *
     * @param be             所属方块实体
     * @param spoutPredicate 判断某方向是否为当前激活喷口的谓词，用于控制非轴向方向的连接
     */
    public SprayerFluidTransportBehaviour(SmartBlockEntity be, Predicate<Direction> spoutPredicate) {
        super(be);
        this.spoutPredicate = spoutPredicate != null ? spoutPredicate : d -> false;
    }

    /**
     * 判断某方向是否允许建立流体连接（即成为管道接口）。
     * <p>
     * 轴向方向（facing 方向）始终允许，以维持基础管道功能；
     * 非轴向方向仅当其为当前激活喷口时才开放连接。
     *
     * @param state     方块状态
     * @param direction 方向
     * @return 是否允许流向该方向
     */
    @Override
    public boolean canHaveFlowToward(BlockState state, Direction direction) {
        Direction facing = state.getValue(SprayerBlock.FACING);
        if (direction.getAxis() == facing.getAxis()) {
            return true;
        }
        return spoutPredicate.test(direction);
    }

    /**
     * 判断是否允许从某方向拉取流体。
     * <p>
     * 受 {@link SprayerBlock#ENABLED} 属性锁紧控制，且喷口拉取权限已由 {@link #canHaveFlowToward} 限定连接存在性，
     * 此处无需额外限制。
     *
     * @param fluid     待抽取的流体
     * @param state     方块状态
     * @param direction 方向
     * @return 是否允许拉取
     */
    @Override
    public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
        if (state.hasProperty(SprayerBlock.ENABLED) && !state.getValue(SprayerBlock.ENABLED)) {
            return false;
        }
        return super.canPullFluidFrom(fluid, state, direction);
    }

    @Override
    public void tick() {
        super.tick();
    }
}