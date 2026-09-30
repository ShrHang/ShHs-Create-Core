package io.github.shrhang.shhs_create_core.content.logistics.dimension_parcel_station;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class DimensionParcelStationScreen extends AbstractContainerScreen<DimensionParcelStationMenu> {
    private final Map<DimensionParcelStationBlockEntity.Channel, Button> buttons =
            new EnumMap<>(DimensionParcelStationBlockEntity.Channel.class);
    private EditBox address;
    private Button networkButton;
    private Button saveAddress;
    private UUID selectedNetwork;
    private int routeIndex;

    public DimensionParcelStationScreen(DimensionParcelStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = ModList.get().isLoaded("fluidlogistics") ? 222 : 196;
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        addToggle(DimensionParcelStationBlockEntity.Channel.ITEM_INPUT, leftPos + 12, topPos + 54);
        addToggle(DimensionParcelStationBlockEntity.Channel.ITEM_OUTPUT, leftPos + 108, topPos + 54);
        if (ModList.get().isLoaded("fluidlogistics")) {
            addToggle(DimensionParcelStationBlockEntity.Channel.FLUID_INPUT, leftPos + 12, topPos + 82);
            addToggle(DimensionParcelStationBlockEntity.Channel.FLUID_OUTPUT, leftPos + 108, topPos + 82);
        }
        int y = topPos + imageHeight - 94;
        networkButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            routeIndex++;
            selectedNetwork = null;
            updateRoute();
        }).bounds(leftPos + 12, y, 236, 20).build());
        address = new EditBox(font, leftPos + 12, y + 27, 168, 18, text("address"));
        address.setMaxLength(64);
        addRenderableWidget(address);
        saveAddress = addRenderableWidget(Button.builder(text("save"), ignored -> {
            if (selectedNetwork == null)
                return;
            DimensionParcelStationPackets.requestAddress(menu, selectedNetwork, address.getValue());
        }).bounds(leftPos + 185, y + 26, 63, 20).build());
        updateRoute();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("text.shhs_create_core.terminal." + key, args);
    }

    private void updateRoute() {
        int count = menu.clientRoutes.size();
        networkButton.active = count > 1;
        saveAddress.active = count > 0 && menu.mayConfigure();
        address.setEditable(saveAddress.active);
        if (count == 0) {
            selectedNetwork = null;
            networkButton.setMessage(text("no_routes"));
            return;
        }
        routeIndex = Math.floorMod(routeIndex, count);
        var route = menu.clientRoutes.getCompound(routeIndex);
        UUID network = route.getUUID("Network");
        if (!network.equals(selectedNetwork)) {
            address.setValue(route.getString("Address"));
            selectedNetwork = network;
        }
        networkButton.setMessage(text(
                "route", routeIndex + 1, count, network.toString().substring(0, 8)));
    }

    private void addToggle(DimensionParcelStationBlockEntity.Channel channel, int x, int y) {
        Button button = Button.builder(label(channel), ignored -> PacketDistributor.sendToServer(new DimensionParcelStationPackets.Toggle(
                menu.getPos(), channel.ordinal())))
                .bounds(x, y, 90, 20)
                .build();
        button.active = menu.mayConfigure();
        buttons.put(channel, addRenderableWidget(button));
    }

    private Component label(DimensionParcelStationBlockEntity.Channel channel) {
        return Component.translatable("text.shhs_create_core.dimension_parcel_station."
                        + channel.name().toLowerCase())
                .append(": ")
                .append(Component.translatable("text.shhs_create_core.dimension_parcel_station."
                        + (menu.isAllowed(channel) ? "enabled" : "disabled")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateRoute();
        buttons.forEach((channel, button) -> {
            button.setMessage(label(channel));
            button.active = menu.mayConfigure();
        });
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff202b32);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xff39464f);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 9, 0xffffff, false);
        Component network = menu.getNetId() < 0
                ? Component.translatable("text.shhs_create_core.dimension_parcel_station.unbound_status")
                : Component.translatable("text.shhs_create_core.dimension_parcel_station.network_status",
                menu.getNetId(), menu.getStationCount());
        graphics.drawString(font, network, 10, 28, 0xd7e7ee, false);
        graphics.drawString(font, text("address_hint"), 12, imageHeight - 38, 0xd7e7ee, false);
        if (!menu.mayConfigure())
            graphics.drawString(font, Component.translatable(
                    "text.shhs_create_core.dimension_parcel_station.read_only"), 10,
                    imageHeight - 18, 0xffb45e, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
