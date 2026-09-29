package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ShHsCreateCore.MODID)
@PrefixGameTestTemplate(false)
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
    public static void terminal_local_virtual_slot_conserves_items(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        DimensionsNet net = DimensionsNet.createNewNetForPlayer(player, 100000, 100);
        helper.assertTrue(net != null, "Create primary network");
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        if (net != null) {
            net.getUnifiedStorage().insert(new ItemStackKey(iron), 130, false);
        }
        DimensionLogisticsTerminalMenu menu = new DimensionLogisticsTerminalMenu(
                7, player.getInventory(), net.getId(), UUID.randomUUID());
        menu.clickLocal(iron, 0, false);
        helper.assertTrue(menu.getCarried().getCount() == 64
                && net.getUnifiedStorage().getStackByKey(new ItemStackKey(iron)).amount() == 66,
                "Left click takes one maximum stack");
        menu.clickLocal(iron, 1, false);
        helper.assertTrue(menu.getCarried().getCount() == 63
                && net.getUnifiedStorage().getStackByKey(new ItemStackKey(iron)).amount() == 67,
                "Right click deposits one matching carried item");
        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        menu.clickLocal(iron, 0, false);
        helper.assertTrue(menu.getCarried().isEmpty()
                        && net.getUnifiedStorage().getStackByKey(new ItemStackKey(iron)).amount() == 67,
                "Left click with a different cursor stack only deposits it");
        helper.assertTrue(net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.DIAMOND))).amount() == 5,
                "Left-clicked cursor items enter the dimension network");
        menu.setCarried(new ItemStack(Items.EMERALD, 4));
        menu.clickLocal(iron, 1, false);
        helper.assertTrue(menu.getCarried().isEmpty()
                        && net.getUnifiedStorage().getStackByKey(new ItemStackKey(iron)).amount() == 67
                        && net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.EMERALD))).amount() == 4,
                "Right click with a different cursor stack only deposits it");
        player.getInventory().clearContent();
        menu.setCarried(ItemStack.EMPTY);
        menu.clickLocal(iron, 0, true);
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 64
                        && net.getUnifiedStorage().getStackByKey(new ItemStackKey(iron)).amount() == 3,
                "Shift click transfers one maximum stack of the remaining local stock");

        DimensionLogisticsTerminalLayout.RightPanels full =
                DimensionLogisticsTerminalLayout.rightPanels(276);
        int[] fullGaps = {
                full.orderY() - DimensionLogisticsTerminalLayout.HEADER_TEXTURE_HEIGHT,
                full.craftY() - full.orderY() - DimensionLogisticsTerminalLayout.ORDER_TEXTURE_HEIGHT,
                full.playerY() - full.craftY() - DimensionLogisticsTerminalLayout.CRAFT_TEXTURE_HEIGHT,
                full.bottomY() - full.playerY() - DimensionLogisticsTerminalLayout.PLAYER_TEXTURE_HEIGHT
        };
        helper.assertTrue(full.modulesCanShare()
                        && Arrays.stream(fullGaps).max().orElseThrow()
                        - Arrays.stream(fullGaps).min().orElseThrow() <= 1,
                "Expanded layout distributes all four module gaps evenly");

        DimensionLogisticsTerminalLayout.RightPanels compact =
                DimensionLogisticsTerminalLayout.rightPanels(256);
        helper.assertTrue(!compact.modulesCanShare() && compact.orderCanShow() && compact.craftCanShow()
                        && compact.orderY() + DimensionLogisticsTerminalLayout.ORDER_TEXTURE_HEIGHT <= compact.playerY()
                        && compact.craftY() + DimensionLogisticsTerminalLayout.CRAFT_TEXTURE_HEIGHT <= compact.playerY(),
                "Compact layout keeps both switchable modules at fixed non-overlapping positions");
        helper.succeed();
    }

    @GameTest(template = "terminal_empty", batch = "terminal")
    public static void terminal_snapshot_exposes_dimension_fluids_as_read_only(GameTestHelper helper) {
        if (!ModList.get().isLoaded("fluidlogistics")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        DimensionsNet net = DimensionsNet.createNewNetForPlayer(player, 100000, 100);
        helper.assertTrue(net != null, "Create primary network");
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        net.getUnifiedStorage().insert(new FluidStackKey(water), 4_000, false);
        TerminalStock.Entry entry = TerminalStock.snapshot(player, net).entries().stream()
                .filter(candidate -> candidate.network() == null && !candidate.requestable()
                        && candidate.stack().getHoverName().getString().equals(water.getHoverName().getString()))
                .findFirst().orElse(null);
        helper.assertTrue(entry != null && entry.amount() == 4_000,
                "Dimension fluid appears as a read-only terminal entry");
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
