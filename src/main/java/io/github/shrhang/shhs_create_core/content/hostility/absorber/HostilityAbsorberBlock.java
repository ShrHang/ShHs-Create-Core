package io.github.shrhang.shhs_create_core.content.hostility.absorber;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import static io.github.shrhang.shhs_create_core.content.registries.ShHsBlockEntityTypes.HOSTILITY_ABSORBER_BE;

/**
 * 恶意吸收器方块，属于动力学方块，固定旋转轴为 Y 轴，复用石磨齿轮模型。
 * 实现 ICogWheel 接口以支持齿轮传动识别，并允许从下方接入动力。
 */
public class HostilityAbsorberBlock extends KineticBlock implements ICogWheel, IBE<HostilityAbsorberBlockEntity> {
    public HostilityAbsorberBlock(Properties properties) {super(properties);}

    @Override
    public Axis getRotationAxis(BlockState state) {return Axis.Y;}
    /**
     * 仅允许从下方（DOWN）接入动力轴，与石磨一致。
     */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }
    /**
     * 方块碰撞箱复用石磨的形状（可选，若不设置则使用默认全方块）。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return AllShapes.MILLSTONE;
    }
    /**
     * 粒子目标半径，与石磨相同。
     */
    @Override
    public float getParticleTargetRadius() {
        return 0.65f;
    }
    /**
     * 粒子初始半径，与石磨相同。
     */
    @Override
    public float getParticleInitialRadius() {
        return 0.75f;
    }
    @Override
    public Class<HostilityAbsorberBlockEntity> getBlockEntityClass() {
        return HostilityAbsorberBlockEntity.class;
    }
    @Override
    public BlockEntityType<? extends HostilityAbsorberBlockEntity> getBlockEntityType() {
        return HOSTILITY_ABSORBER_BE.get();
    }
}