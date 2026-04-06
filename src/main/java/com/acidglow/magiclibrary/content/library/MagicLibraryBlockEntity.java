package com.acidglow.magiclibrary.content.library;

import com.acidglow.magiclibrary.MagicLibraryConfig;
import com.acidglow.magiclibrary.registry.MLBlockEntities;
import com.acidglow.magiclibrary.registry.MLBlocks;
import com.acidglow.magiclibrary.util.LegendaryBookMarkerUtil;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class MagicLibraryBlockEntity extends BlockEntity implements MenuProvider {
    public enum SelectionAction {
        INCREASE,
        DECREASE,
        MAX
    }

    private enum PendingOutputMode {
        NONE,
        EXTRACTION,
        ENCHANTING
    }

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_EXTRACT = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int SLOT_COUNT = 3;

    private static final long NETHER_STAR_ME = 100_000_000L;
    private static final long SUPREME_STORED_POINTS = 1_000L;

    private static final Codec<Map<Identifier, EnchantData>> STORED_ENCHANTS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, EnchantData.CODEC);
    private static final Codec<Map<Identifier, Integer>> PREPARED_LEVELS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    private static final Codec<Map<Identifier, Integer>> OUTPUT_ORIGINAL_LEVELS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    private static final Codec<Map<Identifier, Integer>> AMPLIFIED_MAX_LEVELS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    private static final String TAG_PENDING_OUTPUT_MODE = "PendingOutputMode";
    private static final String TAG_PENDING_OUTPUT_PREVIEW = "PendingOutputPreview";
    private static final String TAG_PENDING_OUTPUT_CLAIMED = "PendingOutputClaimed";
    private static final String TAG_TIER3_PREVIEW_USES_VIRTUAL_BOOK_BASE = "Tier3PreviewUsesVirtualBookBase";

    private MagicLibraryTier tier;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final Map<Identifier, EnchantData> storedEnchantData = new HashMap<>();
    private final Map<Identifier, Integer> preparedEnchantLevels = new HashMap<>();
    private final Map<Identifier, Integer> outputOriginalEnchantLevels = new HashMap<>();
    private final Map<Identifier, Integer> amplifiedMaxLevels = new HashMap<>();
    private final Container menuContainer = new Container() {
        @Override
        public int getContainerSize() {
            return SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            return MagicLibraryBlockEntity.this.isInventoryEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return MagicLibraryBlockEntity.this.items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack removed = ContainerHelper.removeItem(MagicLibraryBlockEntity.this.items, slot, amount);
            if (!removed.isEmpty()) {
                MagicLibraryBlockEntity.this.setChanged();
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(MagicLibraryBlockEntity.this.items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            MagicLibraryBlockEntity.this.items.set(slot, stack);
            if (stack.getCount() > this.getMaxStackSize()) {
                stack.setCount(this.getMaxStackSize());
            }
            MagicLibraryBlockEntity.this.onContainerSlotSet(slot, stack);
            MagicLibraryBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(MagicLibraryBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            MagicLibraryBlockEntity.this.setChanged();
        }

        @Override
        public void clearContent() {
            MagicLibraryBlockEntity.this.items.clear();
            MagicLibraryBlockEntity.this.setChanged();
        }
    };

    private long activeFuelME;
    private long activeFuelMaxME;
    private PendingOutputMode pendingOutputMode = PendingOutputMode.NONE;
    private boolean pendingOutputPreview;
    private boolean pendingOutputClaimed;
    private int upkeepRemainderTenths;
    private ItemStack tier3OutputEnchantBase = ItemStack.EMPTY;
    private boolean tier3PreviewUsesVirtualBookBase;
    private boolean dangerousExtractionBlocked;
    private boolean dangerousExtractionConfirmed;
    private long stowedGameTime = -1L;

    public MagicLibraryBlockEntity(BlockPos pos, BlockState blockState) {
        super(MLBlockEntities.MAGIC_LIBRARY.get(), pos, blockState);
        this.tier = resolveTier(blockState);
    }

    private static MagicLibraryTier resolveTier(BlockState blockState) {
        if (blockState.getBlock() instanceof MagicLibraryBlock magicLibraryBlock) {
            return magicLibraryBlock.getTier();
        }
        return MagicLibraryTier.TIER1;
    }

    public static void serverTick(Level level, BlockPos blockPos, BlockState state, MagicLibraryBlockEntity blockEntity) {
        blockEntity.serverTick();
    }

    public MagicLibraryTier getTier() {
        return this.tier;
    }

    public CompoundTag saveUpgradeData(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void loadUpgradeData(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, BlockState state) {
        this.tier = resolveTier(state);
        this.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    public long getCurrentME() {
        return MagicLibraryConfig.isUpkeepEnabled() ? this.activeFuelME : 1L;
    }

    public long getMaxME() {
        return MagicLibraryConfig.isUpkeepEnabled() ? this.activeFuelMaxME : 1L;
    }

    public void applySupremeUpgrade() {
        if (this.tier != MagicLibraryTier.TIER3 || this.level == null || this.level.isClientSide()) {
            return;
        }

        this.storedEnchantData.clear();
        this.amplifiedMaxLevels.clear();
        this.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(enchantmentHolder -> {
            if (enchantmentHolder.is(EnchantmentTags.CURSE)) {
                return;
            }

            int maxLevel = enchantmentHolder.value().getMaxLevel();
            if (maxLevel <= 0) {
                return;
            }

            this.storedEnchantData.put(
                enchantmentHolder.key().identifier(),
                new EnchantData(SUPREME_STORED_POINTS, maxLevel)
            );
        });

        if (MagicLibraryConfig.isUpkeepEnabled()) {
            this.activeFuelME = NETHER_STAR_ME;
            this.activeFuelMaxME = NETHER_STAR_ME;
        }
        this.upkeepRemainderTenths = 0;
        markChangedAndSync();
    }

    public boolean isDormant() {
        return requiresFuelForUpkeep() && this.activeFuelME <= 0L;
    }

    public int getBaseUpkeepTenthsPerTick() {
        return MagicLibraryConfig.getBaseUpkeepTenths(this.tier);
    }

    public int getCurrentUpkeepTenthsPerTick() {
        long base = getBaseUpkeepTenthsPerTick();
        long perEnchantType = MagicLibraryConfig.getPerEnchantTypeUpkeepTenths();
        long configured = base + (perEnchantType * getStoredEnchantTypeCount());
        return configured >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) configured;
    }

    public double getCurrentUpkeepPerTick() {
        return getCurrentUpkeepTenthsPerTick() / 10.0D;
    }

    public void applyStowedUpkeep(long currentGameTime) {
        if (this.stowedGameTime < 0L) {
            return;
        }

        long elapsedTicks = Math.max(0L, currentGameTime - this.stowedGameTime);
        this.stowedGameTime = -1L;
        if (!MagicLibraryConfig.isUpkeepEnabled() || elapsedTicks <= 0L || this.activeFuelME <= 0L) {
            setChanged();
            return;
        }

        int upkeepTenths = getCurrentUpkeepTenthsPerTick();
        if (upkeepTenths <= 0) {
            setChanged();
            return;
        }

        long elapsedTenths = saturatingMultiply(elapsedTicks, upkeepTenths);
        long totalTenths = saturatingAdd(elapsedTenths, this.upkeepRemainderTenths);
        long drainWholeME = totalTenths / 10L;
        long nextFuelME = Math.max(0L, this.activeFuelME - drainWholeME);
        this.activeFuelME = nextFuelME;
        this.upkeepRemainderTenths = nextFuelME > 0L ? (int) (totalTenths % 10L) : 0;
        if (this.activeFuelME <= 0L) {
            this.activeFuelMaxME = 0L;
        }
        setChanged();
    }

    public long getFuelValue(ItemStack stack) {
        if (!MagicLibraryConfig.isUpkeepEnabled() || stack.isEmpty()) {
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
        if (this.tier == MagicLibraryTier.TIER3 && stack.is(Items.NETHER_STAR)) {
            return NETHER_STAR_ME;
        }
        return 0L;
    }

    public boolean canUseExtractSlot() {
        return !isDormant();
    }

    public boolean canPlaceInExtractSlot(ItemStack stack) {
        return !isDormant() && (hasAnyEnchantments(stack) || isTomeOfAmplification(stack));
    }

    public boolean isDangerousExtractionBlocked() {
        return this.dangerousExtractionBlocked;
    }

    public void confirmDangerousExtraction() {
        if (!this.dangerousExtractionBlocked) {
            return;
        }
        this.dangerousExtractionBlocked = false;
        this.dangerousExtractionConfirmed = true;
        markChangedAndSync();
    }

    public void clearDangerousExtractionPrompt() {
        if (!this.dangerousExtractionBlocked && !this.dangerousExtractionConfirmed) {
            return;
        }
        this.dangerousExtractionBlocked = false;
        this.dangerousExtractionConfirmed = false;
        markChangedAndSync();
    }

    public boolean canUseOutputSlot() {
        return !isDormant();
    }

    public boolean canUseEnchanting() {
        return !isDormant();
    }

    public Container getMenuContainer() {
        return this.menuContainer;
    }

    public Map<Identifier, EnchantData> getStoredEnchantDataView() {
        return Map.copyOf(this.storedEnchantData);
    }

    public Map<Identifier, Integer> getPreparedEnchantLevelsView() {
        return Map.copyOf(this.preparedEnchantLevels);
    }

    public List<Identifier> getStableSortedEnchantIds() {
        List<Identifier> ids = new ArrayList<>(this.storedEnchantData.keySet());
        ids.sort(Comparator.comparing(Identifier::toString, String.CASE_INSENSITIVE_ORDER));
        return ids;
    }

    public int getPreparedLevel(Identifier enchantmentId) {
        return this.preparedEnchantLevels.getOrDefault(enchantmentId, 0);
    }

    public int getCurrentInputEnchantmentLevel(Identifier enchantmentId) {
        ItemStack input = this.items.get(SLOT_EXTRACT);
        if (input.isEmpty()) {
            return 0;
        }
        return getItemEnchantmentLevels(input).getOrDefault(enchantmentId, 0);
    }

    public ItemStack getExtractItemView() {
        return this.items.get(SLOT_EXTRACT).copy();
    }

    public ItemStack getOutputItemView() {
        return this.items.get(SLOT_OUTPUT).copy();
    }

    public ItemStack getEnchantingContextItemView() {
        return getEnchantingContextInput().copy();
    }

    public Map<Identifier, Integer> getOutputOriginalEnchantLevelsView() {
        return Map.copyOf(this.outputOriginalEnchantLevels);
    }

    public Map<Identifier, Integer> getAmplifiedMaxLevelsView() {
        return Map.copyOf(this.amplifiedMaxLevels);
    }

    public boolean isAmplificationMode() {
        return this.tier == MagicLibraryTier.TIER3 && isTomeOfAmplification(this.items.get(SLOT_EXTRACT));
    }

    public boolean isPendingOutputPreview() {
        return this.pendingOutputPreview
            && this.pendingOutputMode == PendingOutputMode.ENCHANTING
            && !this.pendingOutputClaimed
            && !this.preparedEnchantLevels.isEmpty()
            && !this.items.get(SLOT_OUTPUT).isEmpty();
    }

    public boolean claimPendingOutputAtomically(Player player) {
        if (player.level().isClientSide()) {
            return true;
        }

        if (!validatePendingOutputState(true) || isDormant()) {
            return false;
        }

        if (this.pendingOutputMode == PendingOutputMode.EXTRACTION) {
            clearPendingOutputStateAfterClaim();
            markChangedAndSync();
            return true;
        }

        if (this.pendingOutputMode != PendingOutputMode.ENCHANTING) {
            return true;
        }

        return canClaimPendingEnchantingOutput(player);
    }

    public boolean canTakeOutput(Player player) {
        if (!validatePendingOutputState(false)) {
            return false;
        }

        if (isDormant()) {
            return false;
        }

        ItemStack output = this.items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return false;
        }

        if (this.pendingOutputMode == PendingOutputMode.EXTRACTION) {
            return true;
        }

        if (this.pendingOutputMode == PendingOutputMode.NONE) {
            return this.tier == MagicLibraryTier.TIER3 && isValidTier3EnchantingTarget(output);
        }

        if (this.pendingOutputMode != PendingOutputMode.ENCHANTING) {
            return false;
        }

        if (this.pendingOutputClaimed) {
            return true;
        }

        return canClaimPendingEnchantingOutput(player);
    }

    public void onOutputTaken(Player player, ItemStack takenStack) {
        if (player.level().isClientSide()) {
            return;
        }

        if (this.pendingOutputMode == PendingOutputMode.ENCHANTING) {
            finalizePendingEnchantingOutput(player);
            markChangedAndSync();
            return;
        }

        if (!validatePendingOutputState(true)) {
            return;
        }

        if (this.pendingOutputMode == PendingOutputMode.ENCHANTING && this.pendingOutputClaimed) {
            clearPendingOutputStateAfterClaim();
            markChangedAndSync();
            return;
        }

        if (this.pendingOutputMode == PendingOutputMode.NONE) {
            this.tier3OutputEnchantBase = ItemStack.EMPTY;
            this.outputOriginalEnchantLevels.clear();
            this.tier3PreviewUsesVirtualBookBase = false;
            return;
        }
    }

    public boolean handleSelectionAction(Player player, int stableEnchantIndex, SelectionAction action) {
        if (player.level().isClientSide() || isDormant()) {
            return false;
        }
        if (isAmplificationMode()) {
            return false;
        }

        if (this.pendingOutputMode == PendingOutputMode.EXTRACTION && !this.items.get(SLOT_OUTPUT).isEmpty()) {
            return false;
        }

        ItemStack input = getEnchantingContextInput();

        List<Identifier> stableIds = getStableSortedEnchantIds();
        if (stableEnchantIndex < 0 || stableEnchantIndex >= stableIds.size()) {
            return false;
        }

        Identifier enchantmentId = stableIds.get(stableEnchantIndex);
        if (!this.storedEnchantData.containsKey(enchantmentId)) {
            return false;
        }

        int baseLevel = getItemEnchantmentLevels(input).getOrDefault(enchantmentId, 0);
        int currentPrepared = Math.max(baseLevel, this.preparedEnchantLevels.getOrDefault(enchantmentId, baseLevel));
        int maxCraftable = getMaxCraftableLevelFor(player, input, enchantmentId);
        int nextLevel = currentPrepared;

        if (action == SelectionAction.INCREASE) {
            nextLevel = Math.min(currentPrepared + 1, maxCraftable);
        } else if (action == SelectionAction.DECREASE) {
            nextLevel = Math.max(baseLevel, currentPrepared - 1);
        } else if (action == SelectionAction.MAX) {
            nextLevel = maxCraftable;
        }

        if (nextLevel <= baseLevel) {
            this.preparedEnchantLevels.remove(enchantmentId);
        } else if (nextLevel != currentPrepared) {
            this.preparedEnchantLevels.put(enchantmentId, nextLevel);
        } else {
            return false;
        }

        refreshEnchantingOutput();
        markChangedAndSync();
        return true;
    }

    public boolean handleAmplificationAction(Player player, int stableEnchantIndex) {
        if (player.level().isClientSide() || isDormant()) {
            return false;
        }
        if (!isAmplificationMode()) {
            return false;
        }
        if (this.pendingOutputMode == PendingOutputMode.EXTRACTION && !this.items.get(SLOT_OUTPUT).isEmpty()) {
            return false;
        }

        List<Identifier> stableIds = getStableSortedEnchantIds();
        if (stableEnchantIndex < 0 || stableEnchantIndex >= stableIds.size()) {
            return false;
        }

        ItemStack tomeStack = this.items.get(SLOT_EXTRACT);
        if (!isTomeOfAmplification(tomeStack)) {
            return false;
        }

        Identifier enchantmentId = stableIds.get(stableEnchantIndex);
        EnchantData data = this.storedEnchantData.get(enchantmentId);
        if (data == null) {
            return false;
        }

        Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
        if (holder == null) {
            return false;
        }

        int currentMaxLevel = getEffectiveLibraryMaxLevel(enchantmentId, holder);
        int vanillaMaxLevel = Math.max(1, holder.value().getMaxLevel());
        if (!MagicLibraryConfig.isValidTomeUpgradeTarget(enchantmentId, vanillaMaxLevel, currentMaxLevel)) {
            return false;
        }

        int upgradeNumber = MagicLibraryConfig.getAmplificationUpgradeNumber(vanillaMaxLevel, currentMaxLevel);
        int xpCost = MagicLibraryConfig.getAmplificationXpCost(upgradeNumber);

        if (player.experienceLevel < xpCost) {
            return false;
        }

        int effectiveCap = MagicLibraryConfig.getEffectiveAmplifiedMaxLevel(enchantmentId, vanillaMaxLevel);
        int targetLevel = MagicLibraryConfig.getSelectableTargetLevel(currentMaxLevel, currentMaxLevel + 1, effectiveCap);
        if (targetLevel <= currentMaxLevel) {
            return false;
        }
        if (xpCost > 0) {
            player.giveExperienceLevels(-xpCost);
        }
        tomeStack.shrink(1);
        if (tomeStack.isEmpty()) {
            this.items.set(SLOT_EXTRACT, ItemStack.EMPTY);
        }

        int overrideLevel = Math.max(targetLevel, data.maxDiscoveredLevel());
        this.amplifiedMaxLevels.put(enchantmentId, overrideLevel);

        refreshEnchantingOutput();
        markChangedAndSync();
        return true;
    }

    private void serverTick() {
        validatePendingOutputState(false);

        boolean changed = false;

        int upkeepTenths = getCurrentUpkeepTenthsPerTick();
        boolean activeThisTick = !requiresFuelForUpkeep();
        if (MagicLibraryConfig.isUpkeepEnabled() && upkeepTenths > 0) {
            if (this.activeFuelME <= 0L && tryActivateNextFuel()) {
                changed = true;
            }
            activeThisTick = this.activeFuelME > 0L;
            if (activeThisTick) {
                changed = drainActiveFuel(upkeepTenths) || changed;
                if (this.activeFuelME <= 0L && tryActivateNextFuel()) {
                    changed = true;
                }
            }
        } else if (clearActiveFuelState()) {
            changed = true;
        }

        if (activeThisTick) {
            if (tryProcessExtraction()) {
                changed = true;
            }
            if (refreshEnchantingOutput()) {
                changed = true;
            }
        } else if (clearEnchantingPreviewIfPresent()) {
            changed = true;
        }

        if (changed) {
            setChanged();
        }
    }

    private boolean tryProcessExtraction() {
        ItemStack input = this.items.get(SLOT_EXTRACT);
        if (input.isEmpty()) {
            return false;
        }
        if (this.dangerousExtractionBlocked && !this.dangerousExtractionConfirmed) {
            return false;
        }

        Map<Identifier, Integer> levels = getItemEnchantmentLevels(input);
        if (levels.isEmpty()) {
            return false;
        }

        boolean bookLike = isBookLike(input);
        int extractLevelCap = getTierDiscoveredLevelCap();
        Map<Identifier, Integer> toExtract = new HashMap<>();
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            Holder.Reference<Enchantment> holder = resolveEnchantment(entry.getKey());
            if (holder == null) {
                continue;
            }
            if (!holder.is(EnchantmentTags.CURSE) && entry.getValue() <= extractLevelCap) {
                toExtract.put(entry.getKey(), entry.getValue());
            }
        }

        if (toExtract.isEmpty()) {
            return false;
        }

        for (Map.Entry<Identifier, Integer> entry : toExtract.entrySet()) {
            addExtractedEnchantment(entry.getKey(), entry.getValue());
        }

        removeExtractedEnchantments(input, toExtract);

        if (bookLike) {
            if (!hasAnyEnchantments(input)) {
                consumeOneExtractInput();
            }
            this.dangerousExtractionBlocked = shouldBlockDangerousExtraction(this.items.get(SLOT_EXTRACT));
            this.dangerousExtractionConfirmed = false;
            if (this.items.get(SLOT_OUTPUT).isEmpty()) {
                clearPendingOutputStateAfterClaim();
            }
            markChangedAndSync();
            return true;
        }

        boolean broken = applyExtractionDurabilityDamage(input);
        if (broken || input.isEmpty()) {
            this.items.set(SLOT_EXTRACT, ItemStack.EMPTY);
        }
        this.dangerousExtractionBlocked = shouldBlockDangerousExtraction(this.items.get(SLOT_EXTRACT));
        this.dangerousExtractionConfirmed = false;
        if (this.items.get(SLOT_OUTPUT).isEmpty()) {
            clearPendingOutputStateAfterClaim();
        }
        markChangedAndSync();
        return true;
    }

    private boolean refreshEnchantingOutput() {
        if (this.preparedEnchantLevels.isEmpty()) {
            return clearEnchantingPreviewIfPresent();
        }

        if (isDormant()) {
            clearPreparedSelectionsAndPreview();
            return true;
        }

        ItemStack input = getEnchantingContextInput();
        if (input.isEmpty()) {
            clearPreparedSelectionsAndPreview();
            return true;
        }
        if (this.pendingOutputMode != PendingOutputMode.ENCHANTING) {
            capturePendingEnchantingBase(input);
            input = this.tier3OutputEnchantBase.copyWithCount(1);
        }
        PreparedCosts costs = calculatePreparedCosts(input, this.preparedEnchantLevels);
        if (!costs.valid()) {
            clearPreparedSelectionsAndPreview();
            return true;
        }

        ItemStack preview = createEnchantingPreviewBaseStack(input);
        applyPreparedEnchantments(preview, this.preparedEnchantLevels);
        updateLegendaryBookMarker(preview);

        ItemStack output = this.items.get(SLOT_OUTPUT);
        if (this.pendingOutputMode != PendingOutputMode.ENCHANTING || !ItemStack.matches(output, preview)) {
            this.items.set(SLOT_OUTPUT, preview);
            this.pendingOutputMode = PendingOutputMode.ENCHANTING;
            this.pendingOutputPreview = true;
            this.pendingOutputClaimed = false;
            markChangedAndSync();
            return true;
        }

        if (!this.pendingOutputPreview || this.pendingOutputClaimed) {
            this.pendingOutputPreview = true;
            this.pendingOutputClaimed = false;
            markChangedAndSync();
            return true;
        }

        return false;
    }

    private ItemStack createEnchantingPreviewBaseStack(ItemStack input) {
        if (input.isEmpty()) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
        if (input.is(Items.ENCHANTED_BOOK)) {
            return input.copyWithCount(1);
        }
        if (input.is(Items.BOOK)) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
        return input.copyWithCount(1);
    }

    private boolean clearEnchantingPreviewIfPresent() {
        if (this.pendingOutputMode != PendingOutputMode.ENCHANTING) {
            if (this.pendingOutputMode == PendingOutputMode.NONE && this.items.get(SLOT_OUTPUT).isEmpty() && !this.tier3OutputEnchantBase.isEmpty()) {
                this.tier3OutputEnchantBase = ItemStack.EMPTY;
                this.outputOriginalEnchantLevels.clear();
                this.tier3PreviewUsesVirtualBookBase = false;
                markChangedAndSync();
                return true;
            }
            return false;
        }

        boolean changed = false;
        if (!this.items.get(SLOT_OUTPUT).isEmpty() || !this.tier3OutputEnchantBase.isEmpty()) {
            if (!this.tier3OutputEnchantBase.isEmpty() && !this.tier3PreviewUsesVirtualBookBase) {
                this.items.set(SLOT_OUTPUT, this.tier3OutputEnchantBase.copy());
            } else {
                this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
            }
            changed = true;
        }
        if (this.pendingOutputMode != PendingOutputMode.NONE) {
            this.pendingOutputMode = PendingOutputMode.NONE;
            changed = true;
        }
        if (this.pendingOutputPreview) {
            this.pendingOutputPreview = false;
            changed = true;
        }
        if (this.pendingOutputClaimed) {
            this.pendingOutputClaimed = false;
            changed = true;
        }
        if (!this.tier3OutputEnchantBase.isEmpty()) {
            this.tier3OutputEnchantBase = ItemStack.EMPTY;
            this.outputOriginalEnchantLevels.clear();
            changed = true;
        }
        if (this.tier3PreviewUsesVirtualBookBase) {
            this.tier3PreviewUsesVirtualBookBase = false;
            changed = true;
        }

        if (changed) {
            markChangedAndSync();
        }
        return changed;
    }

    private void clearPreparedSelectionsAndPreview() {
        this.preparedEnchantLevels.clear();
        if (this.pendingOutputMode == PendingOutputMode.ENCHANTING && !this.tier3OutputEnchantBase.isEmpty() && !this.tier3PreviewUsesVirtualBookBase) {
            this.items.set(SLOT_OUTPUT, this.tier3OutputEnchantBase.copy());
        } else if (this.pendingOutputMode == PendingOutputMode.ENCHANTING) {
            this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
        }
        this.pendingOutputMode = PendingOutputMode.NONE;
        this.pendingOutputPreview = false;
        this.pendingOutputClaimed = false;
        this.tier3OutputEnchantBase = ItemStack.EMPTY;
        this.outputOriginalEnchantLevels.clear();
        this.tier3PreviewUsesVirtualBookBase = false;
        markChangedAndSync();
    }

    private boolean isUsingVirtualTier3BookPreviewBase() {
        if (this.tier != MagicLibraryTier.TIER3) {
            return false;
        }
        if (!this.tier3OutputEnchantBase.isEmpty()) {
            return false;
        }
        if (isValidTier3EnchantingTarget(this.items.get(SLOT_OUTPUT))) {
            return false;
        }
        ItemStack extract = this.items.get(SLOT_EXTRACT);
        return extract.isEmpty() || isBookLike(extract);
    }

    private void capturePendingEnchantingBase(ItemStack input) {
        capturePendingEnchantingBase(input, this.tier != MagicLibraryTier.TIER3 || isUsingVirtualTier3BookPreviewBase());
    }

    private void capturePendingEnchantingBase(ItemStack input, boolean usesVirtualBookBase) {
        this.tier3PreviewUsesVirtualBookBase = usesVirtualBookBase;
        this.tier3OutputEnchantBase = createEnchantingPreviewBaseStack(input);
        this.outputOriginalEnchantLevels.clear();
        this.outputOriginalEnchantLevels.putAll(getItemEnchantmentLevels(this.tier3OutputEnchantBase));
    }

    private boolean validatePendingOutputState(boolean sync) {
        ItemStack output = this.items.get(SLOT_OUTPUT);
        boolean hasPreparedOutput = !this.preparedEnchantLevels.isEmpty() && !output.isEmpty();
        boolean hasSerializedPendingPreview = this.pendingOutputPreview || this.pendingOutputMode == PendingOutputMode.ENCHANTING;
        boolean expectsPendingEnchanting =
            hasSerializedPendingPreview || (this.pendingOutputMode == PendingOutputMode.NONE && hasPreparedOutput);

        if (expectsPendingEnchanting) {
            if (restorePendingEnchantingState()) {
                return true;
            }
            invalidatePendingEnchantingState(sync);
            return false;
        }

        if (this.pendingOutputMode != PendingOutputMode.EXTRACTION && this.pendingOutputMode != PendingOutputMode.NONE) {
            this.pendingOutputMode = PendingOutputMode.NONE;
        }

        if (output.isEmpty()) {
            this.tier3OutputEnchantBase = ItemStack.EMPTY;
            if (this.pendingOutputMode != PendingOutputMode.EXTRACTION) {
                this.pendingOutputPreview = false;
                this.pendingOutputClaimed = false;
                this.outputOriginalEnchantLevels.clear();
                this.tier3PreviewUsesVirtualBookBase = false;
            }
        } else if (this.pendingOutputMode != PendingOutputMode.ENCHANTING) {
            this.tier3OutputEnchantBase = ItemStack.EMPTY;
            if (this.tier == MagicLibraryTier.TIER3 && isValidTier3EnchantingTarget(output) && this.outputOriginalEnchantLevels.isEmpty()) {
                this.outputOriginalEnchantLevels.putAll(getItemEnchantmentLevels(output));
            }
            if (this.pendingOutputMode != PendingOutputMode.EXTRACTION) {
                this.pendingOutputPreview = false;
                this.pendingOutputClaimed = false;
                this.tier3PreviewUsesVirtualBookBase = false;
            }
        }

        return true;
    }

    private boolean restorePendingEnchantingState() {
        ItemStack output = this.items.get(SLOT_OUTPUT);
        if (output.isEmpty() || this.preparedEnchantLevels.isEmpty()) {
            return false;
        }

        ItemStack base = reconstructPendingEnchantingBase(output);
        if (base.isEmpty() || !canPrepareEnchantingOnInput(base)) {
            return false;
        }

        PreparedCosts costs = calculatePreparedCosts(base, this.preparedEnchantLevels);
        if (!costs.valid()) {
            return false;
        }

        ItemStack preview = createEnchantingPreviewBaseStack(base);
        applyPreparedEnchantments(preview, this.preparedEnchantLevels);
        updateLegendaryBookMarker(preview);

        if (!ItemStack.matches(output, preview)) {
            this.items.set(SLOT_OUTPUT, preview);
        }

        this.pendingOutputMode = PendingOutputMode.ENCHANTING;
        this.pendingOutputPreview = !this.pendingOutputClaimed;
        capturePendingEnchantingBase(base, this.tier != MagicLibraryTier.TIER3 || this.tier3PreviewUsesVirtualBookBase);
        return true;
    }

    private ItemStack reconstructPendingEnchantingBase(ItemStack previewOutput) {
        ItemStack base;
        if (this.tier == MagicLibraryTier.TIER3 && !this.tier3PreviewUsesVirtualBookBase) {
            if (!isValidTier3EnchantingTarget(previewOutput)) {
                return ItemStack.EMPTY;
            }
            base = previewOutput.copyWithCount(1);
        } else {
            base = new ItemStack(Items.ENCHANTED_BOOK);
        }

        restorePreparedEnchantmentsToOriginalLevels(base);
        updateLegendaryBookMarker(base);
        return base;
    }

    private void restorePreparedEnchantmentsToOriginalLevels(ItemStack stack) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        for (Identifier enchantmentId : this.preparedEnchantLevels.keySet()) {
            Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
            if (holder == null) {
                continue;
            }

            int originalLevel = this.outputOriginalEnchantLevels.getOrDefault(enchantmentId, 0);
            mutable.removeIf(existing ->
                existing.unwrapKey().map(key -> enchantmentId.equals(key.identifier())).orElse(false)
            );
            if (originalLevel > 0) {
                mutable.set(holder, originalLevel);
            }
        }
        EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
    }

    private void invalidatePendingEnchantingState(boolean sync) {
        ItemStack base = ItemStack.EMPTY;
        ItemStack output = this.items.get(SLOT_OUTPUT);
        if (!output.isEmpty() && this.tier == MagicLibraryTier.TIER3 && !this.tier3PreviewUsesVirtualBookBase) {
            base = reconstructPendingEnchantingBase(output);
        }

        this.preparedEnchantLevels.clear();
        this.pendingOutputMode = PendingOutputMode.NONE;
        this.pendingOutputPreview = false;
        this.pendingOutputClaimed = false;
        this.tier3OutputEnchantBase = ItemStack.EMPTY;
        this.outputOriginalEnchantLevels.clear();
        this.tier3PreviewUsesVirtualBookBase = false;
        this.items.set(SLOT_OUTPUT, base);

        if (sync) {
            markChangedAndSync();
        } else {
            setChanged();
        }
    }

    private int getMaxCraftableLevelFor(Player player, ItemStack input, Identifier enchantmentId) {
        Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
        if (holder == null) {
            return 0;
        }

        int baseLevel = getItemEnchantmentLevels(input).getOrDefault(enchantmentId, 0);
        int maxLevel = getEffectiveLibraryMaxLevel(enchantmentId, holder);
        if (maxLevel <= baseLevel) {
            return baseLevel;
        }

        int best = baseLevel;
        for (int level = baseLevel + 1; level <= maxLevel; level++) {
            Map<Identifier, Integer> test = new HashMap<>(this.preparedEnchantLevels);
            test.put(enchantmentId, level);
            PreparedCosts costs = calculatePreparedCosts(input, test);
            if (!costs.valid()) {
                break;
            }
            if (requiresTier3XPCost(input) && costs.totalXPCost() > player.experienceLevel) {
                break;
            }
            best = level;
        }
        return best;
    }

    private PreparedCosts calculatePreparedCosts(ItemStack input, Map<Identifier, Integer> preparedLevels) {
        Map<Identifier, Integer> baseLevels = getItemEnchantmentLevels(input);
        Map<Identifier, Integer> previewLevels = new HashMap<>(baseLevels);
        for (Map.Entry<Identifier, Integer> entry : preparedLevels.entrySet()) {
            int targetLevel = entry.getValue();
            if (targetLevel > 0) {
                previewLevels.put(entry.getKey(), Math.max(targetLevel, previewLevels.getOrDefault(entry.getKey(), 0)));
            }
        }
        Map<Identifier, Long> pointCosts = new HashMap<>();
        long totalRawXP = 0L;
        boolean needsXP = requiresTier3XPCost(input);

        for (Map.Entry<Identifier, Integer> entry : preparedLevels.entrySet()) {
            Identifier enchantmentId = entry.getKey();
            int targetLevel = entry.getValue();
            int baseLevel = baseLevels.getOrDefault(enchantmentId, 0);
            if (targetLevel <= baseLevel) {
                continue;
            }

            Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
            if (holder == null) {
                return PreparedCosts.invalid();
            }

            if (!canApplyEnchantmentToInput(input, holder, baseLevel)) {
                return PreparedCosts.invalid();
            }

            if (!isCompatibleWithPreview(enchantmentId, holder, previewLevels)) {
                return PreparedCosts.invalid();
            }

            int effectiveLibraryMax = getEffectiveLibraryMaxLevel(enchantmentId, holder);
            if (targetLevel > effectiveLibraryMax) {
                return PreparedCosts.invalid();
            }

            EnchantData data = this.storedEnchantData.get(enchantmentId);
            if (data == null) {
                return PreparedCosts.invalid();
            }

            long pointCost = getPointCostForUpgrade(baseLevel, targetLevel);
            if (pointCost > data.storedPoints()) {
                return PreparedCosts.invalid();
            }
            pointCosts.put(enchantmentId, pointCost);

            if (needsXP) {
                totalRawXP = saturatingAdd(totalRawXP, getRawXPCostForUpgrade(baseLevel, targetLevel));
            }
        }

        long totalXP = needsXP ? applyTier3Discount(totalRawXP) : 0L;
        return new PreparedCosts(pointCosts, totalXP, true);
    }

    private boolean canClaimPendingEnchantingOutput(Player player) {
        ItemStack input = getEnchantingContextInput();
        PreparedCosts costs = calculatePreparedCosts(input, this.preparedEnchantLevels);
        if (!costs.valid()) {
            return false;
        }

        if (requiresTier3XPCost(input) && costs.totalXPCost() > player.experienceLevel) {
            return false;
        }

        for (Map.Entry<Identifier, Long> entry : costs.pointCosts().entrySet()) {
            EnchantData data = this.storedEnchantData.get(entry.getKey());
            if (data == null || entry.getValue() > data.storedPoints()) {
                return false;
            }
        }

        return true;
    }

    private boolean claimPendingEnchantingOutput(Player player) {
        return canClaimPendingEnchantingOutput(player);
    }

    private void finalizePendingEnchantingOutput(Player player) {
        if (!canClaimPendingEnchantingOutput(player)) {
            clearPendingOutputStateAfterClaim();
            return;
        }

        ItemStack input = getEnchantingContextInput();
        PreparedCosts costs = calculatePreparedCosts(input, this.preparedEnchantLevels);
        if (!costs.valid()) {
            clearPendingOutputStateAfterClaim();
            return;
        }

        for (Map.Entry<Identifier, Long> entry : costs.pointCosts().entrySet()) {
            Identifier enchantmentId = entry.getKey();
            long pointCost = entry.getValue();
            EnchantData data = this.storedEnchantData.get(enchantmentId);
            if (data == null) {
                clearPendingOutputStateAfterClaim();
                return;
            }
            this.storedEnchantData.put(enchantmentId, new EnchantData(data.storedPoints() - pointCost, data.maxDiscoveredLevel()));
        }

        if (requiresTier3XPCost(input) && costs.totalXPCost() > 0L) {
            int xpToPay = costs.totalXPCost() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) costs.totalXPCost();
            player.giveExperienceLevels(-xpToPay);
        }

        if (isUsingExtractGearAsEnchantingSource()) {
            consumeOneExtractInput();
        }

        clearPendingOutputStateAfterClaim();
    }

    private void clearPendingOutputStateAfterClaim() {
        this.preparedEnchantLevels.clear();
        this.pendingOutputMode = PendingOutputMode.NONE;
        this.pendingOutputPreview = false;
        this.pendingOutputClaimed = false;
        this.tier3OutputEnchantBase = ItemStack.EMPTY;
        this.outputOriginalEnchantLevels.clear();
        this.tier3PreviewUsesVirtualBookBase = false;
    }

    private void addExtractedEnchantment(Identifier enchantmentId, int extractedLevel) {
        EnchantData current = this.storedEnchantData.get(enchantmentId);
        long currentPoints = current == null ? 0L : current.storedPoints();
        int currentDiscovered = current == null ? 0 : current.maxDiscoveredLevel();

        int discoveredCap = getTierDiscoveredLevelCap();
        int cappedDiscoveredLevel = Math.min(extractedLevel, discoveredCap);
        long addedPoints = getPointsForLevel(cappedDiscoveredLevel);
        int newDiscovered = Math.max(currentDiscovered, cappedDiscoveredLevel);
        long newPoints = saturatingAdd(currentPoints, addedPoints);

        this.storedEnchantData.put(enchantmentId, new EnchantData(newPoints, newDiscovered));
    }

    private int getEffectiveLibraryMaxLevel(Identifier enchantmentId, Holder.Reference<Enchantment> holder) {
        EnchantData data = this.storedEnchantData.get(enchantmentId);
        if (data == null) {
            return 0;
        }
        int vanillaMaxLevel = Math.max(1, holder.value().getMaxLevel());
        int discoveredMax = data.maxDiscoveredLevel();
        int amplifiedMax = this.amplifiedMaxLevels.getOrDefault(enchantmentId, 0);
        return MagicLibraryConfig.getEffectiveLibraryMaxLevel(enchantmentId, vanillaMaxLevel, discoveredMax, amplifiedMax);
    }

    private int getTierDiscoveredLevelCap() {
        return switch (this.tier) {
            case TIER1 -> 2;
            case TIER2 -> 4;
            case TIER3 -> Integer.MAX_VALUE;
        };
    }

    private boolean canPrepareEnchantingOnInput(ItemStack input) {
        if (isBookLike(input)) {
            return true;
        }
        return this.tier == MagicLibraryTier.TIER3;
    }

    private boolean canApplyEnchantmentToInput(ItemStack input, Holder<Enchantment> enchantment, int currentLevel) {
        if (isBookLike(input)) {
            return true;
        }
        if (this.tier != MagicLibraryTier.TIER3) {
            return false;
        }
        if (currentLevel > 0) {
            return true;
        }
        return enchantment.value().canEnchant(input);
    }

    private boolean isCompatibleWithPreview(
        Identifier enchantmentId,
        Holder<Enchantment> enchantment,
        Map<Identifier, Integer> previewLevels
    ) {
        if (MagicLibraryConfig.allowIncompatibleEnchants()) {
            return true;
        }

        for (Map.Entry<Identifier, Integer> entry : previewLevels.entrySet()) {
            if (entry.getValue() <= 0 || enchantmentId.equals(entry.getKey())) {
                continue;
            }

            Holder.Reference<Enchantment> existing = resolveEnchantment(entry.getKey());
            if (existing != null && !Enchantment.areCompatible(enchantment, existing)) {
                return false;
            }
        }
        return true;
    }

    private boolean requiresTier3XPCost(ItemStack input) {
        return this.tier == MagicLibraryTier.TIER3 && !isBookLike(input);
    }

    private ItemStack getEnchantingContextInput() {
        if (!this.tier3OutputEnchantBase.isEmpty()) {
            return this.tier3OutputEnchantBase.copyWithCount(1);
        }
        if (this.tier == MagicLibraryTier.TIER3) {
            ItemStack output = this.items.get(SLOT_OUTPUT);
            if (isValidTier3EnchantingTarget(output)) {
                return output.copyWithCount(1);
            }
        }
        ItemStack extract = this.items.get(SLOT_EXTRACT);
        if (this.tier == MagicLibraryTier.TIER3 && !extract.isEmpty() && !isBookLike(extract)) {
            return extract;
        }
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    private void onContainerSlotSet(int slot, ItemStack stack) {
        if (slot == SLOT_EXTRACT) {
            this.dangerousExtractionConfirmed = false;
            this.dangerousExtractionBlocked = shouldBlockDangerousExtraction(stack);
            return;
        }

        if (slot != SLOT_OUTPUT) {
            return;
        }

        // When the output slot is emptied as part of a pending preview claim,
        // keep preview state alive until the slot removal path finalizes it.
        if (stack.isEmpty() && this.pendingOutputMode == PendingOutputMode.ENCHANTING && !this.preparedEnchantLevels.isEmpty()) {
            return;
        }
        if (preservePendingPreviewStateForSyncedOutput(stack)) {
            return;
        }

        if (this.pendingOutputMode == PendingOutputMode.ENCHANTING || !this.preparedEnchantLevels.isEmpty()) {
            this.preparedEnchantLevels.clear();
            this.pendingOutputMode = PendingOutputMode.NONE;
            this.pendingOutputPreview = false;
            this.pendingOutputClaimed = false;
        }

        this.tier3OutputEnchantBase = ItemStack.EMPTY;
        this.outputOriginalEnchantLevels.clear();
        this.tier3PreviewUsesVirtualBookBase = false;
        if (this.tier == MagicLibraryTier.TIER3 && isValidTier3EnchantingTarget(stack)) {
            this.tier3OutputEnchantBase = stack.copyWithCount(1);
            this.outputOriginalEnchantLevels.putAll(getItemEnchantmentLevels(this.tier3OutputEnchantBase));
        }
    }

    private boolean preservePendingPreviewStateForSyncedOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(this.pendingOutputPreview || this.pendingOutputMode == PendingOutputMode.ENCHANTING || !this.preparedEnchantLevels.isEmpty())) {
            return false;
        }
        if (this.preparedEnchantLevels.isEmpty()) {
            return false;
        }

        ItemStack base = reconstructPendingEnchantingBase(stack);
        if (base.isEmpty() || !canPrepareEnchantingOnInput(base)) {
            return false;
        }

        PreparedCosts costs = calculatePreparedCosts(base, this.preparedEnchantLevels);
        if (!costs.valid()) {
            return false;
        }

        ItemStack preview = createEnchantingPreviewBaseStack(base);
        applyPreparedEnchantments(preview, this.preparedEnchantLevels);
        updateLegendaryBookMarker(preview);
        if (!ItemStack.matches(stack, preview)) {
            return false;
        }

        this.pendingOutputMode = PendingOutputMode.ENCHANTING;
        this.pendingOutputPreview = !this.pendingOutputClaimed;
        capturePendingEnchantingBase(base, this.tier != MagicLibraryTier.TIER3 || this.tier3PreviewUsesVirtualBookBase);
        return true;
    }

    private boolean isUsingExtractGearAsEnchantingSource() {
        ItemStack extract = this.items.get(SLOT_EXTRACT);
        return this.tier == MagicLibraryTier.TIER3 && !extract.isEmpty() && !isBookLike(extract);
    }

    public boolean canPlaceInOutputSlot(ItemStack stack) {
        return !isDormant() && this.tier == MagicLibraryTier.TIER3 && !stack.is(Items.BOOK) && isValidTier3EnchantingTarget(stack);
    }

    private boolean isTomeOfAmplification(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MLBlocks.TOME_OF_AMPLIFICATION.get());
    }

    private boolean isBookLike(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    private void consumeOneExtractInput() {
        ItemStack input = this.items.get(SLOT_EXTRACT);
        if (input.isEmpty()) {
            return;
        }
        input.shrink(1);
        if (input.isEmpty()) {
            this.items.set(SLOT_EXTRACT, ItemStack.EMPTY);
        }
    }

    private void removeExtractedEnchantments(ItemStack stack, Map<Identifier, Integer> extractedEnchantments) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        mutable.removeIf(
            holder -> holder.unwrapKey().map(key -> extractedEnchantments.containsKey(key.identifier())).orElse(false)
        );
        EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
    }

    private void applyPreparedEnchantments(ItemStack stack, Map<Identifier, Integer> preparedLevels) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        for (Map.Entry<Identifier, Integer> entry : preparedLevels.entrySet()) {
            Holder.Reference<Enchantment> holder = resolveEnchantment(entry.getKey());
            if (holder != null) {
                mutable.set(holder, entry.getValue());
            }
        }
        EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
    }

    private void updateLegendaryBookMarker(ItemStack stack) {
        boolean markLegendary = stack.is(Items.ENCHANTED_BOOK) && hasAboveVanillaMaxEnchantLevel(stack);
        LegendaryBookMarkerUtil.setLegendaryGeneratedBookMarker(stack, markLegendary);
    }

    private boolean hasAboveVanillaMaxEnchantLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getIntValue() > entry.getKey().value().getMaxLevel()) {
                return true;
            }
        }
        return false;
    }

    private boolean applyExtractionDurabilityDamage(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        int maxDurability = stack.getMaxDamage();
        if (maxDurability <= 0) {
            return false;
        }
        int damagePercent = MagicLibraryConfig.getExtractionDamagePercent();
        if (damagePercent <= 0) {
            return false;
        }
        int damage = (int) Math.ceil(maxDurability * (damagePercent / 100.0D));
        int newDamageValue = Math.max(0, stack.getDamageValue() + damage);
        if (newDamageValue >= maxDurability) {
            stack.shrink(1);
            return true;
        }
        stack.setDamageValue(newDamageValue);
        return false;
    }

    private boolean shouldBlockDangerousExtraction(ItemStack stack) {
        if (stack.isEmpty() || isBookLike(stack) || !stack.isDamageableItem()) {
            return false;
        }

        int maxDurability = stack.getMaxDamage();
        if (maxDurability <= 0) {
            return false;
        }

        int damagePercent = MagicLibraryConfig.getExtractionDamagePercent();
        if (damagePercent <= 0) {
            return false;
        }
        int damage = (int) Math.ceil(maxDurability * (damagePercent / 100.0D));
        int newDamageValue = Math.max(0, stack.getDamageValue() + damage);
        if (newDamageValue < maxDurability) {
            return false;
        }

        Map<Identifier, Integer> levels = getItemEnchantmentLevels(stack);
        if (levels.isEmpty()) {
            return false;
        }

        int extractLevelCap = getTierDiscoveredLevelCap();
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            Holder.Reference<Enchantment> holder = resolveEnchantment(entry.getKey());
            if (holder == null) {
                continue;
            }
            if (!holder.is(EnchantmentTags.CURSE) && entry.getValue() <= extractLevelCap) {
                return true;
            }
        }

        return false;
    }

    private Map<Identifier, Integer> getItemEnchantmentLevels(ItemStack stack) {
        Map<Identifier, Integer> levels = new HashMap<>();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(key -> levels.put(key.identifier(), entry.getIntValue()));
        }
        return levels;
    }

    private boolean hasAnyEnchantments(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty();
    }

    private boolean isValidTier3EnchantingTarget(ItemStack stack) {
        if (stack.isEmpty() || this.tier != MagicLibraryTier.TIER3) {
            return false;
        }
        return hasAnyEnchantments(stack) || stack.isEnchantable() || isSupportedGearItem(stack);
    }

    private boolean isSupportedGearItem(ItemStack stack) {
        return stack.is(Items.ELYTRA)
            || stack.is(Items.SHIELD)
            || stack.is(Items.SHEARS)
            || stack.getItem().getDescriptionId().contains("horse_armor");
    }

    private Holder.Reference<Enchantment> resolveEnchantment(Identifier enchantmentId) {
        if (this.level == null) {
            return null;
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
        return this.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
    }

    private long getPointCostForUpgrade(int currentLevel, int targetLevel) {
        if (targetLevel <= currentLevel) {
            return 0L;
        }
        long targetCost = getPointsForLevel(targetLevel);
        long currentCost = getPointsForLevel(currentLevel);
        return Math.max(0L, targetCost - currentCost);
    }

    private long getRawXPCostForUpgrade(int currentLevel, int targetLevel) {
        if (targetLevel <= currentLevel) {
            return 0L;
        }
        long targetCost = getXPForLevel(targetLevel);
        long currentCost = getXPForLevel(currentLevel);
        return Math.max(0L, targetCost - currentCost);
    }

    private long applyTier3Discount(long rawXP) {
        return ceilScaled(rawXP, 0.9D);
    }

    private int getStoredEnchantTypeCount() {
        return this.storedEnchantData.size();
    }

    private boolean requiresFuelForUpkeep() {
        return MagicLibraryConfig.isUpkeepEnabled() && getCurrentUpkeepTenthsPerTick() > 0;
    }

    private boolean tryActivateNextFuel() {
        ItemStack fuelStack = this.items.get(SLOT_FUEL);
        long fuelME = getFuelValue(fuelStack);
        if (fuelME <= 0L) {
            return false;
        }

        fuelStack.shrink(1);
        if (fuelStack.isEmpty()) {
            this.items.set(SLOT_FUEL, ItemStack.EMPTY);
        }
        this.activeFuelME = fuelME;
        this.activeFuelMaxME = fuelME;
        this.upkeepRemainderTenths = 0;
        return true;
    }

    private boolean drainActiveFuel(int upkeepTenths) {
        int totalTenths = upkeepTenths + this.upkeepRemainderTenths;
        long drainWholeME = totalTenths / 10L;
        int nextRemainderTenths = totalTenths % 10;
        long nextFuelME = Math.max(0L, this.activeFuelME - drainWholeME);
        boolean changed = drainWholeME > 0L || nextRemainderTenths != this.upkeepRemainderTenths;
        this.upkeepRemainderTenths = nextFuelME > 0L ? nextRemainderTenths : 0;
        this.activeFuelME = nextFuelME;
        if (this.activeFuelME <= 0L) {
            this.activeFuelMaxME = 0L;
        }
        return changed;
    }

    private boolean clearActiveFuelState() {
        boolean changed = this.activeFuelME != 0L || this.activeFuelMaxME != 0L || this.upkeepRemainderTenths != 0;
        this.activeFuelME = 0L;
        this.activeFuelMaxME = 0L;
        this.upkeepRemainderTenths = 0;
        return changed;
    }

    private void markChangedAndSync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    private static long ceilScaled(long value, double scale) {
        if (value <= 0L) {
            return 0L;
        }
        double scaled = Math.ceil(value * scale);
        return scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) scaled;
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private static long saturatingMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    public static long getPointsForLevel(int level) {
        if (level <= 0) {
            return 0L;
        }
        if (level >= 63) {
            return Long.MAX_VALUE;
        }
        return 1L << level;
    }

    public static long getXPForLevel(int level) {
        if (level <= 0) {
            return 0L;
        }
        if (level >= 62) {
            return Long.MAX_VALUE;
        }
        return 1L << level;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(this.tier.getDisplayName() + " Library");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MagicLibraryMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("ActiveFuelME", this.activeFuelME);
        output.putLong("ActiveFuelMaxME", this.activeFuelMaxME);
        output.putInt("UpkeepRemainderTenths", this.upkeepRemainderTenths);
        output.putBoolean("DangerousExtractionBlocked", this.dangerousExtractionBlocked);
        output.putBoolean("DangerousExtractionConfirmed", this.dangerousExtractionConfirmed);
        output.putInt(TAG_PENDING_OUTPUT_MODE, this.pendingOutputMode.ordinal());
        output.putBoolean(TAG_PENDING_OUTPUT_PREVIEW, this.pendingOutputPreview);
        output.putBoolean(TAG_PENDING_OUTPUT_CLAIMED, this.pendingOutputClaimed);
        output.putBoolean(TAG_TIER3_PREVIEW_USES_VIRTUAL_BOOK_BASE, this.tier3PreviewUsesVirtualBookBase);
        if (this.stowedGameTime >= 0L) {
            output.putLong("StowedGameTime", this.stowedGameTime);
        }
        ContainerHelper.saveAllItems(output, this.items);
        output.store("StoredEnchantData", STORED_ENCHANTS_CODEC, this.storedEnchantData);
        output.store("PreparedEnchantLevels", PREPARED_LEVELS_CODEC, this.preparedEnchantLevels);
        output.store("OutputOriginalEnchantLevels", OUTPUT_ORIGINAL_LEVELS_CODEC, this.outputOriginalEnchantLevels);
        output.store("AmplifiedMaxLevels", AMPLIFIED_MAX_LEVELS_CODEC, this.amplifiedMaxLevels);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.activeFuelME = Math.max(0L, input.getLongOr("ActiveFuelME", 0L));
        this.activeFuelMaxME = Math.max(this.activeFuelME, input.getLongOr("ActiveFuelMaxME", 0L));
        this.upkeepRemainderTenths = Math.clamp(input.getIntOr("UpkeepRemainderTenths", 0), 0, 9);
        this.dangerousExtractionBlocked = input.getBooleanOr("DangerousExtractionBlocked", false);
        this.dangerousExtractionConfirmed = input.getBooleanOr("DangerousExtractionConfirmed", false);
        this.pendingOutputMode = readPendingOutputMode(input.getIntOr(TAG_PENDING_OUTPUT_MODE, PendingOutputMode.NONE.ordinal()));
        this.pendingOutputPreview = input.getBooleanOr(
            TAG_PENDING_OUTPUT_PREVIEW,
            this.pendingOutputMode == PendingOutputMode.ENCHANTING
        );
        this.pendingOutputClaimed = input.getBooleanOr(TAG_PENDING_OUTPUT_CLAIMED, false);
        this.tier3PreviewUsesVirtualBookBase = input.getBooleanOr(TAG_TIER3_PREVIEW_USES_VIRTUAL_BOOK_BASE, false);
        this.stowedGameTime = input.getLongOr("StowedGameTime", -1L);
        ContainerHelper.loadAllItems(input, this.items);

        this.storedEnchantData.clear();
        this.storedEnchantData.putAll(input.read("StoredEnchantData", STORED_ENCHANTS_CODEC).orElse(Map.of()));

        this.preparedEnchantLevels.clear();
        this.preparedEnchantLevels.putAll(input.read("PreparedEnchantLevels", PREPARED_LEVELS_CODEC).orElse(Map.of()));
        this.outputOriginalEnchantLevels.clear();
        this.outputOriginalEnchantLevels.putAll(input.read("OutputOriginalEnchantLevels", OUTPUT_ORIGINAL_LEVELS_CODEC).orElse(Map.of()));
        this.amplifiedMaxLevels.clear();
        this.amplifiedMaxLevels.putAll(input.read("AmplifiedMaxLevels", AMPLIFIED_MAX_LEVELS_CODEC).orElse(Map.of()));
        this.tier3OutputEnchantBase = ItemStack.EMPTY;
        validatePendingOutputState(false);
    }

    private static PendingOutputMode readPendingOutputMode(int ordinal) {
        PendingOutputMode[] values = PendingOutputMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return PendingOutputMode.NONE;
        }
        return values[ordinal];
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private boolean isInventoryEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record PreparedCosts(Map<Identifier, Long> pointCosts, long totalXPCost, boolean valid) {
        private static PreparedCosts invalid() {
            return new PreparedCosts(Map.of(), 0L, false);
        }
    }
}
