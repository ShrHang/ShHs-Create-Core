package io.github.shrhang.shhs_create_core.content.logistics.terminal;

final class DimensionLogisticsTerminalLayout {
    static final int LEFT_PANEL_WIDTH = 226;
    static final int RIGHT_PANEL_WIDTH = 192;
    static final int WINDOW_WIDTH = LEFT_PANEL_WIDTH + RIGHT_PANEL_WIDTH;
    static final int MIN_WINDOW_HEIGHT = 256;
    static final int LAYOUT_MARGIN = 12;
    static final int JEI_RESERVED_WIDTH = RIGHT_PANEL_WIDTH;

    static final int RIGHT_PANEL_X = LEFT_PANEL_WIDTH;
    static final int COMPACT_RIGHT_PANEL_X = (LEFT_PANEL_WIDTH - RIGHT_PANEL_WIDTH) / 2;
    static final int HEADER_HEIGHT = 16;
    static final int BACKGROUND_SLICE_HEIGHT = 16;
    static final int CRAFT_TEXTURE_HEIGHT = 56;
    static final int PLAYER_TEXTURE_HEIGHT = 90;
    static final int CRAFT_PANEL_Y = HEADER_HEIGHT;
    static final int PLAYER_PANEL_Y = CRAFT_PANEL_Y + roundedSectionHeight(CRAFT_TEXTURE_HEIGHT);

    static final int CRAFT_INPUT_X = RIGHT_PANEL_X + 34; // 3*3合成输入槽位x坐标
    static final int CRAFT_INPUT_Y = CRAFT_PANEL_Y + 3; // 3*3合成输入槽位y坐标
    static final int CRAFT_RESULT_X = RIGHT_PANEL_X + 128; // 3*3合成结果槽位x坐标
    static final int CRAFT_RESULT_Y = CRAFT_PANEL_Y + 20; // 3*3合成结果槽位y坐标
    static final int PLAYER_INVENTORY_X = RIGHT_PANEL_X + 16;
    static final int PLAYER_INVENTORY_Y = PLAYER_PANEL_Y + 7;
    static final int PLAYER_HOTBAR_Y = PLAYER_PANEL_Y + 65;

    private DimensionLogisticsTerminalLayout() {}

    private static int roundedSectionHeight(int foregroundHeight) {
        return (foregroundHeight + BACKGROUND_SLICE_HEIGHT - 1) / BACKGROUND_SLICE_HEIGHT
                * BACKGROUND_SLICE_HEIGHT;
    }
}
