package io.github.shrhang.shhs_create_core.compat.fluidlogistics.terminal;

import io.github.shrhang.shhs_create_core.content.logistics.terminal.TerminalData;
import com.yision.fluidlogistics.api.packager.PackageResourceType;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class FluidLogisticsTerminalOrderCompat {
    private FluidLogisticsTerminalOrderCompat() {}

    public static boolean isFluidPackage(ItemStack stack) {
        return FluidPackageItem.isFluidPackage(stack);
    }

    public static List<TerminalData.PackageEntry> inspect(ItemStack box) {
        var inspection = PackageResources.inspectPackage(box);
        List<TerminalData.PackageEntry> entries = new ArrayList<>();
        inspection.ordinaryItems().stream()
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> entries.add(new TerminalData.PackageEntry(stack, stack.getCount(), null)));
        inspection.resources().forEach(resource -> entries.add(new TerminalData.PackageEntry(
                resource.key(), resource.amount(), resource.typeId())));
        return List.copyOf(entries);
    }

    public static boolean matches(TerminalData.PackageEntry resource, ItemStack expected) {
        if (!resource.isResource())
            return false;
        PackageResourceType expectedType = PackageResources.findType(expected).orElse(null);
        return expectedType != null
                && expectedType.id().equals(resource.resourceType())
                && PackageResources.sameResource(resource.key(), expected);
    }

    public static List<ItemStack> createPackages(TerminalData.PackageEntry resource) {
        if (!resource.isResource())
            return List.of();
        PackageResourceType type = PackageResources.get(resource.resourceType()).orElse(null);
        if (type == null)
            return List.of();
        ItemStack key = type.normalizeKey(resource.key());
        int maximum = type.maxPerPackage(key.copy());
        if (maximum <= 0)
            return List.of();
        List<ItemStack> packages = new ArrayList<>();
        int remaining = resource.amount();
        while (remaining > 0) {
            int amount = Math.min(remaining, maximum);
            packages.add(PackageResources.createPackage(key.copy(), amount));
            remaining -= amount;
        }
        return List.copyOf(packages);
    }

    /** Returns the amount that could not be inserted. */
    public static long insert(DimensionsNet net, TerminalData.PackageEntry resource) {
        if (!resource.isResource() || !CompressedTankItem.isFluidStack(resource.key()))
            return resource.amount();
        var fluid = CompressedTankItem.getFluid(resource.key()).copyWithAmount(resource.amount());
        return net.getUnifiedStorage().insert(new FluidStackKey(fluid), resource.amount(), false).amount();
    }
}
