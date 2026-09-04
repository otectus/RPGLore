package com.rpglore.codex;

import com.rpglore.config.ClientConfig;
import com.rpglore.network.ModNetwork;
import com.rpglore.network.ServerboundCodexCopyBookPacket;
import com.rpglore.network.ServerboundCodexOpenBookPacket;
import com.rpglore.network.ServerboundCodexSetDuplicateModePacket;
import com.rpglore.network.ServerboundCodexSetFavoritePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class LoreCodexScreen extends Screen {

    private static final ResourceLocation CODEX_TEXTURE =
            new ResourceLocation("rpg_lore", "textures/gui/codex.png");

    // Book graphic region within the 256x256 texture atlas
    private static final int BOOK_U = 20;
    private static final int BOOK_V = 1;
    private static final int BOOK_WIDTH = 146;
    private static final int BOOK_HEIGHT = 180;

    // Parchment (text area) offset relative to book draw position
    private static final int PARCHMENT_X = 6;
    private static final int PARCHMENT_Y = 7;
    private static final int PARCHMENT_WIDTH = 132;
    private static final int PARCHMENT_HEIGHT = 165;

    // Text area with inner padding
    private static final int TEXT_PADDING = 6;
    private static final int TEXT_WIDTH = PARCHMENT_WIDTH - TEXT_PADDING * 2;

    // Page navigation button sprite UVs in the texture
    private static final int BTN_PREV_NORMAL_U = 3;
    private static final int BTN_PREV_NORMAL_V = 194;
    private static final int BTN_NEXT_NORMAL_U = 3;
    private static final int BTN_NEXT_NORMAL_V = 207;
    private static final int BTN_PREV_HOVER_U = 26;
    private static final int BTN_PREV_HOVER_V = 194;
    private static final int BTN_NEXT_HOVER_U = 26;
    private static final int BTN_NEXT_HOVER_V = 207;
    private static final int BTN_SPRITE_W = 18;
    private static final int BTN_SPRITE_H = 10;

    // Row indicator sprites, drawn into the free atlas space below the book art
    private static final int IND_V = 220;
    private static final int IND_SIZE = 8;
    private static final int IND_UNREAD_U = 0;
    private static final int IND_STAR_FULL_U = 10;
    private static final int IND_STAR_OUTLINE_U = 20;
    private static final int IND_COPY_U = 30;
    private static final int IND_COPY_GREY_U = 40;
    private static final int IND_OPEN_U = 50;
    private static final int IND_OPEN_HOVER_U = 60;

    // Entry layout. Eight rows is what fits below the search header.
    private static final int ENTRIES_PER_PAGE = 8;
    private static final int ENTRY_HEIGHT = 14;

    // Row columns: unread dot, favorite star, then the title
    private static final int COL_UNREAD = 0;
    private static final int COL_STAR = 9;
    private static final int COL_TITLE = 19;

    // Header rows relative to the top of the text area. Eight entry rows end at
    // textTop + 143; the page nav row sits at textTop + 147.
    private static final int ROW_TITLE = 0;
    private static final int ROW_SEARCH = 12;
    private static final int ROW_SEPARATOR = 27;
    private static final int ROW_ENTRIES = 31;

    // Duplicate-mode toggle icons (12x12, hover variant one row below)
    private static final int TOGGLE_STORE_U = 190;
    private static final int TOGGLE_GROUND_U = 203;
    private static final int TOGGLE_V = 1;
    private static final int TOGGLE_HOVER_DIFF = 12;
    private static final int TOGGLE_SIZE = 12;

    // Search magnifier glyph
    private static final int MAG_U = 220;
    private static final int MAG_V = 1;
    private static final int MAG_SIZE = 8;

    // Ribbon tabs sticking out of the book's right edge
    private static final int RIBBON_TUCK = 3;
    private static final int RIBBON_GAP = 2;
    private static final int RIBBON_TOP = 9;
    private static final int RIBBON_MAX_TEXT = 96;

    // Colors for parchment readability
    private static final int COLOR_TITLE = 0x3B2507;
    private static final int COLOR_TEXT = 0x4A3520;
    private static final int COLOR_SUBTLE = 0x7A6A58;
    private static final int COLOR_UNCOLLECTED = 0x8A7A68;
    private static final int COLOR_LINK = 0x1A4A6B;
    private static final int COLOR_LINK_HOVER = 0x0A2A4B;
    private static final int COLOR_COPY = 0x6B4A1A;
    private static final int COLOR_SELECTION = 0x28000000;

    /** Category cycle value standing for "every category". */
    private static final String CATEGORY_ALL = "";

    private CodexScreenData data;
    private List<CodexEntryView> filteredEntries;
    private CodexViewState viewState;
    private int totalPages = 1;

    private ImageButton toggleButton;
    private EditBox searchBox;

    // Width of the search box, kept for the underline drawn in render()
    private int searchWidth;

    // Ribbons render under the book's right border, so they are not renderable widgets
    private final List<RibbonButton<?>> ribbons = new ArrayList<>();

    // Computed layout positions (set in init)
    private int guiLeft, guiTop;
    private int textLeft, textTop;

    // Clickable entry regions for hit-testing
    private final List<EntryRegion> entryRegions = new ArrayList<>();

    // One record per rendered row, for hover tooltips and keyboard selection
    private final List<RowLayout> rowLayouts = new ArrayList<>();

    // Page nav button regions (rendered manually from texture sprites)
    private int prevBtnX, prevBtnY, nextBtnX, nextBtnY;

    // Built once instead of per frame — render() runs every tick
    private final Component styledTitle;
    private final Component uncollectedTitle;

    public LoreCodexScreen(CodexScreenData data) {
        this(data, CodexViewState.DEFAULT);
    }

    public LoreCodexScreen(CodexScreenData data, CodexViewState viewState) {
        super(Component.translatable("rpg_lore.codex.title"));
        this.data = data;
        this.viewState = viewState;
        this.filteredEntries = new ArrayList<>(data.catalog);
        this.styledTitle = Component.translatable("rpg_lore.codex.title").withStyle(
                Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(COLOR_TITLE)));
        this.uncollectedTitle = Component.translatable("rpg_lore.codex.uncollected")
                .withStyle(Style.EMPTY.withItalic(true).withColor(TextColor.fromRgb(COLOR_UNCOLLECTED)));
    }

    public void refreshData(CodexScreenData data) {
        this.data = data;
        if (this.minecraft != null) {
            // Rebuild all widgets so the toggle button appears/disappears (and its
            // label updates) per the new data, and so the category cycle picks up
            // categories from a catalog that may have changed. The first sync of a
            // session arrives AFTER the screen opened with empty data.
            this.rebuildWidgets();
        } else {
            // Not initialized yet; init() will build widgets from the new data
            applyFilter();
        }
    }

    @Override
    protected void init() {
        super.init();

        ribbons.clear();

        guiLeft = (this.width - BOOK_WIDTH) / 2;
        guiTop = (this.height - BOOK_HEIGHT) / 2;
        textLeft = guiLeft + PARCHMENT_X + TEXT_PADDING;
        textTop = guiTop + PARCHMENT_Y + TEXT_PADDING;

        // The magnifier glyph occupies the first 10px of the search row
        searchWidth = (data.allowDuplicatePrevention ? TEXT_WIDTH - TOGGLE_SIZE - 2 : TEXT_WIDTH) - 10;

        // Search box: unbordered so it sits on the parchment instead of on a vanilla frame
        searchBox = new EditBox(this.font, textLeft + 10, textTop + ROW_SEARCH, searchWidth, 12,
                Component.translatable("rpg_lore.codex.search.hint"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(COLOR_TEXT);
        searchBox.setHint(Component.translatable("rpg_lore.codex.search.hint")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_SUBTLE))));
        searchBox.setValue(viewState.query());
        searchBox.setResponder(value -> {
            if (!value.equals(viewState.query())) {
                viewState = viewState.withQuery(value);
                applyFilter();
            }
        });
        addRenderableWidget(searchBox);

        // Toggle duplicate prevention button (small square, right of the search box)
        if (data.allowDuplicatePrevention) {
            int toggleX = textLeft + TEXT_WIDTH - TOGGLE_SIZE;
            toggleButton = new ImageButton(toggleX, textTop + ROW_SEARCH, TOGGLE_SIZE, TOGGLE_SIZE,
                    data.preventDuplicates ? TOGGLE_GROUND_U : TOGGLE_STORE_U, TOGGLE_V,
                    TOGGLE_HOVER_DIFF, CODEX_TEXTURE, 256, 256,
                    // Idempotent set: ask for the opposite of the mode we are showing
                    btn -> ModNetwork.sendToServer(
                            new ServerboundCodexSetDuplicateModePacket(data.preventDuplicates)));
            toggleButton.setTooltip(Tooltip.create(duplicatesTooltip()));
            addRenderableWidget(toggleButton);
        }

        buildRibbons();

        // Page nav button positions (drawn manually as texture sprites, hit-tested in mouseClicked)
        int navY = guiTop + PARCHMENT_Y + PARCHMENT_HEIGHT - BTN_SPRITE_H - 2;
        int parchCenterX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH / 2;
        prevBtnX = parchCenterX - BTN_SPRITE_W - 20;
        prevBtnY = navY;
        nextBtnX = parchCenterX + 20;
        nextBtnY = navY;

        applyFilter();
    }

    // --- Header widgets ---

    /**
     * Builds the three ribbon tabs. Their root is tucked under the book's right
     * border, so they are registered with addWidget and rendered by hand before
     * the book blit rather than by super.render().
     */
    private void buildRibbons() {
        int x = guiLeft + BOOK_WIDTH - RIBBON_TUCK;
        // Whatever the ribbon can spend on text without running off the screen edge
        int maxTextWidth = Math.max(20, Math.min(RIBBON_MAX_TEXT, this.width - x - 4 - 3 - 3 - 6 - 2));

        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        categories.addAll(CodexEntryFilter.categoriesOf(data.catalog));
        if (CodexEntryFilter.hasUncategorized(data.catalog)) {
            categories.add(CodexViewState.UNCATEGORIZED);
        }

        String initialCategory = viewState.category() == null ? CATEGORY_ALL : viewState.category();
        if (!categories.contains(initialCategory)) {
            // The category vanished from the catalog; fall back to showing everything
            initialCategory = CATEGORY_ALL;
            viewState = viewState.withCategory(null);
        }

        addRibbon(new RibbonButton<String>(this.font, x, ribbonY(0), maxTextWidth, categories, initialCategory,
                "rpg_lore.codex.ribbon.category", LoreCodexScreen::ribbonCategoryLabel,
                (btn, value) -> {
                    viewState = viewState.withCategory(CATEGORY_ALL.equals(value) ? null : value);
                    applyFilter();
                }));

        addRibbon(new RibbonButton<CodexViewState.Filter>(this.font, x, ribbonY(1), maxTextWidth,
                List.of(CodexViewState.Filter.values()), viewState.filter(),
                "rpg_lore.codex.ribbon.filter", LoreCodexScreen::filterLabel,
                (btn, value) -> {
                    viewState = viewState.withFilter(value);
                    applyFilter();
                }));

        addRibbon(new RibbonButton<CodexViewState.Sort>(this.font, x, ribbonY(2), maxTextWidth,
                List.of(CodexViewState.Sort.values()), viewState.sort(),
                "rpg_lore.codex.ribbon.sort", LoreCodexScreen::sortLabel,
                (btn, value) -> {
                    viewState = viewState.withSort(value);
                    applyFilter();
                }));
    }

    private int ribbonY(int slot) {
        return guiTop + RIBBON_TOP + slot * (RibbonButton.HEIGHT + RIBBON_GAP);
    }

    private void addRibbon(RibbonButton<?> ribbon) {
        addWidget(ribbon);
        ribbons.add(ribbon);
    }

    /** The ribbon spells out the role itself, so "all" reads as a plain value here. */
    private static Component ribbonCategoryLabel(String value) {
        if (CATEGORY_ALL.equals(value)) return Component.translatable("rpg_lore.codex.filter.all");
        return categoryLabel(value);
    }

    private static Component categoryLabel(String value) {
        if (CATEGORY_ALL.equals(value)) return Component.translatable("rpg_lore.codex.category.all");
        if (CodexViewState.UNCATEGORIZED.equals(value)) {
            return Component.translatable("rpg_lore.codex.category.uncategorized");
        }
        return Component.literal(value);
    }

    private static Component filterLabel(CodexViewState.Filter filter) {
        return Component.translatable("rpg_lore.codex.filter." + filter.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component sortLabel(CodexViewState.Sort sort) {
        return Component.translatable("rpg_lore.codex.sort." + sort.name().toLowerCase(java.util.Locale.ROOT));
    }

    private Component duplicatesTooltip() {
        Component mode = data.preventDuplicates
                ? Component.translatable("rpg_lore.codex.duplicates.ground")
                : Component.translatable("rpg_lore.codex.duplicates.store");
        return Component.translatable("rpg_lore.codex.duplicates.label")
                .withStyle(ChatFormatting.WHITE)
                .append(": ")
                .append(mode)
                .append("\n")
                .append(Component.translatable("rpg_lore.codex.duplicates.tooltip.store"))
                .append("\n")
                .append(Component.translatable("rpg_lore.codex.duplicates.tooltip.ground"));
    }

    // --- List pipeline ---

    private void applyFilter() {
        filteredEntries = CodexEntryFilter.apply(data.catalog, viewState);
        totalPages = Math.max(1, (int) Math.ceil((double) filteredEntries.size() / ENTRIES_PER_PAGE));

        // Keep the keyboard selection visible when the list is rebuilt under it
        String selectedId = viewState.selectedId();
        int page = Math.min(Math.max(viewState.page(), 0), totalPages - 1);
        if (selectedId != null) {
            int index = indexOf(selectedId);
            if (index >= 0) {
                page = index / ENTRIES_PER_PAGE;
            } else {
                selectedId = null;
            }
        }
        viewState = viewState.withPage(page).withSelectedId(selectedId);
    }

    private int indexOf(String bookId) {
        for (int i = 0; i < filteredEntries.size(); i++) {
            if (filteredEntries.get(i).id().equals(bookId)) return i;
        }
        return -1;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Ribbons first: the book blit then covers their tucked-in root
        for (RibbonButton<?> ribbon : ribbons) {
            ribbon.render(graphics, mouseX, mouseY, partialTick);
        }

        // Draw the codex book texture
        graphics.blit(CODEX_TEXTURE, guiLeft, guiTop, BOOK_U, BOOK_V, BOOK_WIDTH, BOOK_HEIGHT);

        int parchCenterX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH / 2;

        // Search row furniture: magnifier glyph and a hairline underline
        graphics.blit(CODEX_TEXTURE, textLeft, textTop + ROW_SEARCH + 2, MAG_U, MAG_V, MAG_SIZE, MAG_SIZE);
        graphics.fill(textLeft, textTop + ROW_SEARCH + 11,
                textLeft + 10 + searchWidth, textTop + ROW_SEARCH + 12, 0x50000000);

        // Title
        int titleWidth = this.font.width(styledTitle);
        graphics.drawString(this.font, styledTitle, parchCenterX - titleWidth / 2,
                textTop + ROW_TITLE, COLOR_TITLE, false);

        // Separator line
        int sepY = textTop + ROW_SEPARATOR;
        graphics.fill(textLeft + 4, sepY, textLeft + TEXT_WIDTH - 4, sepY + 1, 0x40000000);

        // Book entries
        entryRegions.clear();
        rowLayouts.clear();
        int y = textTop + ROW_ENTRIES;
        int startIdx = viewState.page() * ENTRIES_PER_PAGE;
        int endIdx = Math.min(startIdx + ENTRIES_PER_PAGE, filteredEntries.size());

        for (int i = startIdx; i < endIdx; i++) {
            CodexEntryView entry = filteredEntries.get(i);
            renderEntry(graphics, textLeft, y, entry, mouseX, mouseY);
            rowLayouts.add(new RowLayout(y, entry));
            y += ENTRY_HEIGHT;
        }

        // If no entries
        if (filteredEntries.isEmpty()) {
            Component empty = Component.translatable("rpg_lore.codex.no_results")
                    .withStyle(Style.EMPTY.withItalic(true).withColor(TextColor.fromRgb(COLOR_SUBTLE)));
            int emptyW = this.font.width(empty);
            graphics.drawString(this.font, empty, parchCenterX - emptyW / 2, y + 20, COLOR_SUBTLE, false);
        }

        // Page navigation (drawn from texture sprites)
        renderPageNav(graphics, mouseX, mouseY);

        // Render vanilla widgets (search, toggle) on top
        super.render(graphics, mouseX, mouseY, partialTick);

        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderEntry(GuiGraphics graphics, int x, int y,
                             CodexEntryView entry, int mouseX, int mouseY) {
        if (entry.id().equals(viewState.selectedId())) {
            graphics.fill(x - 2, y - 1, x + TEXT_WIDTH + 2, y + ENTRY_HEIGHT - 3, COLOR_SELECTION);
        }

        int indicatorY = y + 2;

        // Unread dot: shape as well as color, so it survives a color-blind palette
        if (entry.collected() && !entry.read() && ClientConfig.CODEX_SHOW_UNREAD_MARKERS.get()) {
            blitIndicator(graphics, IND_UNREAD_U, x + COL_UNREAD, indicatorY);
            entryRegions.add(indicatorRegion(x + COL_UNREAD, indicatorY, entry.id(), EntryAction.NONE));
        }

        // Favorite star (collected rows only)
        if (entry.collected()) {
            blitIndicator(graphics, entry.favorite() ? IND_STAR_FULL_U : IND_STAR_OUTLINE_U,
                    x + COL_STAR, indicatorY);
            entryRegions.add(indicatorRegion(x + COL_STAR, indicatorY, entry.id(), EntryAction.FAVORITE));
        }

        int titleX = x + COL_TITLE;
        // Right-hand icon column: open-book at the far edge, copy icon 3px to its left
        int iconsLeft = x + TEXT_WIDTH;

        if (entry.collected()) {
            // Open-book icon, right-aligned
            int openX = x + TEXT_WIDTH - IND_SIZE;
            boolean hoverOpen = mouseX >= openX && mouseX < openX + IND_SIZE
                    && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            blitIndicator(graphics, hoverOpen ? IND_OPEN_HOVER_U : IND_OPEN_U, openX, indicatorY);
            entryRegions.add(new EntryRegion(openX, y, openX + IND_SIZE, y + ENTRY_HEIGHT,
                    entry.id(), EntryAction.OPEN));
            iconsLeft = openX;

            // Spare copies: icon only, greyed when the bank is empty. The count
            // lives in the hover tooltip.
            if (data.allowCopy) {
                boolean hasSpares = entry.spares() > 0;
                int copyX = copyIconX(x);
                blitIndicator(graphics, hasSpares ? IND_COPY_U : IND_COPY_GREY_U, copyX, indicatorY);
                entryRegions.add(new EntryRegion(copyX, y, copyX + IND_SIZE, y + ENTRY_HEIGHT,
                        entry.id(), hasSpares ? EntryAction.COPY : EntryAction.NONE));
                iconsLeft = copyX;
            }
        }

        int maxTitleWidth = iconsLeft - 4 - titleX;

        // Book title (ellipsised to fit; the row tooltip carries the full text)
        if (entry.collected() || (data.revealUncollectedNames && !entry.title().isEmpty())) {
            int color = COLOR_UNCOLLECTED;
            if (entry.collected()) {
                color = COLOR_TEXT;
                if (entry.titleColor() != null) {
                    try {
                        color = Integer.parseInt(entry.titleColor(), 16);
                    } catch (NumberFormatException ignored) {}
                }
            }
            String titleText = entry.title();
            if (this.font.width(titleText) > maxTitleWidth) {
                titleText = this.font.plainSubstrByWidth(titleText,
                        maxTitleWidth - this.font.width("...")).stripTrailing() + "...";
            }
            graphics.drawString(this.font, titleText, titleX, y + 1, color, false);

            // Clickable title region for collected books
            if (entry.collected()) {
                entryRegions.add(new EntryRegion(titleX, y, titleX + this.font.width(titleText),
                        y + ENTRY_HEIGHT, entry.id(), EntryAction.READ));
            }
        } else {
            graphics.drawString(this.font, uncollectedTitle, titleX, y + 1, COLOR_UNCOLLECTED, false);
        }
    }

    /** Left edge of the copy icon column, three pixels left of the open-book icon. */
    private int copyIconX(int x) {
        return x + TEXT_WIDTH - IND_SIZE - 3 - IND_SIZE;
    }

    private void blitIndicator(GuiGraphics graphics, int u, int x, int y) {
        graphics.blit(CODEX_TEXTURE, x, y, u, IND_V, IND_SIZE, IND_SIZE);
    }

    private EntryRegion indicatorRegion(int x, int y, String bookId, EntryAction action) {
        return new EntryRegion(x, y, x + IND_SIZE, y + IND_SIZE, bookId, action);
    }

    private void renderPageNav(GuiGraphics graphics, int mouseX, int mouseY) {
        int parchCenterX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH / 2;
        int navY = prevBtnY;
        int currentPage = viewState.page();

        boolean canPrev = currentPage > 0;
        boolean canNext = currentPage < totalPages - 1;

        // Previous page button (texture sprite)
        if (canPrev) {
            boolean hoverPrev = mouseX >= prevBtnX && mouseX < prevBtnX + BTN_SPRITE_W
                    && mouseY >= prevBtnY && mouseY < prevBtnY + BTN_SPRITE_H;
            int u = hoverPrev ? BTN_PREV_HOVER_U : BTN_PREV_NORMAL_U;
            int v = hoverPrev ? BTN_PREV_HOVER_V : BTN_PREV_NORMAL_V;
            graphics.blit(CODEX_TEXTURE, prevBtnX, prevBtnY, u, v, BTN_SPRITE_W, BTN_SPRITE_H);
        }

        // Next page button (texture sprite)
        if (canNext) {
            boolean hoverNext = mouseX >= nextBtnX && mouseX < nextBtnX + BTN_SPRITE_W
                    && mouseY >= nextBtnY && mouseY < nextBtnY + BTN_SPRITE_H;
            int u = hoverNext ? BTN_NEXT_HOVER_U : BTN_NEXT_NORMAL_U;
            int v = hoverNext ? BTN_NEXT_HOVER_V : BTN_NEXT_NORMAL_V;
            graphics.blit(CODEX_TEXTURE, nextBtnX, nextBtnY, u, v, BTN_SPRITE_W, BTN_SPRITE_H);
        }

        // Page indicator between buttons
        Component pageLabel = Component.literal((currentPage + 1) + "/" + totalPages)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_SUBTLE)));
        int labelW = this.font.width(pageLabel);
        graphics.drawString(this.font, pageLabel, parchCenterX - labelW / 2, navY, COLOR_SUBTLE, false);
    }

    // --- Tooltips ---

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Indicator tooltips win over the row tooltip they sit inside
        for (EntryRegion region : entryRegions) {
            if (!region.contains(mouseX, mouseY)) continue;
            CodexEntryView entry = find(region.bookId);
            if (entry == null) continue;
            if (region.action == EntryAction.FAVORITE) {
                graphics.renderTooltip(this.font, favoriteTooltip(entry), mouseX, mouseY);
                return;
            }
            if (region.action == EntryAction.COPY || isSpareRegion(region, entry)) {
                graphics.renderTooltip(this.font, sparesTooltip(entry), mouseX, mouseY);
                return;
            }
            if (region.action == EntryAction.OPEN) {
                graphics.renderTooltip(this.font,
                        Component.translatable("rpg_lore.codex.open_book"), mouseX, mouseY);
                return;
            }
        }

        for (RowLayout row : rowLayouts) {
            if (mouseX >= textLeft && mouseX < textLeft + TEXT_WIDTH
                    && mouseY >= row.y && mouseY < row.y + ENTRY_HEIGHT) {
                graphics.renderComponentTooltip(this.font, entryTooltip(row.entry), mouseX, mouseY);
                return;
            }
        }
    }

    /** A greyed spare indicator has action NONE but still explains itself on hover. */
    private boolean isSpareRegion(EntryRegion region, CodexEntryView entry) {
        return region.action == EntryAction.NONE && data.allowCopy && entry.collected()
                && region.x1 == copyIconX(textLeft);
    }

    private List<Component> entryTooltip(CodexEntryView entry) {
        List<Component> lines = new ArrayList<>();
        if (entry.collected()) {
            lines.add(Component.literal(entry.title()).withStyle(ChatFormatting.WHITE));
            if (!entry.author().isEmpty()) {
                lines.add(Component.translatable("rpg_lore.codex.tooltip.author", entry.author())
                        .withStyle(ChatFormatting.GRAY));
            }
            addCategoryLine(lines, entry);
            addSeriesLine(lines, entry);
            lines.add(Component.translatable(entry.read()
                    ? "rpg_lore.codex.tooltip.read" : "rpg_lore.codex.tooltip.unread")
                    .withStyle(ChatFormatting.GRAY));
            if (data.allowCopy) {
                lines.add(sparesTooltip(entry).copy().withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(entry.favorite()
                    ? "rpg_lore.codex.tooltip.favorite" : "rpg_lore.codex.tooltip.not_favorite")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            if (data.revealUncollectedNames && !entry.title().isEmpty()) {
                lines.add(Component.literal(entry.title()).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("rpg_lore.codex.tooltip.hidden")
                        .withStyle(ChatFormatting.GRAY));
            }
            addCategoryLine(lines, entry);
            if (entry.discoveryHint() != null) {
                lines.add(Component.translatable("rpg_lore.codex.tooltip.discovery_hint", entry.discoveryHint())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    private void addCategoryLine(List<Component> lines, CodexEntryView entry) {
        String category = entry.category();
        if (category != null && !category.isBlank()) {
            lines.add(Component.translatable("rpg_lore.codex.tooltip.category", category)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void addSeriesLine(List<Component> lines, CodexEntryView entry) {
        String series = entry.series();
        if (series != null && !series.isBlank()) {
            lines.add(Component.translatable("rpg_lore.codex.tooltip.series", series, entry.seriesOrder())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private Component favoriteTooltip(CodexEntryView entry) {
        return Component.translatable(entry.favorite()
                ? "rpg_lore.codex.favorite.remove" : "rpg_lore.codex.favorite.add");
    }

    private Component sparesTooltip(CodexEntryView entry) {
        return entry.spares() > 0
                ? Component.translatable("rpg_lore.codex.spares", String.valueOf(entry.spares()))
                : Component.translatable("rpg_lore.codex.spares.none");
    }

    @Nullable
    private CodexEntryView find(String bookId) {
        for (CodexEntryView entry : filteredEntries) {
            if (entry.id().equals(bookId)) return entry;
        }
        return null;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check page nav sprite buttons
            if (viewState.page() > 0 && mouseX >= prevBtnX && mouseX < prevBtnX + BTN_SPRITE_W
                    && mouseY >= prevBtnY && mouseY < prevBtnY + BTN_SPRITE_H) {
                viewState = viewState.withPage(viewState.page() - 1);
                return true;
            }
            if (viewState.page() < totalPages - 1 && mouseX >= nextBtnX && mouseX < nextBtnX + BTN_SPRITE_W
                    && mouseY >= nextBtnY && mouseY < nextBtnY + BTN_SPRITE_H) {
                viewState = viewState.withPage(viewState.page() + 1);
                return true;
            }

            // Check entry action regions
            for (EntryRegion region : entryRegions) {
                if (!region.contains((int) mouseX, (int) mouseY)) continue;
                switch (region.action) {
                    case READ, OPEN -> {
                        openBook(region.bookId);
                        return true;
                    }
                    case COPY -> {
                        ModNetwork.sendToServer(new ServerboundCodexCopyBookPacket(region.bookId));
                        return true;
                    }
                    case FAVORITE -> {
                        CodexEntryView entry = find(region.bookId);
                        if (entry != null) {
                            ModNetwork.sendToServer(new ServerboundCodexSetFavoritePacket(
                                    region.bookId, !entry.favorite()));
                        }
                        return true;
                    }
                    // NONE: a greyed indicator, present only for its tooltip
                    case NONE -> {
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean searchFocused = searchBox != null && searchBox.isFocused();

        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            if (searchBox != null) {
                this.setFocused(searchBox);
                searchBox.setFocused(true);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused) {
                searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            this.onClose();
            return true;
        }

        // A focused ribbon must get Enter/Space itself, ahead of "open the selected book"
        if (this.getFocused() instanceof AbstractButton
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                        || keyCode == GLFW.GLFW_KEY_SPACE)
                && super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (!searchFocused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> {
                    if (viewState.page() > 0) viewState = viewState.withPage(viewState.page() - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (viewState.page() < totalPages - 1) {
                        viewState = viewState.withPage(viewState.page() + 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    moveSelection(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    moveSelection(1);
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    String selectedId = viewState.selectedId();
                    CodexEntryView entry = selectedId == null ? null : find(selectedId);
                    if (entry != null && entry.collected()) {
                        openBook(entry.id());
                        return true;
                    }
                }
                default -> { }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Moves the keyboard selection within the rows currently on screen. */
    private void moveSelection(int delta) {
        int startIdx = viewState.page() * ENTRIES_PER_PAGE;
        int endIdx = Math.min(startIdx + ENTRIES_PER_PAGE, filteredEntries.size());
        if (startIdx >= endIdx) return;

        int index = viewState.selectedId() == null ? -1 : indexOf(viewState.selectedId());
        if (index < startIdx || index >= endIdx) {
            index = delta > 0 ? startIdx : endIdx - 1;
        } else {
            index = Math.min(Math.max(index + delta, startIdx), endIdx - 1);
        }
        viewState = viewState.withSelectedId(filteredEntries.get(index).id());
    }

    private void openBook(String bookId) {
        // Save first: the book screen returns here, and the Codex is rebuilt on the way back
        LoreCodexClientHelper.saveViewState(viewState);
        ModNetwork.sendToServer(new ServerboundCodexOpenBookPacket(bookId));
    }

    @Override
    public void onClose() {
        LoreCodexClientHelper.saveViewState(viewState);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Data types ---

    public record CodexScreenData(
            List<CodexEntryView> catalog,
            boolean preventDuplicates,
            int collectedCount,
            int totalCount,
            boolean allowCopy,
            boolean allowDuplicatePrevention,
            boolean revealUncollectedNames
    ) {
        public static CodexScreenData empty() {
            return new CodexScreenData(List.of(), false, 0, 0, false, false, false);
        }
    }

    private record EntryRegion(int x1, int y1, int x2, int y2, String bookId, EntryAction action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private record RowLayout(int y, CodexEntryView entry) {}

    private enum EntryAction { READ, OPEN, COPY, FAVORITE, NONE }
}
