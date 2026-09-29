package io.github.shrhang.shhs_create_core.content.logistics.terminal;

final class DimensionLogisticsTerminalLayout {
    static final int LEFT_PANEL_WIDTH = 226;
    static final int RIGHT_PANEL_WIDTH = 192;
    static final int WINDOW_WIDTH = LEFT_PANEL_WIDTH + RIGHT_PANEL_WIDTH;
    static final int LAYOUT_MARGIN = 12;
    static final int JEI_RESERVED_WIDTH = RIGHT_PANEL_WIDTH;

    static final int STOCK_HEADER_HEIGHT = 36;
    static final int STOCK_BODY_HEIGHT = 20;
    static final int STOCK_FOOTER_HEIGHT = 80;
    static final int MAX_STOCK_BODY_SLICES = 17;

    static final int RIGHT_PANEL_X = LEFT_PANEL_WIDTH;
    static final int COMPACT_RIGHT_PANEL_X = (LEFT_PANEL_WIDTH - RIGHT_PANEL_WIDTH) / 2;
    static final int HEADER_TEXTURE_HEIGHT = 16;
    static final int BACKGROUND_SLICE_HEIGHT = 16;
    static final int ORDER_TEXTURE_HEIGHT = 62;
    static final int CRAFT_TEXTURE_HEIGHT = 72;
    static final int PLAYER_TEXTURE_HEIGHT = 90;
    static final int BOTTOM_TEXTURE_HEIGHT = 24;

    static final int CRAFT_INPUT_X = 32;
    static final int CRAFT_INPUT_Y = 5;
    static final int CRAFT_SLOT_STEP = 20;
    static final int CRAFT_RESULT_X = 128;
    static final int CRAFT_RESULT_Y = 24;
    static final int PLAYER_INVENTORY_X = 16;
    static final int PLAYER_INVENTORY_Y = 7;
    static final int PLAYER_HOTBAR_Y = 65;

    static final int ORDER_TOGGLE_X = 149;
    static final int CRAFT_TOGGLE_X = 169;
    static final int MODULE_TOGGLE_Y = 4;
    static final int MODULE_TOGGLE_SIZE = 16;

    private DimensionLogisticsTerminalLayout() {}

    static int windowHeight(int screenHeight) {
        int minimum = STOCK_HEADER_HEIGHT + STOCK_BODY_HEIGHT + STOCK_FOOTER_HEIGHT;
        int appropriate = Math.max(minimum, screenHeight - 10);
        appropriate -= Math.floorMod(appropriate - STOCK_HEADER_HEIGHT - STOCK_FOOTER_HEIGHT, STOCK_BODY_HEIGHT);
        return Math.min(appropriate, STOCK_HEADER_HEIGHT + STOCK_FOOTER_HEIGHT
                + STOCK_BODY_HEIGHT * MAX_STOCK_BODY_SLICES);
    }

    static RightPanels rightPanels(int windowHeight) {
        int bottomY = windowHeight - BOTTOM_TEXTURE_HEIGHT;
        int baseHeight = HEADER_TEXTURE_HEIGHT + PLAYER_TEXTURE_HEIGHT + BOTTOM_TEXTURE_HEIGHT;
        boolean orderCanShow = windowHeight >= baseHeight + ORDER_TEXTURE_HEIGHT;
        boolean craftCanShow = windowHeight >= baseHeight + CRAFT_TEXTURE_HEIGHT;
        boolean modulesCanShare = windowHeight >= baseHeight + ORDER_TEXTURE_HEIGHT + CRAFT_TEXTURE_HEIGHT;

        if (modulesCanShare) {
            int free = windowHeight - baseHeight - ORDER_TEXTURE_HEIGHT - CRAFT_TEXTURE_HEIGHT;
            int orderY = HEADER_TEXTURE_HEIGHT + gap(free, 4, 0);
            int craftY = orderY + ORDER_TEXTURE_HEIGHT + gap(free, 4, 1);
            int playerY = craftY + CRAFT_TEXTURE_HEIGHT + gap(free, 4, 2);
            return new RightPanels(orderY, craftY, playerY, bottomY,
                    true, true, true);
        }

        int optionalHeight = craftCanShow ? CRAFT_TEXTURE_HEIGHT
                : orderCanShow ? ORDER_TEXTURE_HEIGHT : 0;
        if (optionalHeight == 0) {
            int free = Math.max(0, windowHeight - baseHeight);
            int playerY = HEADER_TEXTURE_HEIGHT + gap(free, 2, 0);
            return new RightPanels(HEADER_TEXTURE_HEIGHT, HEADER_TEXTURE_HEIGHT,
                    playerY, bottomY, false, false, false);
        }

        int free = windowHeight - baseHeight - optionalHeight;
        int optionalY = HEADER_TEXTURE_HEIGHT + gap(free, 3, 0);
        int orderY = optionalY + (optionalHeight - ORDER_TEXTURE_HEIGHT) / 2;
        int playerY = optionalY + optionalHeight + gap(free, 3, 1);
        return new RightPanels(orderY, optionalY, playerY, bottomY,
                false, orderCanShow, craftCanShow);
    }

    private static int gap(int free, int gapCount, int index) {
        return free / gapCount + (index < free % gapCount ? 1 : 0);
    }

    record RightPanels(int orderY, int craftY, int playerY, int bottomY,
                       boolean modulesCanShare, boolean orderCanShow, boolean craftCanShow) {}
}
