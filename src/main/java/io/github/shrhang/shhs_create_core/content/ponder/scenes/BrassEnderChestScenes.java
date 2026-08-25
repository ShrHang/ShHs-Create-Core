package io.github.shrhang.shhs_create_core.content.ponder.scenes;

import io.github.shrhang.shhs_create_core.content.registries.ShHsBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BrassEnderChestScenes {
    public static void transit(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("brass_ender_chest.transit", "Use Brass Ender Chest to transit items");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0)
                .substract(util.select().position(2, 0, 5)), Direction.DOWN);
        scene.idle(10);

        var funnelL = util.grid().at(4, 3, 2);
        var funnelR = util.grid().at(0, 3, 2);
        var beChest = util.grid().at(3, 2, 1);
        var eChest = util.grid().at(1, 2, 1);
        var beltSetUp = util.select().fromTo(0, 1, 2, 4, 2, 5)
                .add(util.select().position(2, 0, 5));
        ItemStack apple = new ItemStack(Items.APPLE);

        ElementLink<WorldSectionElement> eChestSection =
                scene.world().showIndependentSection(util.select().position(eChest), Direction.DOWN);
        scene.world().moveSection(eChestSection, util.vector().of(0, -1, 1), 0);
        ElementLink<WorldSectionElement> beChestSection =
                scene.world().showIndependentSection(util.select().position(beChest), Direction.DOWN);
        scene.world().moveSection(beChestSection, util.vector().of(0, -1, 1), 0);
        scene.idle(15);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(2, 1, 2))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Brass Ender Chests can interact with logistics components.");
        scene.world().modifyBlock(funnelR, state -> state.setValue(FunnelBlock.EXTRACTING, false), false);
        ElementLink<WorldSectionElement> funnelLSection =
                scene.world().showIndependentSection(util.select().position(funnelL), Direction.DOWN);
        scene.world().moveSection(funnelLSection, util.vector().of(-1, -1, 0), 0);
        ElementLink<WorldSectionElement> funnelRSection =
                scene.world().showIndependentSection(util.select().position(funnelR), Direction.DOWN);
        scene.world().moveSection(funnelRSection, util.vector().of(1, -1, 0), 0);
        scene.idle(20);

        ElementLink<EntityElement> acceptedApple =
                scene.world().createItemEntity(util.vector().centerOf(3, 4, 2), util.vector().of(0, -0.2, 0), apple);
        ElementLink<EntityElement> rejectedApple =
                scene.world().createItemEntity(util.vector().centerOf(1, 4, 2), util.vector().of(0, -0.2, 0), apple);
        scene.idle(10);
        scene.world().flapFunnel(funnelL, false);
        scene.world().modifyEntity(acceptedApple, Entity::discard);
        scene.idle(30);

        scene.overlay().showControls(util.vector().centerOf(3, 2, 2), Pointing.RIGHT, 20).rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(10);
        scene.world().modifyBlock(funnelL, s -> s.cycle(FunnelBlock.EXTRACTING), true);
        scene.idle(10);
        ElementLink<EntityElement> popApple =
                scene.world().createItemEntity(util.vector().centerOf(3, 2, 2)
                        .add(0, 0.35, 0), util.vector().of(0, 0.08, 0), apple);
        scene.idle(40);

        scene.world().modifyEntity(popApple, Entity::discard);
        scene.world().modifyEntity(rejectedApple, Entity::discard);
        scene.world().hideIndependentSection(eChestSection, Direction.UP);
        scene.world().hideIndependentSection(beChestSection, Direction.UP);
        scene.world().hideIndependentSection(funnelLSection, Direction.UP);
        scene.world().hideIndependentSection(funnelRSection, Direction.UP);
        scene.idle(20);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().centerOf(1, 2, 2))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Therefore, it can be used to streamline the logistics.");
        scene.world().showSection(beltSetUp, Direction.DOWN);
        scene.world().modifyBlock(funnelL, state -> state.setValue(FunnelBlock.EXTRACTING, false), false);
        scene.world().modifyBlock(funnelR, state -> state.setValue(FunnelBlock.EXTRACTING, true), false);
        scene.world().showSection(util.select().position(funnelL), Direction.DOWN);
        scene.world().showSection(util.select().position(funnelR), Direction.DOWN);
        scene.idle(40);

        acceptedApple = scene.world().createItemEntity(util.vector().centerOf(funnelL.offset(0,2,0)), util.vector().of(0, 0, 0), apple);
        scene.idle(10);
        scene.world().flapFunnel(funnelL, false);
        scene.world().modifyEntity(acceptedApple, Entity::discard);
        scene.idle(20);

        scene.world().flapFunnel(funnelL, true);
        scene.world().createItemOnBelt(util.grid().at(3, 1, 2), Direction.EAST, apple);
        scene.idle(40);
        scene.world().removeItemsFromBelt(util.grid().at(1, 1, 2));
        scene.world().flapFunnel(funnelR, false);
        scene.idle(10);
        scene.world().flapFunnel(funnelR, true);
        popApple = scene.world().createItemEntity(util.vector().centerOf(funnelR).add(0, 0.35, 0), util.vector().of(0, 0.08, 0), apple);
        scene.idle(15);
        scene.world().modifyEntity(popApple, Entity::discard);

        var transferLine = util.select().fromTo(0, 2, 2, 4, 2, 2);
        var beltKinetics = util.select().fromTo(1, 1, 2, 3, 1, 5);
        var brassEnderChests = util.select().position(0, 2, 2)
                .add(util.select().position(4, 2, 2));

        scene.world().hideSection(transferLine.add(beltKinetics), Direction.UP);
        scene.idle(15);
        scene.world().setBlocks(brassEnderChests, ShHsBlocks.BRASS_ENDER_CHEST.getDefaultState(), false);
        scene.world().showSection(brassEnderChests, Direction.DOWN);
        scene.idle(20);

        acceptedApple = scene.world().createItemEntity(util.vector().centerOf(funnelL.offset(0,2,0)), util.vector().of(0, 0, 0), apple);
        scene.idle(10);
        scene.world().flapFunnel(funnelL, false);
        scene.world().modifyEntity(acceptedApple, Entity::discard);
        scene.idle(10);
        scene.world().createItemEntity(util.vector().centerOf(funnelR).add(0, 0.35, 0), util.vector().of(0, 0.08, 0), apple);
        scene.idle(40);
        scene.markAsFinished();
    }

    public static void feature(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("brass_ender_chest.feature", "Features of Brass Ender Chest");
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        var chest = util.grid().at(1, 1, 1);

        scene.overlay()
                .showControls(util.vector().blockSurface(chest, Direction.NORTH), Pointing.RIGHT, 15)
                .rightClick()
                .withItem(ShHsBlocks.BRASS_ENDER_CHEST.asStack());
        scene.idle(5);
        scene.world().showSection(util.select().position(chest), Direction.DOWN);
        scene.idle(20);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chest))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Brass Ender Chest will automatically link to the placer's ender chest Inventory when it's placed.");
        scene.idle(80);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chest))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Anyone who opens a Brass Ender Chest can only access its owner’s ender chest inventory.");
        scene.idle(80);

        scene.overlay().showControls(util.vector().blockSurface(chest, Direction.NORTH), Pointing.RIGHT, 60).withItem(AllItems.GOGGLES.asStack());
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chest))
                .attachKeyFrame()
                .placeNearTarget()
                .text("When wearing Engineers' Goggles, players can see the owner and lock status of this Brass Ender Chest.");
        scene.idle(80);

        scene.overlay()
                .showControls(util.vector().blockSurface(chest, Direction.NORTH), Pointing.RIGHT, 15)
                .rightClick().whileSneaking();
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chest))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Owners can right-click the brass ender chest in sneaking to toggle its locked state.");
        scene.idle(80);

        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chest))
                .attachKeyFrame()
                .placeNearTarget()
                .text("When locked, only the owner can open the brass ender chest.");
        scene.idle(80);

        scene.overlay().showControls(util.vector().blockSurface(chest, Direction.NORTH), Pointing.RIGHT, 60).withItem(AllBlocks.CLIPBOARD.asStack());
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chest))
                .attachKeyFrame()
                .placeNearTarget()
                .text("The clipboard can copy a Brass Ender Chest’s owner and lock status, then apply them to other Brass Ender Chests.");
        scene.idle(80);
        scene.markAsFinished();
    }

}
