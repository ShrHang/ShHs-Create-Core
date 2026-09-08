package io.github.shrhang.shhs_create_core.compat.jei.category;

import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import io.github.shrhang.shhs_create_core.content.kinetics.fan.processing.MiracleFanProcessingRecipe;
import io.github.shrhang.shhs_create_core.content.registries.ShHsFluids;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import static io.github.shrhang.shhs_create_core.content.data.ShHsLang.component;

public class FanMiracleCategory extends ProcessingViaFanCategory.MultiOutput<MiracleFanProcessingRecipe> {

    public FanMiracleCategory(Info<MiracleFanProcessingRecipe> info) {
        super(info);
    }

    @Override
    protected void renderAttachedBlock(GuiGraphics graphics) {
        GuiGameElement.of(ShHsFluids.MIRACLE.getSource().getFlowing())
                .scale(SCALE)
                .atLocal(0, 0, 2)
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .render(graphics);
    }

    @Override
    public Component getTitle() {
        return component("title", "fan_miracle");
    }
}
