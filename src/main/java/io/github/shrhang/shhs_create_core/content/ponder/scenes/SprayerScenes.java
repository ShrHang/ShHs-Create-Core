package io.github.shrhang.shhs_create_core.content.ponder.scenes;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class SprayerScenes {
    public static void intro(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sprayer.intro", "Spraying Fluids using Sprayer");
        scene.configureBasePlate(0, 0, 5);

        Selection largeCog = util.select().position(5, 0, 1);
        Selection tank = util.select().fromTo(4, 1, 2, 4, 2, 2);
        Selection kinetics = util.select().fromTo(5, 1, 0, 3, 1, 0);
        BlockPos open = util.grid().at(1, 1, 1);
        BlockPos sprayerPos = util.grid().at(2, 1, 1);

        scene.world().showSection(util.select().layer(0).substract(largeCog).add(util.select().position(open)), Direction.UP);

        scene.idle(5);
        scene.world().showSection(tank, Direction.DOWN);
        scene.idle(5);
        FluidStack content = new FluidStack(Fluids.LAVA, 10000);
        scene.world().modifyBlockEntity(util.grid().at(4, 1, 2), FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(content, IFluidHandler.FluidAction.EXECUTE));
        scene.idle(10);
        scene.world().showSection(largeCog, Direction.DOWN);
        scene.world().showSection(kinetics, Direction.SOUTH);
        for (int i = 4; i >= 2; i--) {
            scene.world().showSection(util.select().position(i, 1, 1), i == 4 ? Direction.SOUTH : Direction.EAST);
            scene.idle(3);
        }

        scene.overlay()
                .showText(80)
                .pointAt(util.vector().centerOf(1, 1, 1))
                .attachKeyFrame()
                .placeNearTarget()
                .text("The fluid's effect is applied to the block in front of the open pipe...");
        scene.idle(20);
        ElementLink<EntityElement> chicken = scene.world().createEntity(level -> {
            var entity = new Chicken(EntityType.CHICKEN, level);
            var pos = open.getBottomCenter();
            entity.setPos(pos);
            entity.xo = pos.x;
            entity.yo = pos.y;
            entity.zo = pos.z;
            entity.yRotO = 0;
            entity.setYRot(0);
            entity.yHeadRotO = entity.yHeadRot = 0;
            entity.setRemainingFireTicks(60);
            entity.setSharedFlagOnFire(true);
            return entity;
        });
        scene.idle(60);

        scene.world().modifyEntity(chicken, Entity::discard);
        scene.idle(40);

        scene.world().setBlock(open, Blocks.LAVA.defaultBlockState(), false);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(1, 1, 1))
                .attachKeyFrame()
                .placeNearTarget()
                .text("...and creates the fluid sources.");
        scene.idle(60);

        scene.world().hideSection(util.select().position(open), Direction.DOWN);
        scene.world().hideSection(util.select().position(sprayerPos), Direction.DOWN);
        scene.idle(15);

        var sprayer = scene.world().makeSectionIndependent(util.select().position(sprayerPos.above()));
        scene.world().moveSection(sprayer, util.vector().of(0, -1, 0), 5);
        scene.idle(5);

        FluidStack lava = new FluidStack(Fluids.LAVA, 1000);
        FluidParticleData particleData =
                new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), lava);
        Direction facing = Direction.WEST;
        Vec3 origin1 = util.vector().centerOf(sprayerPos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
        Vec3 dirVec = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = dirVec.cross(up).normalize();
        ParticleEmitter sprayerEmitter = (level, x, y, z) -> {
            var random = level.random;
            double theta = random.nextDouble() * Math.PI / 5;
            double phi = random.nextDouble() * Math.PI * 2;
            Vec3 randomDir = dirVec.scale(Math.cos(theta))
                    .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                    .add(up.scale(Math.sin(theta) * Math.sin(phi)))
                    .normalize();
            Vec3 velocity = randomDir.scale(0.35 + random.nextDouble() * 0.25);
            level.addParticle(
                    particleData,
                    x, y, z,
                    velocity.x, velocity.y, velocity.z
            );
        };
        scene.effects().emitParticles(origin1, sprayerEmitter, 4, 160);
        scene.idle(20);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(1, 1, 1))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Sprayer can expand the fluid's effect to a larger area.");
        scene.idle(80);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(1, 1, 1))
                .placeNearTarget()
                .text("But won't create fluid sources.");
        scene.idle(60);

        scene.world().moveSection(sprayer, util.vector().of(1, 1, 1), 10);
        scene.idle(10);
        Vec3 origin2 = util.vector().centerOf(3,2,2)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
        scene.effects().emitParticles(origin2, sprayerEmitter, 4, 100);
        scene.idle(20);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(2, 2, 2))
                .attachKeyFrame()
                .placeNearTarget()
                .text("It can also read the fluid from connected tanks directly.");
        scene.idle(100);
        scene.markAsFinished();
    }
}
