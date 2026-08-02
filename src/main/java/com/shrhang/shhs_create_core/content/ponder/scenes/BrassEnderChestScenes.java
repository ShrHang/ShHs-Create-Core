package com.shrhang.shhs_create_core.content.ponder.scenes;

import com.shrhang.shhs_create_core.content.registries.ShHsBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class BrassEnderChestScenes {
    public static void intro(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("brass_ender_chest.intro", "Features of Brass Ender Chest");
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
    }

    public static void transit(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("brass_ender_chest.transit", "Use Brass Ender Chest to transit items");
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        var chestR = util.grid().at(1, 1, 1);
        var funnelR = util.grid().at(1, 2, 1);
        var chestL = util.grid().at(3, 1, 1);
        var funnelL = util.grid().at(3, 2, 1);

        scene.overlay()
                .showControls(util.vector().blockSurface(chestR, Direction.NORTH), Pointing.RIGHT, 15)
                .rightClick()
                .withItem(new ItemStack(ShHsBlocks.BRASS_ENDER_CHEST.asItem()));
        scene.idle(5);
        scene.world().showSection(util.select().position(chestR), Direction.DOWN);
        scene.idle(20);
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chestR))
                .attachKeyFrame()
                .placeNearTarget()
                .text("When a Brass Ender Chest is placed, it will automatically link to the placer's Ender Chest Inventory.");
        scene.idle(80);
        scene.overlay().showControls(util.vector().blockSurface(chestR, Direction.NORTH), Pointing.RIGHT, 60).withItem(AllItems.GOGGLES.asStack());
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(chestR))
                .attachKeyFrame()
                .placeNearTarget()
                .text("When wearing Engineers' Goggles, players can see the owner and lock status of this Brass Ender Chest.");
        scene.idle(80);
    }
}
