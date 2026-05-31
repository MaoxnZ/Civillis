package civil.towncenter.gui;

import civil.towncenter.network.TownCenterC2SPayload;
import civil.towncenter.network.TownCenterGuiSyncPayload.MemberEntry;
import civil.towncenter.gui.widgets.KickMemberWidget;
import civil.towncenter.gui.widgets.MembersActionButtonWidget;
import civil.towncenter.gui.widgets.NavButtonWidget;
import civil.towncenter.gui.widgets.OpenRegistrationWidget;
import civil.towncenter.gui.widgets.TownCenterNameWidget;
import civil.towncenter.gui.widgets.TownCenterRenameButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Town center members page: open registration, list, kick, register/leave. */
public class TownCenterMembersScreen extends TownCenterScreenBase {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            "civil", "textures/gui/town_center_members_panel.png");

    private OpenRegistrationWidget openRegWidget;
    private TownCenterRenameButtonWidget renameButton;
    private MembersActionButtonWidget registerButton;
    private MembersActionButtonWidget leaveButton;
    private final List<KickMemberWidget> kickWidgets = new ArrayList<>();
    private int membersScrollRows;
    private boolean renaming;

    public TownCenterMembersScreen(TownCenterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected Identifier backgroundTexture() {
        return BACKGROUND;
    }

    @Override
    protected TownCenterGuiPage page() {
        return TownCenterGuiPage.MEMBERS;
    }

    public void setRenaming(boolean renaming) {
        this.renaming = renaming;
    }

    public void applyRenameUi() {
        boolean creator = menu.isCreator();
        if (renameButton != null) {
            renameButton.active = creator;
            renameButton.setConfirmMode(renaming);
        }
        if (nameBox instanceof TownCenterNameWidget townName) {
            townName.setFounderEditable(creator && renaming);
        }
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        menu.setPage(TownCenterGuiPage.MEMBERS);

        addRenderableWidget(new NavButtonWidget(
                this,
                TownCenterGuiPage.MAIN,
                x + TownCenterMenuLayout.BACK_BUTTON_X,
                y + TownCenterMenuLayout.BACK_BUTTON_Y,
                TownCenterMenuLayout.BACK_BUTTON_W,
                TownCenterMenuLayout.BACK_BUTTON_H,
                Component.translatable("gui.civil.town_center.back")));

        nameBox = new TownCenterNameWidget(font,
                x + TownCenterMenuLayout.MEMBERS_NAME_BOX_X,
                y + TownCenterMenuLayout.MEMBERS_NAME_FIELD_Y,
                TownCenterMenuLayout.MEMBERS_NAME_BOX_W,
                TownCenterMenuLayout.MEMBERS_NAME_BOX_H,
                Component.translatable("gui.civil.town_center.name.default"));
        nameBox.setMaxLength(32);
        nameBox.setValue(displayNameForBox());
        addRenderableWidget(nameBox);

        renameButton = addRenderableWidget(new TownCenterRenameButtonWidget(
                x + TownCenterMenuLayout.MEMBERS_RENAME_BUTTON_X,
                y + TownCenterMenuLayout.MEMBERS_RENAME_BUTTON_Y,
                TownCenterMenuLayout.NAV_BUTTON_W,
                TownCenterMenuLayout.PANEL_CHIP_H,
                this::onRenameButtonPressed));

        renaming = false;
        applyRenameUi();
        rebuildMembersWidgets();
    }

    private void onRenameButtonPressed() {
        if (!menu.isCreator()) return;
        if (renaming) {
            sendPayload(TownCenterC2SPayload.setName(menu.containerId, nameBox.getValue()));
            renaming = false;
            applyRenameUi();
        } else {
            renaming = true;
            applyRenameUi();
            nameBox.setFocused(true);
        }
    }

    @Override
    public void onProfileSync() {
        super.onProfileSync();
        rebuildMembersWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOverMembersList(mouseX, mouseY)) {
            int max = maxMemberScrollRows();
            if (max > 0) {
                membersScrollRows = Mth.clamp(membersScrollRows - (int) Math.signum(scrollY), 0, max);
                rebuildMembersWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clearMembersWidgets() {
        if (openRegWidget != null) {
            removeWidget(openRegWidget);
            openRegWidget = null;
        }
        for (KickMemberWidget kick : kickWidgets) {
            removeWidget(kick);
        }
        kickWidgets.clear();
        if (registerButton != null) {
            removeWidget(registerButton);
            registerButton = null;
        }
        if (leaveButton != null) {
            removeWidget(leaveButton);
            leaveButton = null;
        }
    }

    private void rebuildMembersWidgets() {
        clearMembersWidgets();
        membersScrollRows = Mth.clamp(membersScrollRows, 0, maxMemberScrollRows());

        int x = leftPos;
        int y = topPos;
        int listTextY = y + TownCenterMenuLayout.MEMBERS_LIST_TEXT_Y;
        int viewTop = y + TownCenterMenuLayout.MEMBERS_LIST_VIEW_Y;
        int viewBottom = viewTop + TownCenterMenuLayout.MEMBERS_LIST_VIEW_H;

        openRegWidget = addRenderableWidget(new OpenRegistrationWidget(
                this,
                x + TownCenterMenuLayout.MEMBERS_OPEN_REG_X,
                y + TownCenterMenuLayout.MEMBERS_OPEN_REG_Y,
                TownCenterMenuLayout.SUBPAGE_NARROW_CHIP_W,
                TownCenterMenuLayout.PANEL_CHIP_H,
                menu.isCreator()));

        List<MemberEntry> rows = TownCenterClientState.members();
        UUID founderUuid = founderUuid(rows);
        if (menu.isCreator()) {
            for (int i = 0; i < rows.size(); i++) {
                MemberEntry row = rows.get(i);
                if (founderUuid != null && founderUuid.equals(row.uuid())) {
                    continue;
                }
                int rowY = listTextY + (i - membersScrollRows) * TownCenterMenuLayout.MEMBERS_ROW_H;
                if (rowY + TownCenterMenuLayout.MEMBERS_ROW_H <= viewTop || rowY >= viewBottom) {
                    continue;
                }
                KickMemberWidget kick = addRenderableWidget(new KickMemberWidget(
                        this,
                        x + TownCenterMenuLayout.MEMBERS_KICK_X,
                        rowY,
                        row.uuid()));
                kickWidgets.add(kick);
            }
        }

        registerButton = addRenderableWidget(new MembersActionButtonWidget(
                this,
                x + TownCenterMenuLayout.MEMBERS_REGISTER_BUTTON_X,
                y + TownCenterMenuLayout.MEMBERS_OPEN_REG_Y,
                TownCenterMenuLayout.SUBPAGE_NARROW_CHIP_W,
                TownCenterMenuLayout.PANEL_CHIP_H,
                MembersActionButtonWidget.Kind.REGISTER));
        registerButton.active = !menu.isCreator() && !menu.isMember() && menu.isOpenRegistration();

        leaveButton = addRenderableWidget(new MembersActionButtonWidget(
                this,
                x + TownCenterMenuLayout.MEMBERS_LEAVE_BUTTON_X,
                y + TownCenterMenuLayout.MEMBERS_OPEN_REG_Y,
                TownCenterMenuLayout.SUBPAGE_NARROW_CHIP_W,
                TownCenterMenuLayout.PANEL_CHIP_H,
                MembersActionButtonWidget.Kind.LEAVE));
        leaveButton.active = menu.isMember() && !menu.isCreator() && menu.isOpenRegistration();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMembersContent(graphics);
    }

    private void renderMembersContent(GuiGraphics graphics) {
        int textX = leftPos + TownCenterMenuLayout.SUBPAGE_TEXT_X;
        drawSubpageTitle(graphics, Component.translatable("gui.civil.town_center.manage.title"));

        int viewTop = topPos + TownCenterMenuLayout.MEMBERS_LIST_VIEW_Y;
        int viewBottom = viewTop + TownCenterMenuLayout.MEMBERS_LIST_VIEW_H;
        int scissorLeft = leftPos + TownCenterMenuLayout.SUBPAGE_CONTENT_LEFT;
        int scissorRight = scissorLeft + TownCenterMenuLayout.SUBPAGE_CONTENT_WIDTH;
        graphics.enableScissor(scissorLeft, viewTop, scissorRight, viewBottom);

        List<MemberEntry> rows = TownCenterClientState.members();
        UUID founderUuid = founderUuid(rows);
        int listTextY = topPos + TownCenterMenuLayout.MEMBERS_LIST_TEXT_Y;
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.civil.town_center.members.empty"),
                    textX, listTextY,
                    TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_MUTED), false);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                int rowY = listTextY + (i - membersScrollRows) * TownCenterMenuLayout.MEMBERS_ROW_H;
                if (rowY + TownCenterMenuLayout.MEMBERS_ROW_H <= viewTop || rowY >= viewBottom) {
                    continue;
                }
                MemberEntry row = rows.get(i);
                String name = row.name();
                if (name == null || name.isBlank()) {
                    name = Component.translatable("gui.civil.town_center.creator.unknown").getString();
                }
                boolean isFounder = founderUuid != null && founderUuid.equals(row.uuid());
                if (isFounder) {
                    String tag = " [OP]";
                    name = ellipsis(
                            font,
                            name,
                            TownCenterMenuLayout.MEMBERS_NAME_MAX_W - font.width(tag)) + tag;
                } else {
                    name = ellipsis(font, name, TownCenterMenuLayout.MEMBERS_NAME_MAX_W);
                }
                graphics.drawString(font, name, textX, rowY,
                        TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT), false);
            }
        }
        graphics.disableScissor();
    }

    private static UUID founderUuid(List<MemberEntry> rows) {
        return rows.isEmpty() ? null : rows.getFirst().uuid();
    }

    private boolean isMouseOverMembersList(double mouseX, double mouseY) {
        int viewTop = topPos + TownCenterMenuLayout.MEMBERS_LIST_VIEW_Y;
        int viewBottom = viewTop + TownCenterMenuLayout.MEMBERS_LIST_VIEW_H;
        int left = leftPos + TownCenterMenuLayout.SUBPAGE_CONTENT_LEFT;
        int right = left + TownCenterMenuLayout.SUBPAGE_CONTENT_WIDTH;
        return mouseX >= left && mouseX < right && mouseY >= viewTop && mouseY < viewBottom;
    }

    private int maxMemberScrollRows() {
        int count = TownCenterClientState.members().size();
        int visible = TownCenterMenuLayout.membersListVisibleRows();
        return Math.max(0, count - visible);
    }
}
