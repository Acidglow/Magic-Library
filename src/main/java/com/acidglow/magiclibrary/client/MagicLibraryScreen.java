package com.acidglow.magiclibrary.client;

import com.acidglow.magiclibrary.MagicLibraryConfig;
import com.acidglow.magiclibrary.MagicLibrary;
import com.acidglow.magiclibrary.content.library.EnchantData;
import com.acidglow.magiclibrary.content.library.MagicLibraryBlockEntity;
import com.acidglow.magiclibrary.content.library.MagicLibraryMenu;
import com.acidglow.magiclibrary.content.library.MagicLibraryTier;
import com.acidglow.magiclibrary.util.RomanNumeralUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class MagicLibraryScreen extends AbstractContainerScreen<MagicLibraryMenu> {
    private record DiscoveryPopup(List<String> lines, int startTick) {}
    private record TooltipCostPreview(
        int targetLevel,
        boolean maxLevelReached,
        long pointCost,
        long xpCost,
        boolean pointsAffordable,
        boolean xpAffordable
    ) {}

    private record AmplificationPopup(
        Identifier enchantmentId,
        String enchantName,
        int currentLevel,
        int nextLevel,
        int serverIndex
    ) {}

    private record EnchantRow(
        Identifier enchantmentId,
        String name,
        long storedPoints,
        int maxDiscoveredLevel,
        int baseLevel,
        int currentLevel,
        int preparedLevel,
        int serverIndex,
        EnchantRowGroup group
    ) {}

    private enum EnchantRowGroup {
        MELEE_WEAPONS("textures/gui/enchant_groups/enchant_group_melee_weapons.png"),
        RANGED_WEAPONS("textures/gui/enchant_groups/enchant_group_ranged_weapons.png"),
        SPECIAL_WEAPONS("textures/gui/enchant_groups/enchant_group_special_weapons.png"),
        TOOLS("textures/gui/enchant_groups/enchant_group_tools.png"),
        ARMOR("textures/gui/enchant_groups/enchant_group_armor.png"),
        MISC("textures/gui/enchant_groups/enchant_group_misc.png");

        private final Identifier texture;

        EnchantRowGroup(String path) {
            this.texture = Identifier.fromNamespaceAndPath(MagicLibrary.MODID, path);
        }
    }

    private enum SearchAliasTarget {
        HEAD(Items.DIAMOND_HELMET),
        CHEST(Items.DIAMOND_CHESTPLATE),
        LEGS(Items.DIAMOND_LEGGINGS),
        FEET(Items.DIAMOND_BOOTS),
        ARMOR(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
        SWORD(Items.DIAMOND_SWORD),
        AXE(Items.DIAMOND_AXE),
        PICKAXE(Items.DIAMOND_PICKAXE),
        SHOVEL(Items.DIAMOND_SHOVEL),
        HOE(Items.DIAMOND_HOE),
        BOW(Items.BOW),
        CROSSBOW(Items.CROSSBOW),
        TRIDENT(Items.TRIDENT),
        FISHING_ROD(Items.FISHING_ROD),
        TOOL(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE),
        WEAPON(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.BOW, Items.CROSSBOW, Items.TRIDENT),
        BOOK();

        private final net.minecraft.world.item.Item[] sampleItems;

        SearchAliasTarget(net.minecraft.world.item.Item... sampleItems) {
            this.sampleItems = sampleItems;
        }

        private boolean matches(Holder<Enchantment> enchantment) {
            if (this == BOOK) {
                return true;
            }

            for (net.minecraft.world.item.Item item : this.sampleItems) {
                if (enchantment.value().canEnchant(new ItemStack(item))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final int TEXT_COLOR_WHITE = 0xFFFFFFFF;
    private static final int TEXT_COLOR_MUTED = 0xFF6B6B6B;
    private static final int TEXT_COLOR_HOVER = 0xFFFFFFAA;
    private static final int TEXT_COLOR_DORMANT = 0xFFA0A0A0;
    private static final int TEXT_COLOR_AMPLIFICATION_READY = 0xFFFFC86A;
    private static final int TEXT_COLOR_AMPLIFICATION_BLOCKED = 0xFF8D7A54;
    private static final int TOOLTIP_UNAFFORDABLE_COLOR = 0xFFFF5555;
    private static final int TOOLTIP_MAX_LEVEL_COLOR = 0xFF63F4FF;
    private static final int FRAME_COLOR = 0xFFFFFFFF;
    private static final int ME_BAR_BACKGROUND = 0xFF000000;
    private static final int ME_BAR_FILL_START = 0xFF00E0FF;
    private static final int ME_BAR_FILL_END = 0xFF0077FF;
    private static final int ME_BAR_DORMANT_FILL = 0xFF000000;
    private static final int ME_BAR_BORDER = 0xFF000000;
    private static final int ME_BAR_TRACK_HIGHLIGHT = 0x332F2F2F;
    private static final int ME_BAR_FILL_HIGHLIGHT = 0x66FFFFFF;
    private static final int ME_BAR_FILL_SHEEN_START = 0x55B8FFFF;
    private static final int ME_BAR_FILL_SHEEN_END = 0x0000FFFF;
    private static final int ME_BAR_FILL_BOTTOM_SHADE = 0x33002244;
    private static final int ME_BAR_GLOW_TIER1_LAYER1 = 0x228A5CFF;
    private static final int ME_BAR_GLOW_TIER1_LAYER2 = 0x336A3DCC;
    private static final int ME_BAR_GLOW_TIER2_LAYER1 = 0x2200E0FF;
    private static final int ME_BAR_GLOW_TIER2_LAYER2 = 0x3300BFFF;
    private static final int ME_BAR_GLOW_TIER3_LAYER1 = 0x22FFD966;
    private static final int ME_BAR_GLOW_TIER3_LAYER2 = 0x33FFB800;
    private static final int ME_BAR_GLOW_DORMANT_LAYER1 = 0x11000000;
    private static final int ME_BAR_GLOW_DORMANT_LAYER2 = 0x08000000;
    private static final int ROW_HIGHLIGHT = 0x553A3A3A;
    private static final int SCROLLBAR_THUMB = 0xFFBEBEBE;

    private static final int GUI_WIDTH = 212;
    private static final int GUI_HEIGHT = 242;
    private static final int ME_BAR_X = 58;
    private static final int ME_BAR_Y = 30;
    private static final int ME_BAR_WIDTH = 124;
    private static final int ME_BAR_HEIGHT = 10;
    private static final int TIER_TEXT_X = 31;
    private static final int TIER_TEXT_Y = 4;
    private static final int TIER_TEXT_WIDTH = 150;
    private static final int TIER_TEXT_HEIGHT = 18;
    private static final int SEARCH_X = 14;
    private static final int SEARCH_Y = 59;
    private static final int SEARCH_WIDTH = 110;
    private static final int SEARCH_HEIGHT = 10;
    private static final int LIST_X = 14;
    private static final int LIST_Y = 69;
    private static final int LIST_WIDTH = 106;
    private static final int LIST_HEIGHT = 80;
    private static final int SCROLLBAR_X = 128;
    private static final int SCROLLBAR_Y = 60;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_HEIGHT = 88;
    private static final int ROW_HEIGHT = 12;
    private static final int ROW_BACKGROUND_HEIGHT = ROW_HEIGHT - 1;
    private static final int ROW_TEXT_OFFSET_X = 6;
    private static final int ROW_TEXT_RIGHT_PADDING = -20;
    private static final int ROW_START_OFFSET_Y = 3;
    private static final int SLOT_FRAME_OUTSET = 1;
    private static final int SLOT_FRAME_SIZE = 18;
    private static final float ENCHANT_TEXT_SCALE = 0.70f;
    private static final int MODAL_DIM_COLOR = 0x88000000;
    private static final int EXTRACTION_WARNING_WIDTH = 176;
    private static final int EXTRACTION_WARNING_HEIGHT = 72;
    private static final int EXTRACTION_WARNING_BUTTON_WIDTH = 56;
    private static final int EXTRACTION_WARNING_BUTTON_HEIGHT = 16;
    private static final int EXTRACTION_WARNING_BUTTON_GAP = 12;
    private static final int EXTRACTION_WARNING_BUTTON_Y_OFFSET = 48;
    private static final int FADE_IN_TICKS = 12;
    private static final int DISPLAY_TICKS = 72;
    private static final int FADE_OUT_TICKS = 16;
    private static final int TOTAL_TICKS = FADE_IN_TICKS + DISPLAY_TICKS + FADE_OUT_TICKS;
    private static final int DISCOVERY_BG_BASE_ALPHA = 170;
    private static final int DISCOVERY_Y_OFFSET = 40;

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        MagicLibrary.MODID,
        "textures/gui/magic_library.png"
    );
    private static final Identifier APPRENTICE_SCROLLBAR_THUMB_TEXTURE = Identifier.fromNamespaceAndPath(
        MagicLibrary.MODID,
        "textures/gui/apprentice_library_scrollbar_tump.png"
    );
    private static final Identifier ADEPT_SCROLLBAR_THUMB_TEXTURE = Identifier.fromNamespaceAndPath(
        MagicLibrary.MODID,
        "textures/gui/adept_library_scrollbar_tump.png"
    );
    private static final Identifier ARCHMAGE_SCROLLBAR_THUMB_TEXTURE = Identifier.fromNamespaceAndPath(
        MagicLibrary.MODID,
        "textures/gui/archmage_library_scrollbar_tump.png"
    );
    private static final Identifier LIBRARY_FONT_ID = Identifier.fromNamespaceAndPath(MagicLibrary.MODID, "library_font");
    private static final Identifier LIBRARY_FONT_JSON = Identifier.fromNamespaceAndPath(MagicLibrary.MODID, "font/library_font.json");
    private static final Identifier LIBRARY_FONT_TTF = Identifier.fromNamespaceAndPath(MagicLibrary.MODID, "font/library_font.ttf");
    private static final FontDescription LIBRARY_FONT = new FontDescription.Resource(LIBRARY_FONT_ID);
    private static final Map<String, SearchAliasTarget> SEARCH_ALIAS_TARGETS = createSearchAliasTargets();
    private static final int ENCHANT_GROUP_TEXTURE_WIDTH = 542;
    private static final int ENCHANT_GROUP_TEXTURE_HEIGHT = 105;
    @Nullable
    private static Field SEARCH_BOX_TEXT_Y_FIELD;
    private static boolean SEARCH_BOX_TEXT_Y_FIELD_LOOKED_UP;

    private EditBox searchBox;
    private Font searchBoxFont;
    private boolean useLibraryFont;
    private List<EnchantRow> filteredRows = List.of();
    private float scrollOffs;
    private int startRow;
    private boolean scrolling;
    private int popupTickCounter;
    private final Set<Identifier> knownDiscoveredEnchantIds = new HashSet<>();
    private final Deque<DiscoveryPopup> discoveryPopups = new ArrayDeque<>();
    private boolean extractionWarningPopupOpen;
    @Nullable
    private AmplificationPopup amplificationPopup;

    public MagicLibraryScreen(MagicLibraryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void init() {
        String currentSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        super.init();
        this.useLibraryFont = isLibraryFontAvailable();
        this.searchBoxFont = this.useLibraryFont ? createLibrarySearchFont() : this.font;
        this.searchBox = new EditBox(
            this.searchBoxFont,
            this.leftPos + SEARCH_X,
            this.topPos + SEARCH_Y,
            SEARCH_WIDTH,
            SEARCH_HEIGHT,
            Component.literal("Search")
        ) {
            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(this.getX(), this.getY());
                guiGraphics.pose().scale(ENCHANT_TEXT_SCALE, ENCHANT_TEXT_SCALE);
                guiGraphics.pose().translate(-this.getX(), -this.getY());
                super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
                guiGraphics.pose().popMatrix();
            }
        };
        if (this.useLibraryFont) {
            this.searchBox.addFormatter((text, displayPos) -> FormattedCharSequence.forward(text, Style.EMPTY.withFont(LIBRARY_FONT)));
        }
        this.searchBox.setBordered(false);
        this.searchBox.setResponder(value -> {
            this.scrollOffs = 0.0F;
            this.startRow = 0;
            this.rebuildFilteredRows();
        });
        this.searchBox.setValue(currentSearch);
        alignSearchBoxTextVertically();
        this.addRenderableWidget(this.searchBox);
        this.rebuildFilteredRows();
        this.knownDiscoveredEnchantIds.clear();
        this.knownDiscoveredEnchantIds.addAll(this.menu.getStoredEnchantDataClient().keySet());
        this.popupTickCounter = 0;
        this.discoveryPopups.clear();
        this.amplificationPopup = null;
        syncExtractionWarningPopupState();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.popupTickCounter++;
        this.rebuildFilteredRows();
        this.detectNewDiscoveryPopups();
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            TEXTURE,
            this.leftPos,
            this.topPos,
            0.0F,
            0.0F,
            this.imageWidth,
            this.imageHeight,
            GUI_WIDTH,
            GUI_HEIGHT
        );

        int tierPanelX = this.leftPos + TIER_TEXT_X;
        int tierPanelY = this.topPos + TIER_TEXT_Y;
        int tierPanelWidth = TIER_TEXT_WIDTH;
        int tierPanelHeight = TIER_TEXT_HEIGHT;

        int meBarX = this.leftPos + ME_BAR_X;
        int meBarY = this.topPos + ME_BAR_Y;
        long currentME = this.menu.getCurrentMEClient();
        long maxME = this.menu.getMaxMEClient();
        renderEnergyBar(guiGraphics, meBarX, meBarY, currentME, maxME);
        int scrollbarX = this.leftPos + SCROLLBAR_X;
        int scrollbarY = this.topPos + SCROLLBAR_Y;
        int scrollbarRight = scrollbarX + SCROLLBAR_WIDTH;
        int scrollbarBottom = scrollbarY + SCROLLBAR_HEIGHT;

        if (isListScrollable()) {
            int thumbHeight = getScrollbarThumbHeight();
            int thumbTravel = SCROLLBAR_HEIGHT - thumbHeight;
            int thumbY = scrollbarY + Math.max(0, (int) (this.scrollOffs * thumbTravel));
            int thumbLeft = scrollbarX;
            int thumbTop = thumbY;
            guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                getScrollbarThumbTexture(),
                thumbLeft,
                thumbTop,
                0.0F,
                0.0F,
                SCROLLBAR_WIDTH,
                thumbHeight,
                SCROLLBAR_WIDTH,
                thumbHeight
            );
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        alignSearchBoxTextVertically();
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        renderDisabledUpkeepFuelGhost(guiGraphics);
        renderForegroundText(guiGraphics, mouseX, mouseY);

        if (this.extractionWarningPopupOpen) {
            renderExtractionWarningPopup(guiGraphics, mouseX, mouseY);
            return;
        }
        if (this.amplificationPopup != null) {
            renderAmplificationWarningPopup(guiGraphics, mouseX, mouseY);
            return;
        }

        renderDiscoveryPopup(guiGraphics);
        renderEnergyBarTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen) {
            if (event.button() == 0) {
                if (isMouseOverExtractionWarningYes(event.x(), event.y())) {
                    sendDangerousExtractionPopupAction(true);
                    return true;
                }
                if (isMouseOverExtractionWarningNo(event.x(), event.y())) {
                    sendDangerousExtractionPopupAction(false);
                    return true;
                }
            }
            return true;
        }
        if (this.amplificationPopup != null) {
            if (event.button() == 0) {
                if (isMouseOverExtractionWarningYes(event.x(), event.y())) {
                    sendAmplificationPopupAction(this.amplificationPopup.serverIndex());
                    this.amplificationPopup = null;
                    return true;
                }
                if (isMouseOverExtractionWarningNo(event.x(), event.y())) {
                    this.amplificationPopup = null;
                    return true;
                }
            }
            return true;
        }

        if (isMouseOverScrollbar(event.x(), event.y()) && isListScrollable()) {
            this.scrolling = true;
            return true;
        }

        int hoveredIndex = getHoveredFilteredRowIndex(event.x(), event.y());
        if (hoveredIndex >= 0 && hoveredIndex < this.filteredRows.size()) {
            EnchantRow row = this.filteredRows.get(hoveredIndex);
            if (this.menu.isDormantClient()) {
                return true;
            }
            if (isAmplificationModeClient()) {
                if (event.button() == 0 && canAmplifyEnchantClient(row)) {
                    openAmplificationPopup(row);
                }
                return true;
            }

            MagicLibraryMenu.SelectionAction action = null;
            if (event.button() == 0) {
                action = event.hasShiftDown() ? MagicLibraryMenu.SelectionAction.MAX : MagicLibraryMenu.SelectionAction.INCREASE;
            } else if (event.button() == 1) {
                action = MagicLibraryMenu.SelectionAction.DECREASE;
            }

            if (action != null) {
                if (shouldSendSelectionAction(row, action)) {
                    sendSelectionAction(row.serverIndex(), action);
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen || this.amplificationPopup != null) {
            return true;
        }

        if (this.scrolling && isListScrollable()) {
            int thumbHeight = getScrollbarThumbHeight();
            int top = this.topPos + SCROLLBAR_Y;
            int travel = SCROLLBAR_HEIGHT - thumbHeight;
            if (travel <= 0) {
                this.scrollOffs = 0.0F;
            } else {
                this.scrollOffs = ((float) event.y() - top - (thumbHeight / 2.0F)) / travel;
                this.scrollOffs = Math.clamp(this.scrollOffs, 0.0F, 1.0F);
            }
            updateStartRowFromScroll();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen || this.amplificationPopup != null) {
            return true;
        }

        this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen || this.amplificationPopup != null) {
            return true;
        }

        if (isMouseOverListViewport(mouseX, mouseY)) {
            if (isListScrollable()) {
                int maxStart = getMaxStartRow();
                if (maxStart > 0) {
                    this.scrollOffs = Math.clamp(this.scrollOffs - (float) scrollY / maxStart, 0.0F, 1.0F);
                    updateStartRowFromScroll();
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen || this.amplificationPopup != null) {
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(event);
            }
            if (this.searchBox.keyPressed(event)) {
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen || this.amplificationPopup != null) {
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.charTyped(event)) {
                return true;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        return !this.extractionWarningPopupOpen && this.amplificationPopup == null && super.shouldCloseOnEsc();
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ContainerInput containerInput) {
        syncExtractionWarningPopupState();
        syncAmplificationPopupState();
        if (this.extractionWarningPopupOpen || this.amplificationPopup != null) {
            return;
        }

        super.slotClicked(slot, slotId, mouseButton, containerInput);
    }

    private void renderForegroundText(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        Font guiFont = this.font;
        String tierLabel = getTierPanelLabel(this.menu.getTier());
        int tierPanelX = this.leftPos + TIER_TEXT_X;
        int tierPanelY = this.topPos + TIER_TEXT_Y;
        int tierPanelWidth = TIER_TEXT_WIDTH;
        int tierPanelHeight = TIER_TEXT_HEIGHT;
        int tierCenterX = tierPanelX + (tierPanelWidth / 2);
        int tierTextY = tierPanelY + ((tierPanelHeight - guiFont.lineHeight) / 2);
        Component tierLabelComponent = toLibraryFontText(tierLabel);
        int tierLabelX = tierCenterX - (guiFont.width(tierLabelComponent) / 2);
        guiGraphics.text(guiFont, tierLabelComponent, tierLabelX, tierTextY, TEXT_COLOR_WHITE, true);

        int listAbsX = this.leftPos + LIST_X;
        int listAbsY = this.topPos + LIST_Y;
        int listViewportWidth = getListViewportWidth();
        int listRight = listAbsX + listViewportWidth;
        int listBottom = listAbsY + LIST_HEIGHT;
        int hoveredIndex = getHoveredFilteredRowIndex(mouseX, mouseY);

        if (this.filteredRows.isEmpty()) {
            String emptyText = "No enchantments found";
            Component emptyComponent = toLibraryFontText(emptyText);
            int emptyTextWidth = Math.round(guiFont.width(emptyComponent) * ENCHANT_TEXT_SCALE);
            int emptyTextHeight = Math.round(guiFont.lineHeight * ENCHANT_TEXT_SCALE);
            int emptyActualX = listAbsX + ((listViewportWidth - emptyTextWidth) / 2);
            int emptyActualY = listAbsY + ((LIST_HEIGHT - emptyTextHeight) / 2);
            int emptyDrawX = Math.round(emptyActualX / ENCHANT_TEXT_SCALE);
            int emptyDrawY = Math.round(emptyActualY / ENCHANT_TEXT_SCALE);
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(ENCHANT_TEXT_SCALE, ENCHANT_TEXT_SCALE);
            guiGraphics.text(guiFont, emptyComponent, emptyDrawX, emptyDrawY, TEXT_COLOR_MUTED, true);
            guiGraphics.pose().popMatrix();
            return;
        }

        int visibleRows = getVisibleRowCount();
        int endExclusive = Math.min(this.filteredRows.size(), this.startRow + visibleRows);
        guiGraphics.enableScissor(listAbsX, listAbsY, listAbsX + listViewportWidth, listAbsY + LIST_HEIGHT);
        int rowTextPixelWidth = getRowTextPixelWidth();

        for (int i = this.startRow; i < endExclusive; i++) {
            EnchantRow row = this.filteredRows.get(i);
            int rowY = listAbsY + ROW_START_OFFSET_Y + ((i - this.startRow) * ROW_HEIGHT);
            guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                row.group().texture,
                listAbsX,
                rowY,
                0.0F,
                0.0F,
                listViewportWidth,
                ROW_BACKGROUND_HEIGHT,
                ENCHANT_GROUP_TEXTURE_WIDTH,
                ENCHANT_GROUP_TEXTURE_HEIGHT,
                ENCHANT_GROUP_TEXTURE_WIDTH,
                ENCHANT_GROUP_TEXTURE_HEIGHT
            );
            if (i == hoveredIndex) {
                guiGraphics.fill(listAbsX, rowY, listAbsX + listViewportWidth, rowY + ROW_BACKGROUND_HEIGHT, ROW_HIGHLIGHT);
            }

            String rowText = formatRowText(row);
            String clipped = guiFont.plainSubstrByWidth(rowText, Math.round(rowTextPixelWidth / ENCHANT_TEXT_SCALE));
            int actualTextX = listAbsX + ROW_TEXT_OFFSET_X;
            int actualTextY = rowY + Math.round((ROW_BACKGROUND_HEIGHT - (guiFont.lineHeight * ENCHANT_TEXT_SCALE)) * 0.5f);
            int drawX = Math.round(actualTextX / ENCHANT_TEXT_SCALE);
            int drawY = Math.round(actualTextY / ENCHANT_TEXT_SCALE);
            int rowColor = this.menu.isDormantClient()
                ? TEXT_COLOR_DORMANT
                : (
                    isAmplificationModeClient()
                        ? (canAmplifyEnchantClient(row) ? TEXT_COLOR_AMPLIFICATION_READY : TEXT_COLOR_AMPLIFICATION_BLOCKED)
                        : (row.currentLevel() > 0 ? 0xFF80FF80 : TEXT_COLOR_WHITE)
                );
            Component rowComponent = toLibraryFontText(clipped);

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(ENCHANT_TEXT_SCALE, ENCHANT_TEXT_SCALE);
            guiGraphics.text(guiFont, rowComponent, drawX, drawY, rowColor, true);
            guiGraphics.pose().popMatrix();
        }

        guiGraphics.disableScissor();

        if (hoveredIndex >= 0 && hoveredIndex < this.filteredRows.size()) {
            EnchantRow row = this.filteredRows.get(hoveredIndex);
            if (isAmplificationModeClient()) {
                renderAmplificationTooltip(guiGraphics, row, mouseX, mouseY);
            } else {
                renderRowTooltip(guiGraphics, row, mouseX, mouseY);
            }
        }
    }

    private void renderRowTooltip(GuiGraphicsExtractor guiGraphics, EnchantRow row, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        TooltipCostPreview preview = getTooltipCostPreview(row);
        String titleText = getHoverDisplayName(row, preview);
        lines.add(
            Component.literal(titleText).withStyle(style -> style.withUnderlined(true))
        );

        if (this.menu.isDormantClient()) {
            lines.add(Component.literal("Dormant \u2013 insert fuel to activate"));
            guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }

        lines.add(Component.literal("Stored: " + formatNumber(row.storedPoints()) + " points"));

        if (preview.maxLevelReached()) {
            lines.add(Component.literal("Max lvl reached").withStyle(style -> style.withColor(TOOLTIP_MAX_LEVEL_COLOR)));
            guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }

        String actionText = row.baseLevel() > 0 ? "Upgrade" : "New";
        lines.add(Component.literal(actionText));

        lines.add(Component.literal("Cost:"));

        var pointCostLine = Component.literal("- " + formatNumber(preview.pointCost()) + " points");
        if (!preview.pointsAffordable()) {
            pointCostLine = pointCostLine.withStyle(style -> style.withColor(TOOLTIP_UNAFFORDABLE_COLOR));
        }
        lines.add(pointCostLine);

        if (isTier3GearEnchantingInput()) {
            var xpCostLine = Component.literal("- " + formatNumber(preview.xpCost()) + " XP");
            if (!preview.xpAffordable()) {
                xpCostLine = xpCostLine.withStyle(style -> style.withColor(TOOLTIP_UNAFFORDABLE_COLOR));
            }
            lines.add(xpCostLine);
        }

        guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private void renderAmplificationTooltip(GuiGraphicsExtractor guiGraphics, EnchantRow row, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        Holder.Reference<Enchantment> holder = resolveEnchantment(row.enchantmentId());
        if (holder == null) {
            return;
        }
        int currentMax = getAmplificationCurrentLevelClient(row);
        int vanillaMax = Math.max(1, holder.value().getMaxLevel());
        boolean amplifiable = MagicLibraryConfig.isAmplifiableEnchant(row.enchantmentId(), vanillaMax);
        int effectiveCap = MagicLibraryConfig.getEffectiveAmplifiedMaxLevel(row.enchantmentId(), vanillaMax);
        int nextLevel = MagicLibraryConfig.getSelectableTargetLevel(currentMax, currentMax + 1, effectiveCap);
        boolean atCap = MagicLibraryConfig.isAtEffectiveAmplifiedMax(row.enchantmentId(), vanillaMax, currentMax);
        String title = (atCap || (isSingleLevelEnchant(row) && currentMax <= 1))
            ? row.name()
            : (row.name() + " " + RomanNumeralUtil.toRoman(nextLevel));

        lines.add(Component.literal(title).withStyle(style -> style.withUnderlined(true)));

        if (this.menu.isDormantClient()) {
            lines.add(Component.literal("Dormant - insert fuel to activate"));
            guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }

        lines.add(Component.literal("Amplify max level by 1"));
        lines.add(Component.literal("Current max: " + RomanNumeralUtil.toRoman(Math.max(1, currentMax))));

        if (atCap) {
            String blockedText = amplifiable ? "Amplification cap reached" : "Amplification disabled for this enchant";
            lines.add(Component.literal(blockedText).withStyle(style -> style.withColor(TOOLTIP_MAX_LEVEL_COLOR)));
            guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }

        lines.add(Component.literal("Next max: " + RomanNumeralUtil.toRoman(nextLevel)));
        lines.add(Component.literal("Cost:"));

        int xpCost = getAmplificationXpCostClient(row, currentMax);
        int playerXPLevels = this.minecraft.player != null ? this.minecraft.player.experienceLevel : 0;
        boolean hasInfiniteMaterials = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

        var xpCostLine = Component.literal("- " + formatNumber(xpCost) + " XP");
        if (!hasInfiniteMaterials && playerXPLevels < xpCost) {
            xpCostLine = xpCostLine.withStyle(style -> style.withColor(TOOLTIP_UNAFFORDABLE_COLOR));
        }
        lines.add(xpCostLine);

        guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private boolean isAmplificationModeClient() {
        return this.menu.isAmplificationModeClient();
    }

    private int getAmplificationCurrentLevelClient(EnchantRow row) {
        return Math.max(1, row.maxDiscoveredLevel());
    }

    private boolean canAmplifyEnchantClient(EnchantRow row) {
        if (!isAmplificationModeClient()) {
            return false;
        }
        Holder.Reference<Enchantment> holder = resolveEnchantment(row.enchantmentId());
        if (holder == null) {
            return false;
        }
        int currentMax = getAmplificationCurrentLevelClient(row);
        int vanillaMax = Math.max(1, holder.value().getMaxLevel());
        return MagicLibraryConfig.isValidTomeUpgradeTarget(row.enchantmentId(), vanillaMax, currentMax);
    }

    private int getAmplificationXpCostClient(EnchantRow row, int currentMax) {
        Holder.Reference<Enchantment> holder = resolveEnchantment(row.enchantmentId());
        if (holder == null) {
            return 0;
        }
        int vanillaMax = Math.max(1, holder.value().getMaxLevel());
        int upgradeNumber = MagicLibraryConfig.getAmplificationUpgradeNumber(vanillaMax, currentMax);
        return MagicLibraryConfig.getAmplificationXpCost(upgradeNumber);
    }

    private void openAmplificationPopup(EnchantRow row) {
        int currentMax = getAmplificationCurrentLevelClient(row);
        int nextLevel = currentMax + 1;
        Holder.Reference<Enchantment> holder = resolveEnchantment(row.enchantmentId());
        if (holder != null) {
            int vanillaMax = Math.max(1, holder.value().getMaxLevel());
            int effectiveCap = MagicLibraryConfig.getEffectiveAmplifiedMaxLevel(row.enchantmentId(), vanillaMax);
            nextLevel = MagicLibraryConfig.getSelectableTargetLevel(currentMax, currentMax + 1, effectiveCap);
        }
        this.amplificationPopup = new AmplificationPopup(
            row.enchantmentId(),
            row.name(),
            currentMax,
            nextLevel,
            row.serverIndex()
        );
        this.scrolling = false;
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
    }

    private void syncAmplificationPopupState() {
        if (this.amplificationPopup == null) {
            return;
        }
        if (!isAmplificationModeClient()) {
            this.amplificationPopup = null;
            return;
        }

        boolean stillValid = false;
        for (EnchantRow row : this.filteredRows) {
            if (row.serverIndex() == this.amplificationPopup.serverIndex() && canAmplifyEnchantClient(row)) {
                stillValid = true;
                break;
            }
        }
        if (!stillValid) {
            this.amplificationPopup = null;
        }
    }

    private TooltipCostPreview getTooltipCostPreview(EnchantRow row) {
        int original = row.baseLevel();
        int prepared = row.preparedLevel();
        int target = getClickResultLevel(row);
        boolean maxLevelReached = MagicLibraryConfig.isAtEffectiveMax(prepared, getDisplayMaxLevel(row));
        if (target <= prepared) {
            return new TooltipCostPreview(prepared, maxLevelReached, 0L, 0L, true, true);
        }
        long pointCost = getPointCost(original, target);
        boolean pointsAffordable = pointCost <= row.storedPoints();

        long xpCost = 0L;
        boolean xpAffordable = true;
        if (isTier3GearEnchantingInput()) {
            long rawXp = getRawXPCost(original, target);
            xpCost = applyTier3Discount(rawXp);
            int playerXPLevels = this.minecraft.player != null ? this.minecraft.player.experienceLevel : 0;
            boolean hasInfiniteMaterials = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;
            xpAffordable = hasInfiniteMaterials || xpCost <= playerXPLevels;
        }

        return new TooltipCostPreview(target, false, pointCost, xpCost, pointsAffordable, xpAffordable);
    }

    private int getClickResultLevel(EnchantRow row) {
        int prepared = row.preparedLevel();
        int effectiveMax = getDisplayMaxLevel(row);
        return MagicLibraryConfig.getSelectableTargetLevel(prepared, prepared + 1, effectiveMax);
    }

    private String getHoverDisplayName(EnchantRow row, TooltipCostPreview preview) {
        if (isSingleLevelEnchant(row) || preview.maxLevelReached()) {
            return row.name();
        }
        return row.name() + " " + RomanNumeralUtil.toRoman(Math.max(1, preview.targetLevel()));
    }

    private boolean shouldSendSelectionAction(EnchantRow row, MagicLibraryMenu.SelectionAction action) {
        int base = row.baseLevel();
        int prepared = row.preparedLevel();
        int maxCraftable = getMaxCraftableLevelClient(row.enchantmentId(), prepared);

        if (action == MagicLibraryMenu.SelectionAction.DECREASE) {
            return prepared > base;
        }
        if (action == MagicLibraryMenu.SelectionAction.INCREASE) {
            return prepared < maxCraftable;
        }
        if (action == MagicLibraryMenu.SelectionAction.MAX) {
            return maxCraftable > prepared;
        }

        return false;
    }

    private void sendSelectionAction(int serverIndex, MagicLibraryMenu.SelectionAction action) {
        if (this.minecraft.player == null || this.minecraft.gameMode == null) {
            return;
        }
        int buttonId = MagicLibraryMenu.encodeSelectionButtonId(action, serverIndex);
        if (this.menu.clickMenuButton(this.minecraft.player, buttonId)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void rebuildFilteredRows() {
        Map<Identifier, EnchantData> storedData = this.menu.getStoredEnchantDataClient();
        Map<Identifier, Integer> amplifiedMaxLevels = this.menu.getAmplifiedMaxLevelsClient();
        Map<Identifier, Integer> preparedLevels = this.menu.getPreparedEnchantLevelsClient();
        List<Identifier> stableIds = this.menu.getStableSortedEnchantIdsClient();
        boolean amplificationMode = isAmplificationModeClient();
        boolean previewOutputActive = this.menu.isPendingOutputPreviewClient();
        ItemStack enchantingContext = getEnchantingContextInputClient();
        ItemStack outputItem = this.menu.getOutputItemClient();
        Map<Identifier, Integer> baseLevels = getCurrentBaseLevelsClient(enchantingContext, previewOutputActive);
        Map<Identifier, Integer> outputLevels = getItemEnchantmentLevels(outputItem);
        boolean hasOutputItem = !outputItem.isEmpty();
        ItemStack outputApplicabilityStack = hasOutputItem ? outputItem : enchantingContext;
        Map<Identifier, Integer> previewLevels = getCurrentPreviewEnchants(
            baseLevels,
            preparedLevels,
            outputLevels,
            hasOutputItem,
            previewOutputActive
        );
        Map<Identifier, Integer> stableIndexById = new HashMap<>();
        for (int i = 0; i < stableIds.size(); i++) {
            stableIndexById.put(stableIds.get(i), i);
        }

        String query = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<EnchantRow> rows = new ArrayList<>();
        for (Map.Entry<Identifier, EnchantData> entry : storedData.entrySet()) {
            Identifier enchantmentId = entry.getKey();
            Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
            String name = holder != null ? holder.value().description().getString() : enchantmentId.toString();
            int stableIndex = stableIndexById.getOrDefault(enchantmentId, -1);
            if (stableIndex < 0) {
                continue;
            }

            EnchantData data = entry.getValue();
            int effectiveMaxLevel = getEffectiveLibraryMaxLevelClient(enchantmentId, data, amplifiedMaxLevels);
            if (amplificationMode && !shouldShowInTomeMode(enchantmentId, holder, effectiveMaxLevel)) {
                continue;
            }
            if (!matchesSearchTerm(query, name, holder)) {
                continue;
            }

            int baseLevel = baseLevels.getOrDefault(enchantmentId, 0);
            int currentLevel = previewLevels.getOrDefault(enchantmentId, baseLevel);
            if (!amplificationMode) {
                if (holder != null && !isApplicableToOutput(outputApplicabilityStack, holder, currentLevel)) {
                    continue;
                }
                if (holder != null && !isCompatibleWithPreview(enchantmentId, holder, previewLevels)) {
                    continue;
                }
            }
            int preparedLevel = Math.max(baseLevel, preparedLevels.getOrDefault(enchantmentId, currentLevel));
            rows.add(
                new EnchantRow(
                    enchantmentId,
                    name,
                    data.storedPoints(),
                    effectiveMaxLevel,
                    baseLevel,
                    currentLevel,
                    preparedLevel,
                    stableIndex,
                    resolveEnchantRowGroup(holder)
                )
            );
        }

        rows.sort(Comparator.comparing(EnchantRow::name, String.CASE_INSENSITIVE_ORDER));
        this.filteredRows = rows;
        clampScroll();
    }

    private boolean shouldShowInTomeMode(
        Identifier enchantmentId,
        Holder.Reference<Enchantment> holder,
        int currentMaxLevel
    ) {
        if (holder == null || currentMaxLevel <= 0) {
            return false;
        }
        int vanillaMaxLevel = Math.max(1, holder.value().getMaxLevel());
        return MagicLibraryConfig.isValidTomeUpgradeTarget(enchantmentId, vanillaMaxLevel, currentMaxLevel);
    }

    private Map<Identifier, Integer> getCurrentPreviewEnchants(
        Map<Identifier, Integer> baseLevels,
        Map<Identifier, Integer> preparedLevels,
        Map<Identifier, Integer> outputLevels,
        boolean hasOutputItem,
        boolean previewOutputActive
    ) {
        if (hasOutputItem && !previewOutputActive) {
            return outputLevels;
        }

        Map<Identifier, Integer> previewLevels = new HashMap<>(baseLevels);
        for (Map.Entry<Identifier, Integer> entry : preparedLevels.entrySet()) {
            int level = entry.getValue();
            if (level > 0) {
                previewLevels.put(entry.getKey(), Math.max(level, previewLevels.getOrDefault(entry.getKey(), 0)));
            }
        }
        return previewLevels;
    }

    private int getEffectiveLibraryMaxLevelClient(
        Identifier enchantmentId,
        EnchantData data,
        Map<Identifier, Integer> amplifiedMaxLevels
    ) {
        int discoveredMax = data != null ? data.maxDiscoveredLevel() : 0;
        int amplifiedMax = amplifiedMaxLevels.getOrDefault(enchantmentId, 0);
        int rawEffectiveMax = Math.max(Math.max(0, discoveredMax), Math.max(0, amplifiedMax));
        Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
        if (holder == null) {
            return rawEffectiveMax > 0 ? Math.max(1, rawEffectiveMax) : 0;
        }

        int vanillaMaxLevel = Math.max(1, holder.value().getMaxLevel());
        return MagicLibraryConfig.getEffectiveLibraryMaxLevel(enchantmentId, vanillaMaxLevel, discoveredMax, amplifiedMax);
    }

    private boolean matchesSearchTerm(String query, String enchantName, Holder.Reference<Enchantment> enchantment) {
        if (query.isEmpty()) {
            return true;
        }

        String normalizedName = enchantName.toLowerCase(Locale.ROOT);
        String[] tokens = query.split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (normalizedName.contains(token)) {
                continue;
            }
            if (enchantment != null && matchesSearchAliasToken(token, enchantment)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean matchesSearchAliasToken(String token, Holder<Enchantment> enchantment) {
        SearchAliasTarget target = SEARCH_ALIAS_TARGETS.get(token);
        return target != null && target.matches(enchantment);
    }

    private EnchantRowGroup resolveEnchantRowGroup(@Nullable Holder<Enchantment> enchantment) {
        if (enchantment == null) {
            return EnchantRowGroup.MISC;
        }

        EnchantRowGroup primaryGroup = resolveEnchantRowGroup(enchantment, true);
        if (primaryGroup != EnchantRowGroup.MISC) {
            return primaryGroup;
        }
        return resolveEnchantRowGroup(enchantment, false);
    }

    private EnchantRowGroup resolveEnchantRowGroup(Holder<Enchantment> enchantment, boolean primaryOnly) {
        boolean appliesToSword = matchesSampleItem(enchantment, Items.DIAMOND_SWORD, primaryOnly);
        boolean appliesToSpear = matchesSampleItem(enchantment, Items.DIAMOND_SPEAR, primaryOnly);
        boolean appliesToAxe = matchesSampleItem(enchantment, Items.DIAMOND_AXE, primaryOnly);
        boolean appliesToPickaxe = matchesSampleItem(enchantment, Items.DIAMOND_PICKAXE, primaryOnly);
        boolean appliesToShovel = matchesSampleItem(enchantment, Items.DIAMOND_SHOVEL, primaryOnly);
        boolean appliesToHoe = matchesSampleItem(enchantment, Items.DIAMOND_HOE, primaryOnly);
        boolean appliesToBow = matchesSampleItem(enchantment, Items.BOW, primaryOnly);
        boolean appliesToCrossbow = matchesSampleItem(enchantment, Items.CROSSBOW, primaryOnly);
        boolean appliesToMace = matchesSampleItem(enchantment, Items.MACE, primaryOnly);
        boolean appliesToTrident = matchesSampleItem(enchantment, Items.TRIDENT, primaryOnly);
        boolean appliesToFishingRod = matchesSampleItem(enchantment, Items.FISHING_ROD, primaryOnly);
        boolean appliesToShears = matchesSampleItem(enchantment, Items.SHEARS, primaryOnly);
        boolean appliesToHead = matchesSampleItem(enchantment, Items.DIAMOND_HELMET, primaryOnly);
        boolean appliesToChest = matchesSampleItem(enchantment, Items.DIAMOND_CHESTPLATE, primaryOnly);
        boolean appliesToLegs = matchesSampleItem(enchantment, Items.DIAMOND_LEGGINGS, primaryOnly);
        boolean appliesToFeet = matchesSampleItem(enchantment, Items.DIAMOND_BOOTS, primaryOnly);

        boolean melee = appliesToSword || appliesToSpear || (appliesToMace && appliesToAxe);
        boolean tools = appliesToPickaxe
            || appliesToShovel
            || appliesToHoe
            || appliesToFishingRod
            || appliesToShears
            || (appliesToAxe && !appliesToSword && !appliesToSpear && !appliesToMace);
        boolean ranged = appliesToBow || appliesToCrossbow;
        boolean special = appliesToTrident || (appliesToMace && !appliesToSword && !appliesToSpear && !appliesToAxe);
        boolean armor = appliesToHead || appliesToChest || appliesToLegs || appliesToFeet;
        int familyCount = 0;
        familyCount += melee ? 1 : 0;
        familyCount += tools ? 1 : 0;
        familyCount += ranged ? 1 : 0;
        familyCount += special ? 1 : 0;
        familyCount += armor ? 1 : 0;

        if (familyCount != 1) {
            return EnchantRowGroup.MISC;
        }
        if (melee) {
            return EnchantRowGroup.MELEE_WEAPONS;
        }
        if (tools) {
            return EnchantRowGroup.TOOLS;
        }
        if (ranged) {
            return EnchantRowGroup.RANGED_WEAPONS;
        }
        if (special) {
            return EnchantRowGroup.SPECIAL_WEAPONS;
        }
        return EnchantRowGroup.ARMOR;
    }

    private boolean matchesSampleItem(Holder<Enchantment> enchantment, net.minecraft.world.item.Item item, boolean primaryOnly) {
        ItemStack stack = new ItemStack(item);
        return primaryOnly ? enchantment.value().isPrimaryItem(stack) : enchantment.value().canEnchant(stack);
    }

    private boolean isApplicableToOutput(ItemStack outputStack, Holder<Enchantment> enchantment, int currentLevel) {
        return canApplyEnchantmentToInput(outputStack, enchantment, currentLevel);
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

    private String formatRowText(EnchantRow row) {
        Map<Identifier, Integer> outputLevels = getItemEnchantmentLevels(this.menu.getOutputItemClient());
        boolean enchantExistsOnOutputItem = outputLevels.getOrDefault(row.enchantmentId(), 0) > 0;
        int maxLevel = getDisplayMaxLevel(row);
        if (isSingleLevelEnchant(row)) {
            return row.name();
        }

        String maxText = RomanNumeralUtil.toRoman(maxLevel);
        if (enchantExistsOnOutputItem) {
            String currentText = RomanNumeralUtil.toRoman(getDisplayCurrentLevel(row, outputLevels));
            return row.name() + " - " + currentText + " / " + maxText;
        }
        return row.name() + " - " + maxText;
    }

    private boolean isSingleLevelEnchant(EnchantRow row) {
        return isSingleLevelEnchant(row.enchantmentId());
    }

    private boolean isSingleLevelEnchant(Identifier enchantmentId) {
        Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
        if (holder != null) {
            return holder.value().getMaxLevel() <= 1;
        }
        return false;
    }

    private int getDisplayMaxLevel(EnchantRow row) {
        return Math.max(1, row.maxDiscoveredLevel());
    }

    private int getDisplayCurrentLevel(EnchantRow row, Map<Identifier, Integer> outputLevels) {
        int outputLevel = outputLevels.getOrDefault(row.enchantmentId(), row.currentLevel());
        return Math.max(1, outputLevel);
    }

    private Map<Identifier, Integer> getItemEnchantmentLevels(ItemStack stack) {
        if (stack.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, Integer> levels = new HashMap<>();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(key -> levels.put(key.identifier(), entry.getIntValue()));
        }
        return levels;
    }

    private void detectNewDiscoveryPopups() {
        Map<Identifier, EnchantData> stored = this.menu.getStoredEnchantDataClient();

        List<Identifier> newlyDiscovered = new ArrayList<>();
        for (Identifier enchantmentId : stored.keySet()) {
            if (!this.knownDiscoveredEnchantIds.contains(enchantmentId)) {
                newlyDiscovered.add(enchantmentId);
            }
        }
        if (!newlyDiscovered.isEmpty()) {
            newlyDiscovered.sort(Comparator.comparing(Identifier::toString, String.CASE_INSENSITIVE_ORDER));
            List<String> discoveredNames = new ArrayList<>();
            for (Identifier enchantmentId : newlyDiscovered) {
                Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
                discoveredNames.add(holder != null ? holder.value().description().getString() : enchantmentId.toString());
            }
            discoveredNames.sort(String.CASE_INSENSITIVE_ORDER);
            List<String> lines = new ArrayList<>();
            lines.add("New Enchantment Discovered");
            lines.addAll(discoveredNames);
            this.discoveryPopups.addLast(new DiscoveryPopup(lines, this.popupTickCounter));
        }

        this.knownDiscoveredEnchantIds.clear();
        this.knownDiscoveredEnchantIds.addAll(stored.keySet());
    }

    private void renderDiscoveryPopup(GuiGraphicsExtractor guiGraphics) {
        while (!this.discoveryPopups.isEmpty() && this.popupTickCounter - this.discoveryPopups.peekFirst().startTick() >= TOTAL_TICKS) {
            this.discoveryPopups.removeFirst();
        }
        if (this.discoveryPopups.isEmpty()) {
            return;
        }

        DiscoveryPopup popup = this.discoveryPopups.peekFirst();
        int age = this.popupTickCounter - popup.startTick();
        float alpha;
        if (age < FADE_IN_TICKS) {
            alpha = age / (float) FADE_IN_TICKS;
        } else if (age < FADE_IN_TICKS + DISPLAY_TICKS) {
            alpha = 1.0F;
        } else {
            int fadeOutAge = age - (FADE_IN_TICKS + DISPLAY_TICKS);
            alpha = 1.0F - (fadeOutAge / (float) FADE_OUT_TICKS);
        }
        alpha = Math.clamp(alpha, 0.0F, 1.0F);
        if (alpha <= 0.0F) {
            return;
        }

        Font guiFont = this.font;
        List<String> lines = popup.lines();
        if (lines.isEmpty()) {
            return;
        }

        String title = "New Enchantment Discovered";
        List<String> enchantNames = lines.size() > 1 ? lines.subList(1, lines.size()) : List.of();
        int lineHeight = guiFont.lineHeight;
        int nameGap = 2;
        int titleGap = 4;

        int blockHeight = lineHeight;
        if (!enchantNames.isEmpty()) {
            blockHeight += titleGap + enchantNames.size() * lineHeight + ((enchantNames.size() - 1) * nameGap);
        }

        int centerX = this.width / 2;
        int y = (this.height - blockHeight) / 2;
        int textAlpha = Math.clamp((int) (255 * alpha), 0, 255);
        int titleColor = (textAlpha << 24) | 0x00FFFFFF;
        int enchantColor = (textAlpha << 24) | 0x00FFD86B;

        Component titleComponent = toLibraryFontText(title);
        int titleX = centerX - (guiFont.width(titleComponent) / 2);
        guiGraphics.text(guiFont, titleComponent, titleX, y, titleColor, true);

        int lineY = y + lineHeight + titleGap;
        for (String enchantName : enchantNames) {
            Component nameComponent = toLibraryFontText(enchantName);
            int drawX = centerX - (guiFont.width(nameComponent) / 2);
            guiGraphics.text(guiFont, nameComponent, drawX, lineY, enchantColor, true);
            lineY += lineHeight + nameGap;
        }
    }

    private void renderExtractionWarningPopup(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, this.width, this.height, MODAL_DIM_COLOR);

        int popupX = getExtractionWarningPopupX();
        int popupY = getExtractionWarningPopupY();
        int popupRight = popupX + EXTRACTION_WARNING_WIDTH;
        int popupBottom = popupY + EXTRACTION_WARNING_HEIGHT;

        guiGraphics.fill(popupX, popupY, popupRight, popupBottom, 0xFF1E1E1E);
        guiGraphics.fill(popupX, popupY, popupRight, popupY + 1, 0xFF3E3E3E);
        guiGraphics.fill(popupX, popupBottom - 1, popupRight, popupBottom, 0xFF090909);
        guiGraphics.fill(popupX, popupY, popupX + 1, popupBottom, 0xFF3E3E3E);
        guiGraphics.fill(popupRight - 1, popupY, popupRight, popupBottom, 0xFF090909);

        Font guiFont = this.font;
        String title = "WARNING!";
        String line1 = "This extraction will break the item.";
        String line2 = "Do you want to continue?";

        Component titleText = toLibraryFontText(title);
        Component line1Text = toLibraryFontText(line1);
        Component line2Text = toLibraryFontText(line2);
        int centerX = popupX + (EXTRACTION_WARNING_WIDTH / 2);

        guiGraphics.centeredText(guiFont, titleText, centerX, popupY + 8, 0xFFFFE28A);
        guiGraphics.centeredText(guiFont, line1Text, centerX, popupY + 22, 0xFFFFFFFF);
        guiGraphics.centeredText(guiFont, line2Text, centerX, popupY + 32, 0xFFFFFFFF);

        renderExtractionWarningButton(guiGraphics, mouseX, mouseY, true);
        renderExtractionWarningButton(guiGraphics, mouseX, mouseY, false);
    }

    private void renderAmplificationWarningPopup(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        AmplificationPopup popup = this.amplificationPopup;
        if (popup == null) {
            return;
        }

        guiGraphics.fill(0, 0, this.width, this.height, MODAL_DIM_COLOR);

        int popupX = getExtractionWarningPopupX();
        int popupY = getExtractionWarningPopupY();
        int popupRight = popupX + EXTRACTION_WARNING_WIDTH;
        int popupBottom = popupY + EXTRACTION_WARNING_HEIGHT;

        guiGraphics.fill(popupX, popupY, popupRight, popupBottom, 0xFF1E1E1E);
        guiGraphics.fill(popupX, popupY, popupRight, popupY + 1, 0xFF3E3E3E);
        guiGraphics.fill(popupX, popupBottom - 1, popupRight, popupBottom, 0xFF090909);
        guiGraphics.fill(popupX, popupY, popupX + 1, popupBottom, 0xFF3E3E3E);
        guiGraphics.fill(popupRight - 1, popupY, popupRight, popupBottom, 0xFF090909);

        Font guiFont = this.font;
        String title = "WARNING!";
        String line1 = "Are you sure you want to upgrade";
        String currentDisplay = getAmplificationPopupCurrentDisplay(popup);
        String line2 = "\"" + currentDisplay + "\" to level \"" + RomanNumeralUtil.toRoman(popup.nextLevel()) + "\"?";

        Component titleText = toLibraryFontText(title);
        Component line1Text = toLibraryFontText(line1);
        Component line2Text = toLibraryFontText(line2);
        int centerX = popupX + (EXTRACTION_WARNING_WIDTH / 2);

        guiGraphics.centeredText(guiFont, titleText, centerX, popupY + 8, 0xFFFFE28A);
        guiGraphics.centeredText(guiFont, line1Text, centerX, popupY + 22, 0xFFFFFFFF);
        guiGraphics.centeredText(guiFont, line2Text, centerX, popupY + 32, 0xFFFFFFFF);

        renderExtractionWarningButton(guiGraphics, mouseX, mouseY, true);
        renderExtractionWarningButton(guiGraphics, mouseX, mouseY, false);
    }

    private void renderExtractionWarningButton(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean yesButton) {
        int buttonX = yesButton ? getExtractionWarningYesX() : getExtractionWarningNoX();
        int buttonY = getExtractionWarningButtonsY();
        int buttonRight = buttonX + EXTRACTION_WARNING_BUTTON_WIDTH;
        int buttonBottom = buttonY + EXTRACTION_WARNING_BUTTON_HEIGHT;
        boolean hovered = mouseX >= buttonX && mouseX < buttonRight && mouseY >= buttonY && mouseY < buttonBottom;

        int base = hovered ? 0xFF555555 : 0xFF3A3A3A;
        guiGraphics.fill(buttonX, buttonY, buttonRight, buttonBottom, base);
        guiGraphics.fill(buttonX, buttonY, buttonRight, buttonY + 1, 0xFF7A7A7A);
        guiGraphics.fill(buttonX, buttonBottom - 1, buttonRight, buttonBottom, 0xFF1A1A1A);
        guiGraphics.fill(buttonX, buttonY, buttonX + 1, buttonBottom, 0xFF7A7A7A);
        guiGraphics.fill(buttonRight - 1, buttonY, buttonRight, buttonBottom, 0xFF1A1A1A);

        String label = yesButton ? "Yes" : "No";
        Component labelText = toLibraryFontText(label);
        int textY = buttonY + (EXTRACTION_WARNING_BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1;
        int buttonCenterX = buttonX + (EXTRACTION_WARNING_BUTTON_WIDTH / 2);
        guiGraphics.centeredText(this.font, labelText, buttonCenterX, textY, 0xFFFFFFFF);
    }

    private void drawSlotFrame(GuiGraphicsExtractor guiGraphics, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.menu.slots.size()) {
            return;
        }
        Slot slot = this.menu.slots.get(slotIndex);
        drawFrame(
            guiGraphics,
            this.leftPos + slot.x - SLOT_FRAME_OUTSET,
            this.topPos + slot.y - SLOT_FRAME_OUTSET,
            SLOT_FRAME_SIZE,
            SLOT_FRAME_SIZE,
            FRAME_COLOR
        );
    }

    private void renderEnergyBar(GuiGraphicsExtractor guiGraphics, int x, int y, long currentME, long maxME) {
        drawRoundedRect(guiGraphics, x, y, ME_BAR_WIDTH, ME_BAR_HEIGHT, ME_BAR_BACKGROUND);

        if (ME_BAR_HEIGHT >= 4) {
            drawRoundedRect(
                guiGraphics,
                x + 1,
                y + 1,
                Math.max(0, ME_BAR_WIDTH - 2),
                Math.max(0, (ME_BAR_HEIGHT / 2) - 1),
                ME_BAR_TRACK_HIGHLIGHT
            );
        }

        int fillWidth = 0;
        if (maxME > 0L && currentME > 0L) {
            fillWidth = (int) ((currentME * ME_BAR_WIDTH) / maxME);
            fillWidth = Math.clamp(fillWidth, 0, ME_BAR_WIDTH);
        } else if (currentME <= 0L) {
            fillWidth = ME_BAR_WIDTH;
        }

        if (fillWidth <= 0) {
            return;
        }

        int baseColorStart = currentME <= 0L ? ME_BAR_DORMANT_FILL : ME_BAR_FILL_START;
        int baseColorEnd = currentME <= 0L ? ME_BAR_DORMANT_FILL : ME_BAR_FILL_END;
        drawRoundedGradient(guiGraphics, x, y, fillWidth, ME_BAR_HEIGHT, baseColorStart, baseColorEnd);

        if (currentME > 0L) {
            int glossHeight = Math.max(2, ME_BAR_HEIGHT / 3);
            drawRoundedGradient(
                guiGraphics,
                x + 1,
                y + 1,
                Math.max(0, fillWidth - 2),
                glossHeight,
                ME_BAR_FILL_HIGHLIGHT,
                0x11FFFFFF
            );

            int sheenWidth = Math.max(10, fillWidth / 4);
            drawRoundedGradient(
                guiGraphics,
                x + Math.max(0, (fillWidth / 5) - 1),
                y + 1,
                Math.min(sheenWidth, fillWidth),
                Math.max(2, ME_BAR_HEIGHT - 4),
                ME_BAR_FILL_SHEEN_START,
                ME_BAR_FILL_SHEEN_END
            );
        }

        if (ME_BAR_HEIGHT >= 3) {
            drawRoundedRect(
                guiGraphics,
                x + 1,
                y + ME_BAR_HEIGHT - 2,
                Math.max(0, fillWidth - 2),
                1,
                ME_BAR_FILL_BOTTOM_SHADE
            );
        }

        if (fillWidth < ME_BAR_WIDTH) {
            int separatorX = x + fillWidth;
            guiGraphics.fill(separatorX, y + 2, separatorX + 1, y + ME_BAR_HEIGHT - 2, ME_BAR_BORDER);
        }
    }

    private void renderDisabledUpkeepFuelGhost(GuiGraphicsExtractor guiGraphics) {
        if (MagicLibraryConfig.isUpkeepEnabled() || MagicLibraryBlockEntity.SLOT_FUEL >= this.menu.slots.size()) {
            return;
        }

        Slot fuelSlot = this.menu.slots.get(MagicLibraryBlockEntity.SLOT_FUEL);
        if (fuelSlot.hasItem()) {
            return;
        }

        int x = this.leftPos + fuelSlot.x;
        int y = this.topPos + fuelSlot.y;
        guiGraphics.fill(x, y, x + 16, y + 16, 0x88000000);
        guiGraphics.fakeItem(new ItemStack(Items.NETHER_STAR), x, y);
    }

    private void drawRoundedRect(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width <= 2 || height <= 2) {
            guiGraphics.fill(x, y, x + width, y + height, color);
            return;
        }

        guiGraphics.fill(x + 1, y, x + width - 1, y + height, color);
        guiGraphics.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private void drawRoundedGradient(
        GuiGraphicsExtractor guiGraphics,
        int x,
        int y,
        int width,
        int height,
        int startColor,
        int endColor
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width <= 2 || height <= 2) {
            guiGraphics.fillGradient(x, y, x + width, y + height, startColor, endColor);
            return;
        }

        guiGraphics.fillGradient(x + 1, y, x + width - 1, y + height, startColor, endColor);
        guiGraphics.fillGradient(x, y + 1, x + width, y + height - 1, startColor, endColor);
    }

    private void drawFrame(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int right = x + width;
        int bottom = y + height;
        guiGraphics.fill(x, y, right, y + 1, color);
        guiGraphics.fill(x, bottom - 1, right, bottom, color);
        guiGraphics.fill(x, y, x + 1, bottom, color);
        guiGraphics.fill(right - 1, y, right, bottom, color);
    }

    private void syncExtractionWarningPopupState() {
        this.extractionWarningPopupOpen = this.menu.isDangerousExtractionBlockedClient();
        if (this.extractionWarningPopupOpen) {
            this.amplificationPopup = null;
            this.scrolling = false;
            if (this.searchBox != null) {
                this.searchBox.setFocused(false);
            }
        }
    }

    private void sendDangerousExtractionPopupAction(boolean confirm) {
        if (this.minecraft.player == null || this.minecraft.gameMode == null) {
            return;
        }
        int buttonId = MagicLibraryMenu.encodeDangerousExtractionPopupButtonId(confirm);
        if (this.menu.clickMenuButton(this.minecraft.player, buttonId)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void sendAmplificationPopupAction(int serverIndex) {
        if (this.minecraft.player == null || this.minecraft.gameMode == null) {
            return;
        }
        int buttonId = MagicLibraryMenu.encodeAmplificationButtonId(serverIndex);
        if (this.menu.clickMenuButton(this.minecraft.player, buttonId)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private String getAmplificationPopupCurrentDisplay(AmplificationPopup popup) {
        if (isSingleLevelEnchant(popup.enchantmentId()) && popup.currentLevel() <= 1) {
            return popup.enchantName();
        }
        return popup.enchantName() + " " + RomanNumeralUtil.toRoman(Math.max(1, popup.currentLevel()));
    }

    private int getExtractionWarningPopupX() {
        return (this.width - EXTRACTION_WARNING_WIDTH) / 2;
    }

    private int getExtractionWarningPopupY() {
        return (this.height - EXTRACTION_WARNING_HEIGHT) / 2;
    }

    private int getExtractionWarningButtonsY() {
        return getExtractionWarningPopupY() + EXTRACTION_WARNING_BUTTON_Y_OFFSET;
    }

    private int getExtractionWarningYesX() {
        int left = getExtractionWarningPopupX();
        int totalButtonsWidth = (EXTRACTION_WARNING_BUTTON_WIDTH * 2) + EXTRACTION_WARNING_BUTTON_GAP;
        return left + (EXTRACTION_WARNING_WIDTH - totalButtonsWidth) / 2;
    }

    private int getExtractionWarningNoX() {
        return getExtractionWarningYesX() + EXTRACTION_WARNING_BUTTON_WIDTH + EXTRACTION_WARNING_BUTTON_GAP;
    }

    private boolean isMouseOverExtractionWarningYes(double mouseX, double mouseY) {
        int x = getExtractionWarningYesX();
        int y = getExtractionWarningButtonsY();
        return mouseX >= x
            && mouseX < x + EXTRACTION_WARNING_BUTTON_WIDTH
            && mouseY >= y
            && mouseY < y + EXTRACTION_WARNING_BUTTON_HEIGHT;
    }

    private boolean isMouseOverExtractionWarningNo(double mouseX, double mouseY) {
        int x = getExtractionWarningNoX();
        int y = getExtractionWarningButtonsY();
        return mouseX >= x
            && mouseX < x + EXTRACTION_WARNING_BUTTON_WIDTH
            && mouseY >= y
            && mouseY < y + EXTRACTION_WARNING_BUTTON_HEIGHT;
    }

    private int getHoveredFilteredRowIndex(double mouseX, double mouseY) {
        if (!isMouseOverListViewport(mouseX, mouseY) || this.filteredRows.isEmpty()) {
            return -1;
        }

        int rowOffset = (int) (mouseY - (this.topPos + LIST_Y + ROW_START_OFFSET_Y));
        if (rowOffset < 0) {
            return -1;
        }

        int localRow = rowOffset / ROW_HEIGHT;
        int rowLocalY = rowOffset % ROW_HEIGHT;
        if (rowLocalY >= ROW_BACKGROUND_HEIGHT) {
            return -1;
        }
        int absoluteRow = this.startRow + localRow;
        if (localRow < 0 || localRow >= getVisibleRowCount()) {
            return -1;
        }
        if (absoluteRow < 0 || absoluteRow >= this.filteredRows.size()) {
            return -1;
        }
        return absoluteRow;
    }

    private boolean isMouseOverListViewport(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + LIST_X
            && mouseX < this.leftPos + LIST_X + getListViewportWidth()
            && mouseY >= this.topPos + LIST_Y
            && mouseY < this.topPos + LIST_Y + LIST_HEIGHT;
    }

    private int getListViewportWidth() {
        return LIST_WIDTH;
    }

    private int getRowTextPixelWidth() {
        return Math.max(0, getListViewportWidth() - ROW_TEXT_OFFSET_X - ROW_TEXT_RIGHT_PADDING);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + SCROLLBAR_X
            && mouseX < this.leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH
            && mouseY >= this.topPos + SCROLLBAR_Y
            && mouseY < this.topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT;
    }

    private boolean isMouseOverEnergyBar(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + ME_BAR_X
            && mouseX < this.leftPos + ME_BAR_X + ME_BAR_WIDTH
            && mouseY >= this.topPos + ME_BAR_Y
            && mouseY < this.topPos + ME_BAR_Y + ME_BAR_HEIGHT;
    }

    private void renderEnergyBarTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (!isMouseOverEnergyBar(mouseX, mouseY)) {
            return;
        }

        long currentME = this.menu.getCurrentMEClient();
        long maxME = this.menu.getMaxMEClient();
        int upkeepTenths = this.menu.getUpkeepTenthsPerTickClient();
        if (!MagicLibraryConfig.isUpkeepEnabled()) {
            List<Component> lines = List.of(
                Component.literal("Fuel: disabled / infinite (100%)"),
                Component.literal("Upkeep: disabled")
            );
            guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }

        List<Component> lines = List.of(
            Component.literal("Fuel: " + formatNumber(currentME) + " / " + formatNumber(maxME) + " ME (" + formatPercent(currentME, maxME) + ")"),
            Component.literal("Upkeep: " + formatTenths(upkeepTenths) + " ME/t")
        );
        guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private int getVisibleRowCount() {
        return Math.max(1, (LIST_HEIGHT - (ROW_START_OFFSET_Y * 2)) / ROW_HEIGHT);
    }

    private boolean isListScrollable() {
        return this.filteredRows.size() > getVisibleRowCount();
    }

    private int getMaxStartRow() {
        return Math.max(0, this.filteredRows.size() - getVisibleRowCount());
    }

    private int getScrollbarThumbHeight() {
        return SCROLLBAR_WIDTH;
    }

    private Identifier getScrollbarThumbTexture() {
        return switch (this.menu.getTier()) {
            case TIER1 -> APPRENTICE_SCROLLBAR_THUMB_TEXTURE;
            case TIER2 -> ADEPT_SCROLLBAR_THUMB_TEXTURE;
            case TIER3 -> ARCHMAGE_SCROLLBAR_THUMB_TEXTURE;
        };
    }

    private void clampScroll() {
        if (!isListScrollable()) {
            this.scrollOffs = 0.0F;
            this.startRow = 0;
            return;
        }
        this.scrollOffs = Math.clamp(this.scrollOffs, 0.0F, 1.0F);
        updateStartRowFromScroll();
    }

    private void updateStartRowFromScroll() {
        int maxStart = getMaxStartRow();
        this.startRow = Math.max((int) (this.scrollOffs * maxStart + 0.5F), 0);
    }

    private int getMaxCraftableLevelClient(Identifier enchantmentId, int baseLevel) {
        Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
        if (holder == null) {
            return baseLevel;
        }

        Map<Identifier, EnchantData> storedData = this.menu.getStoredEnchantDataClient();
        EnchantData data = storedData.get(enchantmentId);
        if (data == null) {
            return baseLevel;
        }

        ItemStack input = getEnchantingContextInputClient();

        int maxAllowed = getEffectiveLibraryMaxLevelClient(enchantmentId, data, this.menu.getAmplifiedMaxLevelsClient());
        if (maxAllowed <= baseLevel) {
            return baseLevel;
        }

        int best = baseLevel;
        for (int level = baseLevel + 1; level <= maxAllowed; level++) {
            Map<Identifier, Integer> test = new HashMap<>(this.menu.getPreparedEnchantLevelsClient());
            test.put(enchantmentId, level);
            if (!isPreparedMapAffordableClient(test, input)) {
                break;
            }
            best = level;
        }
        return best;
    }

    private boolean isPreparedMapAffordableClient(Map<Identifier, Integer> preparedLevels, ItemStack input) {
        Map<Identifier, EnchantData> storedData = this.menu.getStoredEnchantDataClient();
        Map<Identifier, Integer> amplifiedMaxLevels = this.menu.getAmplifiedMaxLevelsClient();
        Map<Identifier, Integer> baseLevels = getCurrentBaseLevelsClient(input, this.menu.isPendingOutputPreviewClient());
        Map<Identifier, Integer> previewLevels = new HashMap<>(baseLevels);
        for (Map.Entry<Identifier, Integer> entry : preparedLevels.entrySet()) {
            int targetLevel = entry.getValue();
            if (targetLevel > 0) {
                previewLevels.put(entry.getKey(), Math.max(targetLevel, previewLevels.getOrDefault(entry.getKey(), 0)));
            }
        }
        long rawTotalXP = 0L;
        boolean needsXP = isTier3GearEnchantingInput(input);

        for (Map.Entry<Identifier, Integer> entry : preparedLevels.entrySet()) {
            Identifier enchantmentId = entry.getKey();
            int targetLevel = entry.getValue();
            int baseLevel = baseLevels.getOrDefault(enchantmentId, 0);
            if (targetLevel <= baseLevel) {
                continue;
            }

            EnchantData data = storedData.get(enchantmentId);
            if (data == null) {
                return false;
            }

            Holder.Reference<Enchantment> holder = resolveEnchantment(enchantmentId);
            if (holder == null) {
                return false;
            }

            if (!canApplyEnchantmentToInput(input, holder, baseLevel)) {
                return false;
            }

            if (!isCompatibleWithPreview(enchantmentId, holder, previewLevels)) {
                return false;
            }

            int maxAllowed = getEffectiveLibraryMaxLevelClient(enchantmentId, data, amplifiedMaxLevels);
            if (targetLevel > maxAllowed) {
                return false;
            }

            long pointCost = getPointCost(baseLevel, targetLevel);
            if (pointCost > data.storedPoints()) {
                return false;
            }

            if (needsXP) {
                rawTotalXP = saturatingAdd(rawTotalXP, getRawXPCost(baseLevel, targetLevel));
            }
        }

        if (needsXP) {
            long finalXP = applyTier3Discount(rawTotalXP);
            int playerXPLevels = this.minecraft.player != null ? this.minecraft.player.experienceLevel : 0;
            boolean hasInfiniteMaterials = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;
            if (!hasInfiniteMaterials && finalXP > playerXPLevels) {
                return false;
            }
        }

        return true;
    }

    private Map<Identifier, Integer> getCurrentBaseLevelsClient(ItemStack input, boolean previewOutputActive) {
        if (previewOutputActive) {
            return this.menu.getOutputOriginalEnchantLevelsClient();
        }
        return getItemEnchantmentLevels(input);
    }

    private long getPointCost(int currentLevel, int targetLevel) {
        if (targetLevel <= currentLevel) {
            return 0L;
        }
        long targetCost = MagicLibraryBlockEntity.getPointsForLevel(targetLevel);
        long currentCost = MagicLibraryBlockEntity.getPointsForLevel(currentLevel);
        return Math.max(0L, targetCost - currentCost);
    }

    private long getRawXPCost(int currentLevel, int targetLevel) {
        if (targetLevel <= currentLevel) {
            return 0L;
        }
        long targetCost = MagicLibraryBlockEntity.getXPForLevel(targetLevel);
        long currentCost = MagicLibraryBlockEntity.getXPForLevel(currentLevel);
        return Math.max(0L, targetCost - currentCost);
    }

    private long applyTier3Discount(long rawXP) {
        return ceilScaled(rawXP, 0.9D);
    }

    private boolean canPrepareEnchantingOnInput(ItemStack input) {
        if (isBookLike(input)) {
            return true;
        }
        return this.menu.getTier() == MagicLibraryTier.TIER3;
    }

    private boolean canApplyEnchantmentToInput(ItemStack input, Holder<Enchantment> enchantment, int currentLevel) {
        if (isBookLike(input)) {
            return true;
        }
        if (this.menu.getTier() != MagicLibraryTier.TIER3) {
            return false;
        }
        if (currentLevel > 0) {
            return true;
        }
        return enchantment.value().canEnchant(input);
    }

    private boolean isTier3GearEnchantingInput() {
        return isTier3GearEnchantingInput(getEnchantingContextInputClient());
    }

    private boolean isTier3GearEnchantingInput(ItemStack input) {
        return this.menu.getTier() == MagicLibraryTier.TIER3 && !isBookLike(input);
    }

    private ItemStack getEnchantingContextInputClient() {
        ItemStack context = this.menu.getEnchantingContextItemClient();
        if (!context.isEmpty()) {
            return context;
        }
        ItemStack extract = this.menu.getExtractItemClient();
        if (this.menu.getTier() == MagicLibraryTier.TIER3 && !extract.isEmpty() && !isBookLike(extract)) {
            return extract;
        }
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    private boolean isBookLike(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    private Holder.Reference<Enchantment> resolveEnchantment(Identifier enchantmentId) {
        if (this.minecraft.level == null) {
            return null;
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
        return this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
    }

    private static Map<String, SearchAliasTarget> createSearchAliasTargets() {
        Map<String, SearchAliasTarget> aliases = new HashMap<>();
        addSearchAliases(aliases, SearchAliasTarget.HEAD, "head", "helm", "helmet");
        addSearchAliases(aliases, SearchAliasTarget.CHEST, "chest", "chestplate");
        addSearchAliases(aliases, SearchAliasTarget.LEGS, "legs", "leggings");
        addSearchAliases(aliases, SearchAliasTarget.FEET, "feet", "boots");
        addSearchAliases(aliases, SearchAliasTarget.ARMOR, "armor");
        addSearchAliases(aliases, SearchAliasTarget.SWORD, "sword");
        addSearchAliases(aliases, SearchAliasTarget.AXE, "axe");
        addSearchAliases(aliases, SearchAliasTarget.PICKAXE, "pickaxe", "pick");
        addSearchAliases(aliases, SearchAliasTarget.SHOVEL, "shovel", "spade");
        addSearchAliases(aliases, SearchAliasTarget.HOE, "hoe");
        addSearchAliases(aliases, SearchAliasTarget.BOW, "bow");
        addSearchAliases(aliases, SearchAliasTarget.CROSSBOW, "crossbow");
        addSearchAliases(aliases, SearchAliasTarget.TRIDENT, "trident");
        addSearchAliases(aliases, SearchAliasTarget.FISHING_ROD, "rod", "fishing");
        addSearchAliases(aliases, SearchAliasTarget.TOOL, "tool");
        addSearchAliases(aliases, SearchAliasTarget.WEAPON, "weapon");
        addSearchAliases(aliases, SearchAliasTarget.BOOK, "book");
        return Map.copyOf(aliases);
    }

    private static void addSearchAliases(Map<String, SearchAliasTarget> aliases, SearchAliasTarget target, String... keys) {
        for (String key : keys) {
            aliases.put(key, target);
        }
    }

    private Component toLibraryFontText(String text) {
        if (!this.useLibraryFont) {
            return Component.literal(text);
        }
        return Component.literal(text).withStyle(style -> style.withFont(LIBRARY_FONT));
    }

    private Font createLibrarySearchFont() {
        try {
            Field providerField = Font.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            Font.Provider baseProvider = (Font.Provider) providerField.get(this.font);
            Font.Provider libraryProvider = new Font.Provider() {
                @Override
                public GlyphSource glyphs(FontDescription fontDescription) {
                    return baseProvider.glyphs(LIBRARY_FONT);
                }

                @Override
                public EffectGlyph effect() {
                    return baseProvider.effect();
                }
            };
            return new Font(libraryProvider);
        } catch (ReflectiveOperationException exception) {
            return this.font;
        }
    }

    private void alignSearchBoxTextVertically() {
        if (this.searchBox == null) {
            return;
        }

        Field textYField = getSearchBoxTextYField();
        if (textYField == null) {
            return;
        }

        float targetTextY = this.searchBox.getY() + (this.searchBox.getHeight() - (this.searchBoxFont.lineHeight * ENCHANT_TEXT_SCALE)) / 2.0F;
        int textY = Math.round(this.searchBox.getY() + ((targetTextY - this.searchBox.getY()) / ENCHANT_TEXT_SCALE));
        try {
            textYField.setInt(this.searchBox, textY);
        } catch (IllegalAccessException ignored) {
        }
    }

    @Nullable
    private static Field getSearchBoxTextYField() {
        if (!SEARCH_BOX_TEXT_Y_FIELD_LOOKED_UP) {
            SEARCH_BOX_TEXT_Y_FIELD_LOOKED_UP = true;
            try {
                SEARCH_BOX_TEXT_Y_FIELD = EditBox.class.getDeclaredField("textY");
                SEARCH_BOX_TEXT_Y_FIELD.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                SEARCH_BOX_TEXT_Y_FIELD = null;
            }
        }
        return SEARCH_BOX_TEXT_Y_FIELD;
    }

    private boolean isLibraryFontAvailable() {
        if (this.minecraft == null) {
            return false;
        }
        return this.minecraft.getResourceManager().getResource(LIBRARY_FONT_JSON).isPresent()
            && this.minecraft.getResourceManager().getResource(LIBRARY_FONT_TTF).isPresent();
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

    private static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String formatTenths(int tenths) {
        int whole = tenths / 10;
        int decimal = Math.abs(tenths % 10);
        if (decimal == 0) {
            return Integer.toString(whole);
        }
        return whole + "." + decimal;
    }

    private static String formatPercent(long current, long max) {
        if (max <= 0L || current <= 0L) {
            return "0%";
        }
        long percent = Math.clamp(Math.round((current * 100.0D) / max), 0L, 100L);
        return percent + "%";
    }

    private static String getTierPanelLabel(MagicLibraryTier tier) {
        return switch (tier) {
            case TIER1 -> "Apprentice Library";
            case TIER2 -> "Adept Library";
            case TIER3 -> "Archmage Library";
        };
    }
}
