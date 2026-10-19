package civil.towncenter.gui;

/**
 * Single source of truth for {@link TownCenterMenu} slots and town center screen widgets.
 * Coordinates are GUI-local (0,0 = top-left of {@link #WIDTH}×{@link #HEIGHT} panel).
 */

public final class TownCenterMenuLayout {
    public static final int WIDTH = 248;
    public static final int HEIGHT = 184;
    public static final int SLOT_SIZE = 18;
    public static final int ATLAS_SIZE = 256;

    public static final int SLOT_DONATION_INDEX = 0;
    public static final int SLOT_INGOT_INDEX = 1;
    public static final int SLOT_HOTBAR_START = 2;
    public static final int SLOT_HOTBAR_END = 11;

    public static final int HEADER_X = 32;
    public static final int HEADER_Y = 0;
    public static final int HEADER_WIDTH = 184;
    public static final int HEADER_HEIGHT = 20;

    public static final int LEVEL_X = 20;
    public static final int LEVEL_Y = 22;
    public static final int LEVEL_WIDTH = 208;
    public static final int LEVEL_HEIGHT = 8;
    public static final int LEVEL_PAD = 3;
    public static final int LEVEL_GAP = 2;
    public static final int LEVEL_SEGMENTS = 5;
    public static final int LEVEL_FILL_TOP = LEVEL_Y + 2;
    public static final int LEVEL_FILL_BOTTOM = LEVEL_Y + LEVEL_HEIGHT - 2;

    public static final int MAIN_X = 26;
    public static final int MAIN_Y = 30;
    public static final int MAIN_WIDTH = 196;
    public static final int MAIN_HEIGHT = 106;

    public static final int TITLE_X = HEADER_X + HEADER_WIDTH / 2;
    public static final int TITLE_Y = HEADER_Y + 6;

    public static final int TOGGLE_WIDTH = 41;
    public static final int TOGGLE_HEIGHT = 12;
    public static final int TOGGLE_TRACK_HEIGHT = 8;
    public static final int TOGGLE_THUMB_SIZE = 10;
    public static final int TOGGLE_X = LEVEL_X + (LEVEL_WIDTH - TOGGLE_WIDTH + 1) / 2;
    public static final int TOGGLE_Y = MAIN_Y + 5;

    public static final int DISPLAY_NAME_Y = MAIN_Y + 8;
    public static final int DISPLAY_NAME_X = MAIN_X + 12;
    /** Left half of header row; level label is right-aligned on the same row. */
    public static final int DISPLAY_NAME_MAX_W = MAIN_WIDTH / 2 - 28;
    public static final int DISPLAY_LEVEL_RIGHT = MAIN_X + MAIN_WIDTH - 12;
    public static final int SECTION_TITLE_Y = MAIN_Y + 28;
    public static final int SECTION_TITLE_MARGIN = 6;
    public static final int SECTION_TITLE_LINE_GAP = 4;
    public static final int STATS_X = MAIN_X + 12;
    public static final int STATUS_LINE_Y = MAIN_Y + 40;
    public static final int STATS_Y = MAIN_Y + 52;
    public static final int LINE_HEIGHT = 10;
    public static final int MAIN_STATUS_LEFT_X = STATS_X;
    public static final int MAIN_STATUS_LEFT_W = MAIN_WIDTH / 2 - 18;
    public static final int MAIN_STATUS_RIGHT_X = MAIN_X + MAIN_WIDTH / 2 + 6;
    public static final int MAIN_STATUS_RIGHT_W = MAIN_WIDTH / 2 - 18;
    public static final int MAIN_STATUS_Y = STATS_Y;

    public static final int PANEL_CHIP_H = 14;
    public static final int NAV_BUTTON_W = 73;
    public static final int NAV_BUTTON_H = PANEL_CHIP_H;
    public static final int MEMBERS_BUTTON_X = MAIN_X + 12;
    public static final int MEMBERS_BUTTON_Y = MAIN_Y + MAIN_HEIGHT - 20;
    public static final int MAIN_STATUS_MAX_ROWS =
            (MEMBERS_BUTTON_Y - MAIN_STATUS_Y) / LINE_HEIGHT;
    public static final int OPTIONS_BUTTON_X = MAIN_X + MAIN_WIDTH - 84;
    public static final int OPTIONS_BUTTON_Y = MEMBERS_BUTTON_Y;

    /** Subpage chrome inset and shared vertical rhythm (Members + Options). */
    public static final int SUBPAGE_BODY_INSET = 8;
    public static final int SUBPAGE_CONTENT_LEFT = MAIN_X + SUBPAGE_BODY_INSET;
    public static final int SUBPAGE_CONTENT_WIDTH = MAIN_WIDTH - SUBPAGE_BODY_INSET * 2;
    public static final int SUBPAGE_TEXT_X = MAIN_X + 12;
    public static final int SUBPAGE_TEXT_W = MAIN_WIDTH - 24;
    public static final int SUBPAGE_BELOW_BACK_GAP = 4;
    public static final int SUBPAGE_SECOND_Y = MAIN_Y + 6 + PANEL_CHIP_H + SUBPAGE_BELOW_BACK_GAP;
    public static final int SUBPAGE_SECTION_GAP = 2;
    /** Text starts 2px below the frame interior top: frame border + 2px. */
    public static final int SUBPAGE_FRAME_TEXT_TOP_PAD = 3;
    public static final int SUBPAGE_CONTENT_BOTTOM_PAD = 6;
    public static final int SUBPAGE_FOOTER_Y = MAIN_Y + MAIN_HEIGHT - 18;
    public static final int SUBPAGE_FOOTER_CHIP_GAP = 6;
    public static final int SUBPAGE_NARROW_CHIP_W = 56;

    public static final int BACK_BUTTON_W = SUBPAGE_NARROW_CHIP_W;
    public static final int BACK_BUTTON_H = PANEL_CHIP_H;
    public static final int BACK_BUTTON_X = SUBPAGE_CONTENT_LEFT;
    public static final int BACK_BUTTON_Y = MAIN_Y + 6;
    public static final int SUBPAGE_TITLE_Y = MAIN_Y + 8;
    public static final int SUBPAGE_TITLE_LEFT_START = BACK_BUTTON_X + BACK_BUTTON_W + 10;

    public static final int MEMBERS_OPEN_REG_X = SUBPAGE_CONTENT_LEFT;
    public static final int MEMBERS_OPEN_REG_Y = SUBPAGE_FOOTER_Y;
    public static final int MEMBERS_REGISTER_BUTTON_X = MEMBERS_OPEN_REG_X + SUBPAGE_NARROW_CHIP_W + SUBPAGE_FOOTER_CHIP_GAP;
    public static final int MEMBERS_LEAVE_BUTTON_X = MEMBERS_REGISTER_BUTTON_X + SUBPAGE_NARROW_CHIP_W + SUBPAGE_FOOTER_CHIP_GAP;
    public static final int MEMBERS_ROW_H = 12;
    public static final int MEMBERS_KICK_SIZE = 12;
    public static final int MEMBERS_KICK_X = MAIN_X + MAIN_WIDTH - MEMBERS_KICK_SIZE - 10;
    public static final int MEMBERS_NAME_MAX_W = MEMBERS_KICK_X - SUBPAGE_TEXT_X - 4;

    /** Rename row: same 14px strip as the Rename chip and other panel buttons. */
    public static final int MEMBERS_RENAME_ROW_Y = SUBPAGE_SECOND_Y;
    public static final int MEMBERS_RENAME_BUTTON_Y = MEMBERS_RENAME_ROW_Y;
    public static final int MEMBERS_NAME_FIELD_H = PANEL_CHIP_H;
    public static final int MEMBERS_NAME_FIELD_Y = MEMBERS_RENAME_ROW_Y;
    public static final int MEMBERS_NAME_BOX_X = SUBPAGE_CONTENT_LEFT;
    public static final int MEMBERS_NAME_FIELD_W = SUBPAGE_CONTENT_WIDTH - NAV_BUTTON_W;
    public static final int MEMBERS_NAME_BOX_W = MEMBERS_NAME_FIELD_W;
    public static final int MEMBERS_NAME_BOX_H = MEMBERS_NAME_FIELD_H;
    public static final int MEMBERS_RENAME_BUTTON_X = MEMBERS_NAME_BOX_X + MEMBERS_NAME_FIELD_W;
    /** List frame below rename row with the same visual gap Options uses between its two regions. */
    public static final int MEMBERS_LIST_VIEW_Y = MEMBERS_RENAME_ROW_Y + PANEL_CHIP_H + SUBPAGE_SECTION_GAP;
    public static final int MEMBERS_LIST_VIEW_H = SUBPAGE_FOOTER_Y - MEMBERS_LIST_VIEW_Y - SUBPAGE_CONTENT_BOTTOM_PAD;
    public static final int MEMBERS_LIST_TEXT_TOP_PAD = SUBPAGE_FRAME_TEXT_TOP_PAD;
    public static final int MEMBERS_LIST_TEXT_Y = MEMBERS_LIST_VIEW_Y + MEMBERS_LIST_TEXT_TOP_PAD;

    /** Donation row + effect preview share the same full-width chrome (no split columns). */
    public static final int OPTIONS_CONTENT_LEFT = SUBPAGE_CONTENT_LEFT;
    public static final int OPTIONS_CONTENT_WIDTH = SUBPAGE_CONTENT_WIDTH;
    /** Column centers for donation/ingot slots only (not the full-width chrome inset). */
    public static final int OPTIONS_BODY_PAD = 12;
    public static final int OPTIONS_COLUMN_GAP = 8;
    public static final int OPTIONS_COLUMN_W = (MAIN_WIDTH - OPTIONS_BODY_PAD * 2 - OPTIONS_COLUMN_GAP) / 2;
    public static final int OPTIONS_LEFT_COL_X = MAIN_X + OPTIONS_BODY_PAD;
    public static final int OPTIONS_RIGHT_COL_X = OPTIONS_LEFT_COL_X + OPTIONS_COLUMN_W + OPTIONS_COLUMN_GAP;
    public static final int OPTIONS_SLOTS_FRAME_Y = SUBPAGE_SECOND_Y;
    public static final int OPTIONS_SLOTS_FRAME_H = 22;
    public static final int OPTIONS_SLOTS_ROW_Y = OPTIONS_SLOTS_FRAME_Y + SUBPAGE_SECTION_GAP + 1;
    public static final int OPTIONS_DONATION_SLOT_X = OPTIONS_LEFT_COL_X + (OPTIONS_COLUMN_W - SLOT_SIZE) / 2;
    public static final int OPTIONS_DONATION_SLOT_Y = OPTIONS_SLOTS_ROW_Y;
    public static final int OPTIONS_INGOT_SLOT_X = OPTIONS_RIGHT_COL_X + (OPTIONS_COLUMN_W - SLOT_SIZE) / 2;
    public static final int OPTIONS_INGOT_SLOT_Y = OPTIONS_SLOTS_ROW_Y;
    public static final int OPTIONS_PREVIEW_VIEW_Y = OPTIONS_SLOTS_FRAME_Y + OPTIONS_SLOTS_FRAME_H + SUBPAGE_SECTION_GAP;
    public static final int OPTIONS_PREVIEW_TEXT_Y = OPTIONS_PREVIEW_VIEW_Y + SUBPAGE_FRAME_TEXT_TOP_PAD;
    public static final int OPTIONS_SLOT_ROW_TEXT_Y = OPTIONS_SLOTS_ROW_Y + (SLOT_SIZE - 8) / 2;
    public static final int OPTIONS_COST_HINT_SQUARE = 7;
    public static final int OPTIONS_COST_HINT_GAP = 3;
    public static final int OPTIONS_COST_HINT_LEFT_OFFSET = 3;
    public static final int OPTIONS_SLOT_CONNECTOR_GAP = 8;
    public static final int OPTIONS_TEXT_X = SUBPAGE_TEXT_X;
    public static final int OPTIONS_TEXT_W = SUBPAGE_TEXT_W;
    public static final int OPTIONS_EFFECT_ZONE_BOTTOM = SUBPAGE_FOOTER_Y - SUBPAGE_CONTENT_BOTTOM_PAD;
    public static final int OPTIONS_CONFIRM_X = (WIDTH - NAV_BUTTON_W) / 2;
    public static final int OPTIONS_CONFIRM_Y = SUBPAGE_FOOTER_Y;

    public static final int DONATION_SLOT_X = OPTIONS_DONATION_SLOT_X;
    public static final int DONATION_SLOT_Y = OPTIONS_DONATION_SLOT_Y;
    public static final int INGOT_SLOT_X = OPTIONS_INGOT_SLOT_X;
    public static final int INGOT_SLOT_Y = OPTIONS_INGOT_SLOT_Y;

    public static final int HOTBAR_RIM_PAD = 3;
    public static final int HOTBAR_ROW_W = 9 * SLOT_SIZE;
    public static final int HOTBAR_RIM_W = HOTBAR_ROW_W + HOTBAR_RIM_PAD * 2 + 2;
    public static final int HOTBAR_RIM_H = SLOT_SIZE + HOTBAR_RIM_PAD * 2 + 2;
    public static final int HOTBAR_RIM_X = (WIDTH - HOTBAR_RIM_W) / 2;
    public static final int HOTBAR_RIM_Y = 151;
    public static final int HOTBAR_INNER_W = HOTBAR_ROW_W + HOTBAR_RIM_PAD * 2;
    public static final int HOTBAR_INNER_H = SLOT_SIZE + HOTBAR_RIM_PAD * 2;
    public static final int HOTBAR_X = HOTBAR_RIM_X + 1 + (HOTBAR_INNER_W - HOTBAR_ROW_W) / 2 + 1;
    public static final int HOTBAR_Y = HOTBAR_RIM_Y + 1 + (HOTBAR_INNER_H - SLOT_SIZE) / 2 + 1;

    private TownCenterMenuLayout() {}

    public static int levelCellWidth() {
        int inner = LEVEL_WIDTH - LEVEL_PAD * 2;
        return (inner - LEVEL_GAP * (LEVEL_SEGMENTS - 1)) / LEVEL_SEGMENTS;
    }

    public static int membersListVisibleRows() {
        return Math.max(1, MEMBERS_LIST_VIEW_H / MEMBERS_ROW_H);
    }

    public static int levelCellStart(int index) {
        int inner = LEVEL_WIDTH - LEVEL_PAD * 2;
        int cellW = levelCellWidth();
        int used = LEVEL_SEGMENTS * cellW + (LEVEL_SEGMENTS - 1) * LEVEL_GAP;
        int start = LEVEL_X + LEVEL_PAD + (inner - used) / 2;
        return start + index * (cellW + LEVEL_GAP);
    }

    /** Right edge of donation slot (cost cluster / level hint anchor). */
    public static int optionsDonationSlotRightX() {
        return OPTIONS_DONATION_SLOT_X + SLOT_SIZE;
    }

    /** Left edge of ingot slot (level hint horizontal center anchor). */
    public static int optionsIngotSlotLeftX() {
        return OPTIONS_INGOT_SLOT_X;
    }
}
