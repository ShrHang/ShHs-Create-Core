package io.github.shrhang.shhs_create_core.compat.fluidlogistics.terminal;

import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.client.PackageResourceClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class FluidLogisticsTerminalClientCompat {
    private FluidLogisticsTerminalClientCompat() {}

    public static boolean renderAmount(GuiGraphics graphics, ItemStack stack, long amount, boolean infinite) {
        int displayAmount = infinite ? BigItemStack.INF : (int) Math.clamp(amount, 1, BigItemStack.INF - 1L);
        return PackageResourceClient.tryRenderStockKeeperAmount(graphics, stack, displayAmount);
    }

    public static Optional<List<Component>> inventoryTooltip(ItemStack stack, long amount, boolean infinite,
                                                              boolean advanced) {
        int displayAmount = infinite ? BigItemStack.INF : (int) Math.clamp(amount, 1, BigItemStack.INF - 1L);
        return PackageResources.tooltipOf(stack, displayAmount, advanced,
                PackageResourceDisplay.TooltipContext.STOCK_KEEPER_INVENTORY);
    }
}
