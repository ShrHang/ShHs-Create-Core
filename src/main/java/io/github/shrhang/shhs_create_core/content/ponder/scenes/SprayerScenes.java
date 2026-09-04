package io.github.shrhang.shhs_create_core.content.ponder.scenes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlock;
import io.github.shrhang.shhs_create_core.content.fluid.sprayer.SprayerBlockEntity;
import io.github.shrhang.shhs_create_core.content.registries.ShHsBlocks;
import io.github.shrhang.shhs_create_core.content.util.sprayer.SprayerHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class SprayerScenes {
    public static void intro(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sprayer.intro", "Spraying Fluids using Sprayers");
        scene.configureBasePlate(0, 0, 5);

        Direction facing = Direction.WEST;
        BlockPos openPos = util.grid().at(1, 1, 1);
        BlockPos sprayerPos = util.grid().at(2, 1, 1);
        Selection largeCog = util.select().position(5, 0, 1);
        Selection tank = util.select().fromTo(4, 1, 2, 3, 2, 3);
        Selection kinetics = util.select().fromTo(5, 1, 0, 3, 1, 0);
        Selection open = util.select().position(openPos);
        Selection sprayer = util.select().position(sprayerPos);


        scene.world().showSection(util.select().layer(0).substract(largeCog).add(open), Direction.UP);
        scene.idle(5);

        scene.world().showSection(tank, Direction.DOWN);
        scene.idle(5);

        scene.world().modifyBlockEntity(util.grid().at(3, 1, 2), FluidTankBlockEntity.class, be ->
                be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 48000), IFluidHandler.FluidAction.EXECUTE));
        scene.idle(10);

        scene.world().showSection(largeCog, Direction.DOWN);
        scene.world().showSection(kinetics, Direction.SOUTH);
        for (int i = 4; i >= 2; i--) {
            scene.world().showSection(util.select().position(i, 1, 1), i == 4 ? Direction.SOUTH : Direction.EAST);
            scene.idle(3);
        }

        scene.overlay()
                .showText(80)
                .pointAt(util.vector().centerOf(openPos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("The fluid's effect is applied to the front of the open pipe...");
        scene.idle(20);

        ElementLink<EntityElement> chicken = scene.world().createEntity(level -> {
            var entity = new Chicken(EntityType.CHICKEN, level);
            var pos = openPos.getBottomCenter();
            entity.setPos(pos);
            entity.xo = pos.x;
            entity.yo = pos.y;
            entity.zo = pos.z;
            entity.yRotO = 180;
            entity.setYRot(180);
            entity.yHeadRotO = entity.yHeadRot = 180;
            entity.yBodyRotO = entity.yBodyRot = 180;
            entity.setRemainingFireTicks(60);
            entity.setSharedFlagOnFire(true);
            return entity;
        });
        scene.idle(60);
        scene.world().modifyEntity(chicken, Entity::discard);
        scene.idle(40);

        scene.world().setBlock(openPos, Blocks.LAVA.defaultBlockState(), false);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(openPos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("...and creates the fluid sources.");
        scene.idle(60);

        scene.world().hideSection(sprayer.add(open), Direction.DOWN);
        scene.idle(15);

        // ----- 第一个喷洒器 -----
        scene.world().setBlock(sprayerPos, ShHsBlocks.SPRAYER.getDefaultState()
                .setValue(SprayerBlock.FACING, facing), false);
        scene.world().showSection(sprayer, Direction.DOWN);
        scene.idle(5);
        scene.world().modifyBlockEntity(sprayerPos, SprayerBlockEntity.class, be ->
                be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE));
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(openPos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Sprayers can expand the fluid's effect to a larger area.");
        scene.idle(80);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(openPos))
                .placeNearTarget()
                .text("But won't create fluid sources.");
        scene.idle(70);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(openPos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("If the nozzle is blocked, sprayers will not operate.");
        scene.world().setBlock(openPos, AllBlocks.COPPER_CASING.getDefaultState(), true);
        scene.world().showSection(open, Direction.EAST);
        scene.idle(5);
        scene.world().modifyBlockEntity(sprayerPos, SprayerBlockEntity.class, SprayerBlockEntity::updateFrontBlocked);
        scene.idle(80);

        // ----- 第二个喷洒器（移动演示）-----
        sprayerPos = util.grid().at(2, 2, 3);
        scene.world().showSection(util.select().position(sprayerPos), Direction.DOWN);
        scene.idle(10);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(sprayerPos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Sprayers can also read the fluid from connected tanks directly.");
        scene.idle(60);

        scene.markAsFinished();
    }

    public static void range(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sprayer.range", "Controlling spray Range by adjusting the angle");
        scene.configureBasePlate(0, 0, 5);

        Direction facing = Direction.WEST;
        BlockPos pumpPos = util.grid().at(3, 1, 2);
        BlockPos sprayerPos = util.grid().at(2, 1, 2);
        BlockPos valvePos = util.grid().at(2, 2, 2);
        Selection largeCog = util.select().position(5, 0, 2);
        Selection tank = util.select().fromTo(4, 1, 2, 4, 2, 2);
        Selection kinetics = util.select().fromTo(5, 1, 1, 3, 1, 1).add(largeCog);
        Selection sprayerKinetics = util.select().fromTo(sprayerPos, valvePos);

        scene.world().showSection(util.select().layer(0).substract(largeCog), Direction.UP);
        scene.idle(5);

        scene.world().showSection(tank, Direction.DOWN);
        scene.idle(5);

        FluidStack content = new FluidStack(Fluids.LAVA, 10000);
        scene.world().modifyBlockEntity(util.grid().at(4, 1, 2), FluidTankBlockEntity.class, be ->
                be.getTankInventory().fill(content, IFluidHandler.FluidAction.EXECUTE));
        scene.idle(10);

        scene.world().showSection(largeCog, Direction.EAST);
        scene.world().showSection(kinetics, Direction.SOUTH);
        for (int i = 0; i < 2; i++) {
            switch (i) {
                case 0 -> scene.world().showSection(util.select().position(pumpPos), Direction.DOWN);
                case 1 -> scene.world().showSection(util.select().position(sprayerPos), Direction.DOWN);
            }
            scene.idle(2 * (i + 2));
        }

        var sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, facing, 180, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, new Object(), sprayerRange, 70);
        scene.idle(10);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().blockSurface(sprayerPos, facing))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Spray range depends on their angle.");
        scene.idle(60);

        ElementLink<WorldSectionElement> valve =
                scene.world().showIndependentSection(util.select().position(valvePos), Direction.DOWN);
        scene.idle(15);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(valvePos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Their shaft input controls their angle.");
        scene.idle(60);

        scene.world().setKineticSpeed(sprayerKinetics, -32);
        scene.world().rotateSection(valve, 0, -90, 0, 10);
        scene.effects().rotationSpeedIndicator(valvePos);
        scene.world().modifyBlockEntity(sprayerPos, SprayerBlockEntity.class, be -> be.setAngle(90));
        scene.idle(10);
        scene.world().setKineticSpeed(sprayerKinetics, 0);
        sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, facing, 90, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, new Object(), sprayerRange, 40);
        scene.idle(70);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().blockSurface(sprayerPos, facing))
                .attachKeyFrame()
                .placeNearTarget()
                .text("When the angle is zero, sprayers will stop operating.");
        scene.idle(60);

        scene.world().setKineticSpeed(sprayerKinetics, -32);
        scene.world().rotateSection(valve, 0, -90, 0, 10);
        scene.effects().rotationSpeedIndicator(valvePos);
        scene.world().modifyBlockEntity(sprayerPos, SprayerBlockEntity.class, be -> be.setAngle(0));
        scene.idle(10);
        scene.world().setKineticSpeed(sprayerKinetics, 0);
        sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, facing, 90, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, new Object(), sprayerRange, 20);
        scene.idle(20);

        scene.markAsFinished();
    }

    public static void facing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sprayer.facing", "Impact of Sprayers' facing on their range");
        scene.configureBasePlate(0, 0, 5);

        BlockPos sprayerPos = util.grid().at(2, 4, 2);
        Object slot = new Object();

        scene.showBasePlate();
        scene.idle(5);

        ElementLink<WorldSectionElement> sprayer =
                scene.world().showIndependentSection(util.select().position(sprayerPos), Direction.UP);
        scene.idle(5);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(sprayerPos))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Sprayers facing different directions have different spray ranges.");
        scene.idle(70);

        var sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, Direction.WEST, 60, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, slot, sprayerRange, 40);
        scene.idle(40);

        scene.world().rotateSection(sprayer, 0, 0, -90, 5);
        sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, Direction.UP, 60, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, slot, sprayerRange, 40);
        scene.idle(40);

        scene.world().rotateSection(sprayer, -90, 0, 0, 5);
        sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, Direction.NORTH, 60, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, slot, sprayerRange, 40);
        scene.idle(40);

        scene.world().rotateSection(sprayer, -90, 0, 0, 5);
        sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, Direction.DOWN, 60, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, slot, sprayerRange, 40);
        scene.idle(40);

        scene.world().rotateSection(sprayer, 0, 0, 90, 5);
        sprayerRange = SprayerHelper.buildAABBFromAngle(sprayerPos, Direction.WEST, 60, SprayerBlockEntity.MAX_ANGLE);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, slot, sprayerRange, 40);
        scene.idle(40);

        scene.markAsFinished();
    }
}