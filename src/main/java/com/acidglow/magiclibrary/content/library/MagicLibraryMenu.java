package com.acidglow.magiclibrary.content.library;

import com.acidglow.magiclibrary.MagicLibraryConfig;
import com.acidglow.magiclibrary.registry.MLBlocks;
import com.acidglow.magiclibrary.registry.MLMenus;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class MagicLibraryMenu extends AbstractContainerMenu {
    public enum SelectionAction {
        INCREASE(1),
        DECREASE(2),
        MAX(3);

        private final int encodedType;

        SelectionAction(int encodedType) {
            this.encodedType = encodedType;
        }

        public int encodedType() {
            return this.encodedType;
        }

        @Nullable
        public static SelectionAction fromEncodedType(int encodedType) {
            for (SelectionAction action : values()) {
                if (action.encodedType == encodedType) {
                    return action;
                }
            }
            return null;
        }
    }

    private static final int INTERNAL_SLOT_COUNT = 3;
    private static final int SLOT_INDEX_FUEL = 0;
    private static final int SLOT_INDEX_EXTRACT = 1;
    private static final int PLAYER_SLOT_START = INTERNAL_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 9 * 3;
    private static final int PLAYER_INVENTORY_START = PLAYER_SLOT_START;
    private static final int PLAYER_INVENTORY_END_EXCLUSIVE = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END_EXCLUSIVE;
    private static final int HOTBAR_END_EXCLUSIVE = HOTBAR_START + Inventory.getSelectionSize();
    private static final int PLAYER_SLOT_END_EXCLUSIVE = HOTBAR_END_EXCLUSIVE;
    private static final int BUTTON_ACTION_SHIFT = 20;
    private static final int BUTTON_INDEX_MASK = (1 << BUTTON_ACTION_SHIFT) - 1;
    private static final int BUTTON_ACTION_CONFIRM_DANGEROUS_EXTRACTION = 4;
    private static final int BUTTON_ACTION_CANCEL_DANGEROUS_EXTRACTION = 5;
    private static final int BUTTON_ACTION_CONFIRM_AMPLIFICATION = 6;

    private final Level level;
    private final BlockPos blockPos;
    private final MagicLibraryTier tier;
    private final SimpleContainer fallbackSlots;
    private final Container slotContainer;
    @Nullable
    private final MagicLibraryBlockEntity blockEntity;

    private int currentMELowClient;
    private int currentMEHighClient;
    private int maxMELowClient;
    private int maxMEHighClient;
    private int upkeepTenthsClient;
    private int dangerousExtractionBlockedClient;

    public MagicLibraryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    public MagicLibraryMenu(int containerId, Inventory playerInventory, MagicLibraryBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.getBlockPos());
    }

    public MagicLibraryMenu(int containerId, Inventory playerInventory, BlockPos blockPos) {
        super(MLMenus.MAGIC_LIBRARY_MENU.get(), containerId);
        this.level = playerInventory.player.level();
        this.blockPos = blockPos.immutable();
        this.fallbackSlots = new SimpleContainer(INTERNAL_SLOT_COUNT);

        BlockEntity blockEntity = this.level.getBlockEntity(this.blockPos);
        this.blockEntity = blockEntity instanceof MagicLibraryBlockEntity magicLibraryBlockEntity ? magicLibraryBlockEntity : null;
        this.tier = resolveTier();
        this.slotContainer = this.blockEntity != null ? this.blockEntity.getMenuContainer() : this.fallbackSlots;

        this.addSlot(new Slot(this.slotContainer, MagicLibraryBlockEntity.SLOT_FUEL, 21, 26) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isValidFuel(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return MagicLibraryConfig.isUpkeepEnabled() || this.hasItem();
            }

            @Override
            public boolean isActive() {
                return MagicLibraryConfig.isUpkeepEnabled() || this.hasItem();
            }
        });
        this.addSlot(new Slot(this.slotContainer, MagicLibraryBlockEntity.SLOT_EXTRACT, 173, 71) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canPlaceInExtractSlot(stack);
            }
        });
        this.addSlot(new Slot(this.slotContainer, MagicLibraryBlockEntity.SLOT_OUTPUT, 173, 113) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canPlaceInOutputSlot(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return canUseOutputSlot() && (MagicLibraryMenu.this.blockEntity == null || MagicLibraryMenu.this.blockEntity.canTakeOutput(player));
            }

            @Override
            public Optional<ItemStack> tryRemove(int amount, int limit, Player player) {
                if (MagicLibraryMenu.this.blockEntity != null && !MagicLibraryMenu.this.blockEntity.claimPendingOutputAtomically(player)) {
                    return Optional.empty();
                }
                return super.tryRemove(amount, limit, player);
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                if (MagicLibraryMenu.this.blockEntity != null) {
                    MagicLibraryMenu.this.blockEntity.onOutputTaken(player, stack);
                }
            }
        });

        addPlayerInventory(playerInventory, 26, 153);
        addPlayerHotbar(playerInventory, 26, 211);
        addMagicEnergyDataSlots();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        int actionType = buttonId >>> BUTTON_ACTION_SHIFT;
        if (actionType == BUTTON_ACTION_CONFIRM_DANGEROUS_EXTRACTION) {
            if (player.level().isClientSide()) {
                return true;
            }
            if (this.blockEntity == null) {
                return false;
            }
            this.blockEntity.confirmDangerousExtraction();
            return true;
        }
        if (actionType == BUTTON_ACTION_CANCEL_DANGEROUS_EXTRACTION) {
            if (player.level().isClientSide()) {
                return true;
            }
            return cancelDangerousExtraction(player);
        }
        if (actionType == BUTTON_ACTION_CONFIRM_AMPLIFICATION) {
            if (player.level().isClientSide()) {
                return true;
            }
            if (this.blockEntity == null) {
                return false;
            }
            int enchantIndex = buttonId & BUTTON_INDEX_MASK;
            return this.blockEntity.handleAmplificationAction(player, enchantIndex);
        }

        int enchantIndex = buttonId & BUTTON_INDEX_MASK;
        SelectionAction action = SelectionAction.fromEncodedType(actionType);
        if (action == null) {
            return false;
        }

        if (player.level().isClientSide()) {
            return true;
        }

        if (this.blockEntity == null) {
            return false;
        }

        MagicLibraryBlockEntity.SelectionAction blockEntityAction = switch (action) {
            case INCREASE -> MagicLibraryBlockEntity.SelectionAction.INCREASE;
            case DECREASE -> MagicLibraryBlockEntity.SelectionAction.DECREASE;
            case MAX -> MagicLibraryBlockEntity.SelectionAction.MAX;
        };
        return this.blockEntity.handleSelectionAction(player, enchantIndex, blockEntityAction);
    }

    private void addMagicEnergyDataSlots() {
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return toLow(getCurrentMEServer());
            }

            @Override
            public void set(int value) {
                currentMELowClient = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return toHigh(getCurrentMEServer());
            }

            @Override
            public void set(int value) {
                currentMEHighClient = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return toLow(getMaxMEServer());
            }

            @Override
            public void set(int value) {
                maxMELowClient = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return toHigh(getMaxMEServer());
            }

            @Override
            public void set(int value) {
                maxMEHighClient = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return getUpkeepTenthsPerTickServer();
            }

            @Override
            public void set(int value) {
                upkeepTenthsClient = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return isDangerousExtractionBlockedServer() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                dangerousExtractionBlockedClient = value;
            }
        });
    }

    public static int encodeSelectionButtonId(SelectionAction action, int enchantIndex) {
        return (action.encodedType() << BUTTON_ACTION_SHIFT) | (enchantIndex & BUTTON_INDEX_MASK);
    }

    public static int encodeDangerousExtractionPopupButtonId(boolean confirm) {
        int action = confirm ? BUTTON_ACTION_CONFIRM_DANGEROUS_EXTRACTION : BUTTON_ACTION_CANCEL_DANGEROUS_EXTRACTION;
        return action << BUTTON_ACTION_SHIFT;
    }

    public static int encodeAmplificationButtonId(int enchantIndex) {
        return (BUTTON_ACTION_CONFIRM_AMPLIFICATION << BUTTON_ACTION_SHIFT) | (enchantIndex & BUTTON_INDEX_MASK);
    }

    private MagicLibraryTier resolveTier() {
        if (this.blockEntity != null) {
            return this.blockEntity.getTier();
        }

        if (this.level.getBlockState(this.blockPos).getBlock() instanceof MagicLibraryBlock magicLibraryBlock) {
            return magicLibraryBlock.getTier();
        }

        return MagicLibraryTier.TIER1;
    }

    public MagicLibraryTier getTier() {
        return this.tier;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    @Nullable
    public MagicLibraryBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public long getCurrentMEClient() {
        return fromHighLow(this.currentMEHighClient, this.currentMELowClient);
    }

    public long getMaxMEClient() {
        return fromHighLow(this.maxMEHighClient, this.maxMELowClient);
    }

    public int getUpkeepTenthsPerTickClient() {
        return Math.max(0, this.upkeepTenthsClient);
    }

    public boolean isDangerousExtractionBlockedClient() {
        return this.dangerousExtractionBlockedClient != 0;
    }

    public boolean isDormantClient() {
        return getUpkeepTenthsPerTickClient() > 0 && getCurrentMEClient() <= 0L;
    }

    public Map<Identifier, EnchantData> getStoredEnchantDataClient() {
        return this.blockEntity != null ? this.blockEntity.getStoredEnchantDataView() : Map.of();
    }

    public Map<Identifier, Integer> getPreparedEnchantLevelsClient() {
        return this.blockEntity != null ? this.blockEntity.getPreparedEnchantLevelsView() : Map.of();
    }

    public List<Identifier> getStableSortedEnchantIdsClient() {
        return this.blockEntity != null ? this.blockEntity.getStableSortedEnchantIds() : List.of();
    }

    public int getCurrentInputEnchantmentLevelClient(Identifier enchantmentId) {
        return this.blockEntity != null ? this.blockEntity.getCurrentInputEnchantmentLevel(enchantmentId) : 0;
    }

    public ItemStack getExtractItemClient() {
        return this.blockEntity != null ? this.blockEntity.getExtractItemView() : ItemStack.EMPTY;
    }

    public ItemStack getOutputItemClient() {
        return this.blockEntity != null ? this.blockEntity.getOutputItemView() : ItemStack.EMPTY;
    }

    public ItemStack getEnchantingContextItemClient() {
        return this.blockEntity != null ? this.blockEntity.getEnchantingContextItemView() : ItemStack.EMPTY;
    }

    public Map<Identifier, Integer> getOutputOriginalEnchantLevelsClient() {
        return this.blockEntity != null ? this.blockEntity.getOutputOriginalEnchantLevelsView() : Map.of();
    }

    public Map<Identifier, Integer> getAmplifiedMaxLevelsClient() {
        return this.blockEntity != null ? this.blockEntity.getAmplifiedMaxLevelsView() : Map.of();
    }

    public boolean isAmplificationModeClient() {
        return this.blockEntity != null && this.blockEntity.isAmplificationMode();
    }

    public boolean isPendingOutputPreviewClient() {
        return this.blockEntity != null && this.blockEntity.isPendingOutputPreview();
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(this.blockPos) instanceof MagicLibraryBlockEntity)) {
            return false;
        }

        return player.distanceToSqr(
            this.blockPos.getX() + 0.5D,
            this.blockPos.getY() + 0.5D,
            this.blockPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        if (index == MagicLibraryBlockEntity.SLOT_OUTPUT && this.blockEntity != null && this.blockEntity.isPendingOutputPreview()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (!slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        if (index < INTERNAL_SLOT_COUNT) {
            if (!this.moveItemStackTo(stackInSlot, PLAYER_SLOT_START, Math.min(PLAYER_SLOT_END_EXCLUSIVE, this.slots.size()), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_SLOT_END_EXCLUSIVE) {
            boolean moved = false;

            Slot fuelSlot = this.slots.get(SLOT_INDEX_FUEL);
            if (fuelSlot.mayPlace(stackInSlot)) {
                moved = this.moveItemStackTo(stackInSlot, SLOT_INDEX_FUEL, SLOT_INDEX_FUEL + 1, false);
            }

            if (!moved) {
                Slot extractSlot = this.slots.get(SLOT_INDEX_EXTRACT);
                if (extractSlot.mayPlace(stackInSlot)) {
                    moved = this.moveItemStackTo(stackInSlot, SLOT_INDEX_EXTRACT, SLOT_INDEX_EXTRACT + 1, false);
                }
            }

            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stackInSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stackInSlot);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide() && this.blockEntity == null) {
            this.clearContainer(player, this.fallbackSlots);
        }
    }

    private boolean isValidFuel(ItemStack stack) {
        if (this.blockEntity != null) {
            return this.blockEntity.getFuelValue(stack) > 0L;
        }
        return getFuelMEForTier(stack, this.tier) > 0L;
    }

    private boolean canUseExtractSlot() {
        return this.blockEntity == null || this.blockEntity.canUseExtractSlot();
    }

    private boolean canPlaceInExtractSlot(ItemStack stack) {
        if (this.blockEntity != null) {
            return this.blockEntity.canPlaceInExtractSlot(stack);
        }
        return canUseExtractSlot() && !stack.isEmpty() && (
            !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty() || isTomeOfAmplification(stack)
        );
    }

    private boolean canUseOutputSlot() {
        return this.blockEntity == null || this.blockEntity.canUseOutputSlot();
    }

    private boolean canPlaceInOutputSlot(ItemStack stack) {
        if (this.blockEntity != null) {
            return this.blockEntity.canPlaceInOutputSlot(stack);
        }
        if (this.tier != MagicLibraryTier.TIER3 || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BOOK)) {
            return false;
        }
        return stack.isEnchantable() || isSupportedGearItem(stack) || !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty();
    }

    private long getCurrentMEServer() {
        return this.blockEntity != null ? this.blockEntity.getCurrentME() : 0L;
    }

    private long getMaxMEServer() {
        return this.blockEntity != null ? this.blockEntity.getMaxME() : 0L;
    }

    private int getUpkeepTenthsPerTickServer() {
        if (this.blockEntity != null) {
            return this.blockEntity.getCurrentUpkeepTenthsPerTick();
        }
        return MagicLibraryConfig.getBaseUpkeepTenths(this.tier);
    }

    private boolean isDangerousExtractionBlockedServer() {
        return this.blockEntity != null && this.blockEntity.isDangerousExtractionBlocked();
    }

    private boolean cancelDangerousExtraction(Player player) {
        if (this.blockEntity == null) {
            return false;
        }

        ItemStack extractStack = this.slotContainer.getItem(SLOT_INDEX_EXTRACT);
        if (extractStack.isEmpty()) {
            this.blockEntity.clearDangerousExtractionPrompt();
            return true;
        }

        ItemStack stackToReturn = extractStack.copy();
        this.slotContainer.setItem(SLOT_INDEX_EXTRACT, ItemStack.EMPTY);
        this.blockEntity.clearDangerousExtractionPrompt();
        returnToPlayerStorageOrDrop(player, stackToReturn);
        this.broadcastChanges();
        return true;
    }

    private void returnToPlayerStorageOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END_EXCLUSIVE, false)) {
            // Continue to hotbar fallback with the remaining stack.
        }

        if (!stack.isEmpty()) {
            this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END_EXCLUSIVE, false);
        }

        if (!stack.isEmpty()) {
            player.drop(stack.copy(), false);
            stack.setCount(0);
        }
    }

    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory, int x, int y) {
        for (int slot = 0; slot < 9; slot++) {
            this.addSlot(new Slot(playerInventory, slot, x + slot * 18, y));
        }
    }

    private static long getFuelMEForTier(ItemStack stack, MagicLibraryTier tier) {
        if (!MagicLibraryConfig.isUpkeepEnabled()) {
            return 0L;
        }
        if (stack.is(Items.REDSTONE)) {
            return MagicLibraryConfig.getRedstoneFuelME();
        }
        if (stack.is(Items.GLOWSTONE_DUST)) {
            return MagicLibraryConfig.getGlowstoneDustFuelME();
        }
        if (stack.is(Items.AMETHYST_SHARD)) {
            return MagicLibraryConfig.getAmethystShardFuelME();
        }
        if (tier == MagicLibraryTier.TIER3 && stack.is(Items.NETHER_STAR)) {
            return 100_000_000L;
        }
        return 0L;
    }

    private static boolean isTomeOfAmplification(ItemStack stack) {
        return stack.is(MLBlocks.TOME_OF_AMPLIFICATION.get());
    }

    private static boolean isSupportedGearItem(ItemStack stack) {
        return stack.is(Items.ELYTRA)
            || stack.is(Items.SHIELD)
            || stack.is(Items.SHEARS)
            || stack.getItem().getDescriptionId().contains("horse_armor");
    }

    private static int toLow(long value) {
        return (int) (value & 0xFFFFFFFFL);
    }

    private static int toHigh(long value) {
        return (int) ((value >>> 32) & 0xFFFFFFFFL);
    }

    private static long fromHighLow(int high, int low) {
        return (Integer.toUnsignedLong(high) << 32) | Integer.toUnsignedLong(low);
    }
}
