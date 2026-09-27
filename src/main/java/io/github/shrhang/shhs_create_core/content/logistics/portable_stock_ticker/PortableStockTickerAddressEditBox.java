package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public class PortableStockTickerAddressEditBox extends EditBox {
    private final DestinationSuggestions suggestions;
    private Consumer<String> mainResponder;
    private String previousValue = "=)";

    public PortableStockTickerAddressEditBox(Screen screen, Font font, int x, int y, int width, int height,
                                             List<String> addresses) {
        super(font, x, y, width, height, Component.empty());
        List<IntAttached<String>> options = addresses.stream().map(IntAttached::withZero).toList();
        suggestions = new DestinationSuggestions(Minecraft.getInstance(), screen, this, font, options, true,
                -72 + y);
        suggestions.setAllowSuggestions(true);
        suggestions.updateCommandInfo();
        mainResponder = value -> {
            if (!value.equals(previousValue)) {
                suggestions.updateCommandInfo();
            }
            previousValue = value;
        };
        setResponder(mainResponder);
        setBordered(false);
        setFocused(false);
        mouseClicked(0, 0, 0);
        setMaxLength(64);
    }

    public void tick() {
        if (!isFocused()) {
            suggestions.hide();
        }
        suggestions.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (suggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
            setFocused(false);
            moveCursorToEnd(false);
            mouseClicked(0, 0, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (suggestions.mouseScrolled(Mth.clamp(scrollY, -1, 1))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isMouseOver(mouseX, mouseY)) {
            setValue("");
            return true;
        }

        boolean wasFocused = isFocused();
        if (super.mouseClicked(mouseX, mouseY, button)) {
            if (!wasFocused) {
                setHighlightPos(0);
                setCursorPosition(getValue().length());
            }
            return true;
        }
        return suggestions.mouseClicked((int) mouseX, (int) mouseY, button);
    }

    @Override
    public void setValue(String text) {
        setHighlightPos(0);
        super.setValue(text);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 500);
        suggestions.render(graphics, mouseX, mouseY);
        pose.popPose();
    }

    @Override
    public void setResponder(Consumer<String> responder) {
        super.setResponder(responder == mainResponder ? mainResponder : mainResponder.andThen(responder));
    }
}
