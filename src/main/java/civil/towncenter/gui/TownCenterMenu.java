package civil.towncenter.gui;

import civil.CivilServices;
import civil.ModMenuTypes;
import civil.civilization.TownCenterStructureValidator;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.towncenter.TownCenterActivationHandler;
import civil.towncenter.TownCenterLevelTable;
import civil.towncenter.network.TownCenterMenuViewers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;

/**
 * Town center: donation + ingot slots, hotbar row, activation toggle via menu button.
 */
public class TownCenterMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_ACTIVATION = 0;
    private static final int DATA_LEVEL = 0;
    private static final int DATA_UPGRADE_COST = 1;
    private static final int DATA_GAMEPLAY_ACTIVE = 2;
    private static final int DATA_SHUTDOWN_SECONDS = 3;
    private static final int DATA_IS_CREATOR = 4;
    private static final int DATA_IS_MEMBER = 5;
    private static final int DATA_OPEN_REGISTRATION = 6;
    private static final int DATA_HORIZ_RADIUS = 7;
    private static final int DATA_VERT_RADIUS = 8;
    private static final int DATA_BASE_SCORE_PERMILLE = 9;
    private static final int DATA_COUNT = 10;
    private final BlockPos lecternPos;
    private final SimpleContainer donationContainer;
    private final SimpleContainer ingotContainer;
    private final ContainerData data;
    private TownCenterGuiPage currentPage = TownCenterGuiPage.MAIN;
    /** Client-side factory (lectern pos is server authority only). */
    public TownCenterMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, BlockPos.ZERO);
    }

    public TownCenterMenu(int containerId, Inventory playerInv, BlockPos lecternPos) {
        super(ModMenuTypes.getTownCenter(), containerId);
        this.lecternPos = lecternPos;
        this.donationContainer = new SimpleContainer(1);
        this.ingotContainer = new SimpleContainer(1);
        this.data = new SimpleContainerData(DATA_COUNT);
        addSlot(new Slot(donationContainer, 0, TownCenterMenuLayout.DONATION_SLOT_X, TownCenterMenuLayout.DONATION_SLOT_Y) {
            @Override
            public boolean isActive() {
                return optionsSlotsActive();
            }
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canPlaceUpgradeItems() && stack.is(Items.EMERALD_BLOCK);
            }
            @Override
            public boolean mayPickup(Player player) {
                return optionsSlotsActive();
            }
        });
        addSlot(new Slot(ingotContainer, 0, TownCenterMenuLayout.INGOT_SLOT_X, TownCenterMenuLayout.INGOT_SLOT_Y) {
            @Override
            public boolean isActive() {
                return optionsSlotsActive();
            }
            @Override
            public boolean mayPlace(ItemStack stack) {
                if (!canPlaceUpgradeItems()) return false;
                return stack.isEmpty()
                        || stack.is(Items.IRON_INGOT)
                        || stack.is(Items.GOLD_INGOT)
                        || stack.is(Items.NETHERITE_INGOT);
            }
            @Override
            public boolean mayPickup(Player player) {
                return optionsSlotsActive();
            }
        });
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(
                    playerInv,
                    col,
                    TownCenterMenuLayout.HOTBAR_X + col * TownCenterMenuLayout.SLOT_SIZE,
                    TownCenterMenuLayout.HOTBAR_Y));
        }
        addDataSlots(data);
        syncFromTracker(playerInv.player);
        broadcastChanges();
        if (playerInv.player instanceof ServerPlayer sp) {
            TownCenterMenuViewers.sendInitial(sp, this);
        }
    }

    private boolean optionsSlotsActive() {
        return currentPage == TownCenterGuiPage.OPTIONS;
    }

    private boolean canPlaceUpgradeItems() {
        return optionsSlotsActive()
                && hasBenefit()
                && isGameplayActive()
                && getLevel() < TownCenterLevelTable.maxLevel()
                && getUpgradeCost() > 0;
    }

    public TownCenterGuiPage getCurrentPage() {
        return currentPage;
    }

    public void setPage(TownCenterGuiPage page) {
        currentPage = page == null ? TownCenterGuiPage.MAIN : page;
        broadcastChanges();
    }

    public void syncFromTracker(Player player) {
        if (player.level().isClientSide()) return;
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null) return;
        String dim = player.level().dimension().identifier().toString();
        TownCenterEntry entry = tracker.getEntry(dim, lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        if (entry == null) return;
        long gameTime = player.level().getGameTime();
        int level = entry.level();
        data.set(DATA_LEVEL, level);
        data.set(DATA_UPGRADE_COST, TownCenterLevelTable.upgradeCost(level));
        data.set(DATA_GAMEPLAY_ACTIVE, entry.isGameplayActive(gameTime) ? 1 : 0);
        long remaining = entry.hasShutdownDeadline(gameTime)
                ? Math.max(0, (entry.deactivateDeadlineTick() - gameTime + 19) / 20) : 0;
        data.set(DATA_SHUTDOWN_SECONDS, (int) remaining);
        UUID pid = player.getUUID();
        data.set(DATA_IS_CREATOR, entry.isCreator(pid) ? 1 : 0);
        data.set(DATA_IS_MEMBER, entry.isMember(pid) ? 1 : 0);
        data.set(DATA_OPEN_REGISTRATION, entry.openRegistration() ? 1 : 0);
        data.set(DATA_HORIZ_RADIUS, TownCenterLevelTable.horizRadius(level));
        data.set(DATA_VERT_RADIUS, TownCenterLevelTable.vertRadius(level));
        data.set(DATA_BASE_SCORE_PERMILLE, (int) Math.round(TownCenterLevelTable.rawValue(level) * 1000));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            returnTransientUpgradeItems(player);
            String dim = player.level().dimension().identifier().toString();
            TownCenterMenuViewers.unregister(dim, lecternPos, player.getUUID());
        }
    }

    private void returnTransientUpgradeItems(Player player) {
        returnTransientContainerItem(player, donationContainer);
        returnTransientContainerItem(player, ingotContainer);
    }

    private static void returnTransientContainerItem(Player player, SimpleContainer container) {
        ItemStack stack = container.removeItemNoUpdate(0);
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    public BlockPos getLecternPos() {
        return lecternPos;
    }

    public int getLevel() {
        return data.get(DATA_LEVEL);
    }

    public int getUpgradeCost() {
        return data.get(DATA_UPGRADE_COST);
    }

    public boolean isGameplayActive() {
        return data.get(DATA_GAMEPLAY_ACTIVE) != 0;
    }

    public boolean isActivated() {
        return isGameplayActive();
    }

    public int getShutdownSecondsRemaining() {
        return data.get(DATA_SHUTDOWN_SECONDS);
    }

    public boolean isShutdownPending() {
        return getShutdownSecondsRemaining() > 0;
    }

    public boolean isCreator() {
        return data.get(DATA_IS_CREATOR) != 0;
    }

    public boolean isMember() {
        return data.get(DATA_IS_MEMBER) != 0;
    }

    public boolean hasBenefit() {
        return isCreator() || isMember();
    }

    public boolean isOpenRegistration() {
        return data.get(DATA_OPEN_REGISTRATION) != 0;
    }

    public int getHorizRadiusVc() {
        return data.get(DATA_HORIZ_RADIUS);
    }

    public int getVertRadiusVc() {
        return data.get(DATA_VERT_RADIUS);
    }

    public int getBaseScorePermille() {
        return data.get(DATA_BASE_SCORE_PERMILLE);
    }

    public ItemStack getDonationStack() {
        return getSlot(TownCenterMenuLayout.SLOT_DONATION_INDEX).getItem();
    }

    public ItemStack getIngotStack() {
        return getSlot(TownCenterMenuLayout.SLOT_INGOT_INDEX).getItem();
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return false;
        String dim = serverLevel.dimension().identifier().toString();
        TownCenterEntry entry = tracker.getEntry(dim, lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        if (entry == null) return false;
        if (!TownCenterStructureValidator.isEmeraldBelow(serverLevel, lecternPos)) return false;
        if (!TownCenterStructureValidator.lecternHasWrittenBook(serverLevel, lecternPos)) return false;
        return player.distanceToSqr(
                lecternPos.getX() + 0.5, lecternPos.getY() + 0.5, lecternPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide()) return true;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        if (id == BUTTON_TOGGLE_ACTIVATION) {
            boolean ok = isShutdownPending() || isGameplayActive()
                    ? TownCenterActivationHandler.tryDeactivate(serverLevel, lecternPos)
                    : TownCenterActivationHandler.tryReactivate(serverLevel, lecternPos);
            if (ok) syncFromTracker(player);
            return ok;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!optionsSlotsActive()
                && (index == TownCenterMenuLayout.SLOT_DONATION_INDEX
                || index == TownCenterMenuLayout.SLOT_INGOT_INDEX)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = slot.getItem();
        stack = taken.copy();
        if (index == TownCenterMenuLayout.SLOT_DONATION_INDEX || index == TownCenterMenuLayout.SLOT_INGOT_INDEX) {
            if (!optionsSlotsActive()) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(taken, TownCenterMenuLayout.SLOT_HOTBAR_START, TownCenterMenuLayout.SLOT_HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (canPlaceUpgradeItems() && taken.is(Items.EMERALD_BLOCK)) {
            if (!moveItemStackTo(taken, TownCenterMenuLayout.SLOT_DONATION_INDEX, TownCenterMenuLayout.SLOT_DONATION_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (canPlaceUpgradeItems()
                && (taken.is(Items.IRON_INGOT) || taken.is(Items.GOLD_INGOT) || taken.is(Items.NETHERITE_INGOT))) {
            if (!moveItemStackTo(taken, TownCenterMenuLayout.SLOT_INGOT_INDEX, TownCenterMenuLayout.SLOT_INGOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (taken.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, taken);
        return stack;
    }

    public record Provider(BlockPos lecternPos) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("container.civil.town_center");
        }
        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
            return new TownCenterMenu(containerId, inv, lecternPos);
        }
    }
}
