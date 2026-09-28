package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.registries.ShHsBlocks;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ShHsCreateCore.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public class TerminalGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        return new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "terminal-test"));
    }
    public static void prepareTemplate(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        // GameTestServer prepares structures before invoking BeforeBatch.
        try { emptyTemplate(event.getServer().overworld()); }
        catch (Exception exception) { throw new IllegalStateException("Cannot create terminal test template", exception); }
    }

    @BeforeBatch(batch = "terminal")
    public static void emptyTemplate(ServerLevel level) throws Exception {
        var tag = TagParser.parseTag("{size:[8,4,8],palette:[{Name:\"minecraft:air\"}],blocks:[],entities:[]}");
        level.getStructureManager().getOrCreate(ShHsCreateCore.rl("terminal_empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = "terminal_empty", batch = "terminal")
    public static void terminal_local_order_conserves_items(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        DimensionsNet net = DimensionsNet.createNewNetForPlayer(player, 100000, 100);
        helper.assertTrue(net != null, "Create primary network");
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        net.getUnifiedStorage().insert(new ItemStackKey(iron), 1300, false);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        TerminalOrders service = new TerminalOrders();
        UUID submission = UUID.randomUUID();
        List<TerminalStock.Entry> request = List.of(new TerminalStock.Entry(iron, 1300, null, true));
        helper.assertTrue(service.submit(player, net, submission, request), "Submit local order");
        helper.assertTrue(service.submit(player, net, submission, request), "Retry must be idempotent");
        helper.assertTrue(net.getUnifiedStorage().getStackByKey(new ItemStackKey(iron)).amount() == 0, "Withdraw exactly once");
        helper.assertTrue(service.forPlayer(player.getUUID()).getFirst().packages.size() == 3, "1300 iron needs three nine-slot packages");
        TerminalOrders restored = TerminalOrders.load(service.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
        ServerPlayer other = player(helper);
        restored.claim(other, submission, true);
        helper.assertTrue(restored.forPlayer(player.getUUID()).getFirst().packages.size() == 3, "Other player cannot claim");
        player.getInventory().clearContent();
        restored.claim(player, submission, false);
        restored.claim(player, submission, false);
        int actual = 0;
        for (ItemStack box : player.getInventory().items) if (box.getItem() instanceof PackageItem) {
            var contents = PackageItem.getContents(box);
            for (int i = 0; i < contents.getSlots(); i++) actual += contents.getStackInSlot(i).getCount();
        }
        helper.assertTrue(actual == 1300 && restored.forPlayer(player.getUUID()).isEmpty(), "No lost or duplicated items after restore and repeated claim");
        helper.succeed();
    }

    @GameTest(template = "terminal_empty", batch = "terminal")
    public static void terminal_receipt_simulation_and_late_delivery(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BlockPos local = new BlockPos(1, 1, 1);
        helper.setBlock(local, ShHsBlocks.DIMENSION_PARCEL_STATION.get().defaultBlockState());
        helper.setBlock(local.east(), AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
        BlockPos target = helper.absolutePos(local);
        UUID orderId = UUID.randomUUID();
        CompoundTag order = new CompoundTag();
        order.putUUID("Id", orderId); order.putUUID("Owner", player.getUUID()); order.putInt("Net", -1);
        CompoundTag shipment = new CompoundTag();
        shipment.putInt("Id", -100); shipment.putUUID("Network", UUID.randomUUID());
        shipment.putString("Dimension", helper.getLevel().dimension().location().toString());
        shipment.putLong("Pos", target.asLong()); shipment.putString("Address", "test-terminal");
        shipment.put("Remaining", TerminalData.stacks(List.of(new ItemStack(Items.DIAMOND, 8)), player.registryAccess()));
        ListTag shipments = new ListTag(); shipments.add(shipment); order.put("Shipments", shipments);
        ListTag orders = new ListTag(); orders.add(order); CompoundTag saved = new CompoundTag(); saved.put("Orders", orders);
        TerminalOrders service = TerminalOrders.load(saved, player.registryAccess());
        player.server.overworld().getDataStorage().set("ShHsTerminalOrders", service);
        ItemStack box = PackageItem.containing(List.of(new ItemStack(Items.DIAMOND, 4)));
        PackageItem.addAddress(box, "test-terminal"); PackageItem.setOrder(box, -100, 0, true, 0, false, null);
        PackagerBlockEntity packager = (PackagerBlockEntity) helper.getLevel().getBlockEntity(target.east());
        helper.assertTrue(packager.unwrapBox(box, true), "Mixin routes simulation to terminal order");
        helper.assertTrue(service.forPlayer(player.getUUID()).getFirst().held.isEmpty(), "Simulation must not collect");
        helper.assertTrue(packager.unwrapBox(box, false), "Actual package received via mixin");
        helper.assertTrue(Boolean.FALSE.equals(service.receive(helper.getLevel(), target, box, false)), "Duplicate fragment rejected");
        helper.assertTrue(service.forPlayer(player.getUUID()).getFirst().held.getFirst().getCount() == 4, "Exactly four diamonds held");
        service.claim(player, orderId, true);
        PackageItem.setOrder(box, -100, 0, true, 1, true, null);
        helper.assertTrue(Boolean.TRUE.equals(service.receive(helper.getLevel(), target, box, false)), "Late delivery accepted");
        helper.assertTrue(service.forPlayer(player.getUUID()).getFirst().held.getFirst().getCount() == 4, "Missing original network retains late items for owner");
        helper.succeed();
    }

    @GameTest(template = "terminal_empty", batch = "terminal")
    public static void terminal_recipe_sources_and_components(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        DimensionsNet net = DimensionsNet.createNewNetForPlayer(player, 10000, 100);
        net.getUnifiedStorage().insert(new ItemStackKey(new ItemStack(Items.OAK_PLANKS)), 10, false);
        DimensionLogisticsTerminalMenu menu = new DimensionLogisticsTerminalMenu(7, player.getInventory(), net.getId(), UUID.randomUUID());
        menu.fillRecipe(ResourceLocation.withDefaultNamespace("stick"), false);
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.STICK), "JEI-style fill finds stick recipe");
        long before = net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.OAK_PLANKS))).amount();
        ItemStack result = menu.getSlot(0).remove(4);
        menu.getSlot(0).onTake(player, result);
        helper.assertTrue(net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.OAK_PLANKS))).amount() == before - 2, "Crafting refills from dimension network");
        menu.clearCrafting();
        ItemStack renamed = new ItemStack(Items.IRON_INGOT); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        helper.assertFalse(new ItemStackKey(renamed).equals(new ItemStackKey(new ItemStack(Items.IRON_INGOT))), "Component variants stay distinct");
        var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.STICK),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS), Ingredient.of(Items.OAK_PLANKS)));
        var pool = Map.of(new ItemStackKey(new ItemStack(Items.OAK_PLANKS)), 1L, new ItemStackKey(new ItemStack(Items.BIRCH_PLANKS)), 1L);
        helper.assertTrue(TerminalCrafting.plan(recipe, pool) != null, "Overlapping ingredients allocate without double use");
        helper.assertTrue(TerminalCrafting.plan(recipe, Map.of(new ItemStackKey(new ItemStack(Items.OAK_PLANKS)), 1L)) == null, "Insufficient materials rejected");
        helper.succeed();
    }
}
