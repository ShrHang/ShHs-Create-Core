package io.github.shrhang.shhs_create_core.content.logistics.terminal;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

@OnlyIn(Dist.CLIENT)
final class DimensionLogisticsTerminalClientLayout {
    private DimensionLogisticsTerminalClientLayout() {}

    static Snapshot create() {
        var window = Minecraft.getInstance().getWindow();
        int requiredWidth = DimensionLogisticsTerminalLayout.WINDOW_WIDTH
                + DimensionLogisticsTerminalLayout.LAYOUT_MARGIN;
        if (ModList.get().isLoaded("jei"))
            requiredWidth += DimensionLogisticsTerminalLayout.JEI_RESERVED_WIDTH;
        return new Snapshot(window.getGuiScaledWidth() < requiredWidth,
                DimensionLogisticsTerminalLayout.windowHeight(window.getGuiScaledHeight()));
    }

    record Snapshot(boolean compact, int windowHeight) {}
}
