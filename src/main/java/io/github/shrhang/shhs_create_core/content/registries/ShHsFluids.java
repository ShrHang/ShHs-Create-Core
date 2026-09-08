package io.github.shrhang.shhs_create_core.content.registries;

import com.simibubi.create.content.fluids.VirtualFluid;
import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsFluids {

    public static final FluidEntry<VirtualFluid> HOSTILITY;
    public static final FluidEntry<BaseFlowingFluid.Flowing> MIRACLE;

    static {
        HOSTILITY = REGISTRATE.virtualFluid("hostility")
                .lang("Hostility")
                .register();

        MIRACLE = REGISTRATE.standardFluid("miracle")
                .lang("Miracle")
                .properties(p -> p.lightLevel(15).density(823))
                .fluidProperties(p -> p.tickRate(2))
                .source(BaseFlowingFluid.Source::new)
                .block()
                .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN))
                .build()
                .bucket()
                .onRegister(ShHsFluids::registerFluidDispenseBehavior)
                .tag(Tags.Items.BUCKETS)
                .build()
                .register();
    }

    private static final DispenseItemBehavior DEFAULT = new DefaultDispenseItemBehavior();

    private static final DispenseItemBehavior DISPENSE_FLUID = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispensibleContainerItem container = (DispensibleContainerItem) stack.getItem();
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            Level level = source.level();

            if (container.emptyContents(null, level, pos, null, stack)) {
                return new ItemStack(Items.BUCKET);
            }

            return DEFAULT.dispense(source, stack);
        }
    };

    private static void registerFluidDispenseBehavior(BucketItem bucket) {
        DispenserBlock.registerBehavior(bucket, DISPENSE_FLUID);
    }

    public static void register() {
    }
}
