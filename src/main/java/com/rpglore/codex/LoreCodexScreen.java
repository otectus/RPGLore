package com.rpglore.codex;

import com.rpglore.network.ModNetwork;
import com.rpglore.network.ServerboundCodexCopyBookPacket;
import com.rpglore.network.ServerboundCodexOpenBookPacket;
import com.rpglore.network.ServerboundCodexToggleDuplicatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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

    // Entry layout
    private static final int ENTRIES_PER_PAGE = 7;
    private static final int ENTRY_HEIGHT = 14;

    // Colors for parchment readability
    private static final int COLOR_TITLE = 0x3B2507;
    private static final int COLOR_TEXT = 0x4A3520;
    private static final int COLOR_SUBTLE = 0x7A6A58;
    private static final int COLOR_COLLECTED = 0x2D6B1A;
    private static final int COLOR_UNCOLLECTED = 0x8A7A68;
    private static final int COLOR_LINK = 0x1A4A6B;
    private static final int COLOR_LINK_HOVER = 0x0A2A4B;
    private static final int COLOR_COPY = 0x6B4A1A;
    private static final int COLOR_COPY_HOVER = 0x4B2A0A;

    private CodexScreenData data;
    private List<CodexBookEntry> filteredEntries;
    private int currentPage = 0;
    private int totalPages = 1;

    private Button toggleButton;

    // Computed layout positions (set in init)
    private int guiLeft, guiTop;
    private int textLeft, textTop;

    // Clickable entry regions for hit-testing
    private final List<EntryRegion> entryRegions = new ArrayList<>();

    // Page nav button regions (rendered manually from texture sprites)
    private int prevBtnX, prevBtnY, nextBtnX, nextBtnY;

    public LoreCodexScreen(CodexScreenData data) {
        super(Component.translatable("rpg_lore.codex.title"));
        this.data = data;
        this.filteredEntries = new ArrayList<>(data.catalog);
    }

    public void refreshData(CodexScreenData data) {
        this.data = data;
        applyFilter();
        if (toggleButton != null) {
            toggleButton.setMessage(getToggleLabel());
        }
    }

    @Override
    protected void init() {
        super.init();

        guiLeft = (this.width - BOOK_WIDTH) / 2;
        guiTop = (this.height - BOOK_HEIGHT) / 2;
        textLeft = guiLeft + PARCHMENT_X + TEXT_PADDING;
        textTop = guiTop + PARCHMENT_Y + TEXT_PADDING;

        // Toggle duplicate prevention button (small square, top-right of parchment)
        if (data.allowDuplicatePrevention) {
            int toggleSize = 12;
            int toggleX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH - toggleSize - TEXT_PADDING;
            int toggleY = textTop;
            toggleButton = Button.builder(getToggleLabel(), btn -> {
                ModNetwork.sendToServer(new ServerboundCodexToggleDuplicatePacket());
            }).bounds(toggleX, toggleY, toggleSize, toggleSize)
              .tooltip(Tooltip.create(
                      Component.literal("Pick-Up").withStyle(ChatFormatting.WHITE),
                      Component.literal("Enable/Disable Automatic Pick-Up")))
              .build();
            addRenderableWidget(toggleButton);
        }

        // Page nav button positions (drawn manually as texture sprites, hit-tested in mouseClicked)
        int navY = guiTop + PARCHMENT_Y + PARCHMENT_HEIGHT - BTN_SPRITE_H - 2;
        int parchCenterX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH / 2;
        prevBtnX = parchCenterX - BTN_SPRITE_W - 20;
        prevBtnY = navY;
        nextBtnX = parchCenterX + 20;
        nextBtnY = navY;

        applyFilter();
    }

    private void applyFilter() {
        filteredEntries = new ArrayList<>(data.catalog);
        totalPages = Math.max(1, (int) Math.ceil((double) filteredEntries.size() / ENTRIES_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;
    }

    private Component getToggleLabel() {
        return data.preventDuplicates
                ? Component.literal("\u2716").withStyle(ChatFormatting.DARK_RED)
                : Component.literal("\u2714").withStyle(ChatFormatting.DARK_GREEN);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Draw the codex book texture
        graphics.blit(CODEX_TEXTURE, guiLeft, guiTop, BOOK_U, BOOK_V, BOOK_WIDTH, BOOK_HEIGHT);

        int y = textTop;

        // Title
        Component title = Component.literal("Lore Codex").withStyle(
                Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(COLOR_TITLE)));
        int titleWidth = this.font.width(title);
        int parchCenterX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH / 2;
        graphics.drawString(this.font, title, parchCenterX - titleWidth / 2, y, COLOR_TITLE, false);
        y += 12;

        // Collection counter
        Component counter = Component.literal(data.collectedCount + " / " + data.totalCount)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_SUBTLE)));
        int counterWidth = this.font.width(counter);
        graphics.drawString(this.font, counter, parchCenterX - counterWidth / 2, y, COLOR_SUBTLE, false);
        y += 11;

        // Separator line
        int sepLeft = textLeft + 4;
        int sepRight = textLeft + TEXT_WIDTH - 4;
        graphics.fill(sepLeft, y, sepRight, y + 1, 0x40000000);
        y += 4;

        // Book entries
        entryRegions.clear();
        int startIdx = currentPage * ENTRIES_PER_PAGE;
        int endIdx = Math.min(startIdx + ENTRIES_PER_PAGE, filteredEntries.size());

        for (int i = startIdx; i < endIdx; i++) {
            CodexBookEntry entry = filteredEntries.get(i);
            renderEntry(graphics, textLeft, y, entry, mouseX, mouseY);
            y += ENTRY_HEIGHT;
        }

        // If no entries
        if (filteredEntries.isEmpty()) {
            Component empty = Component.literal("No books found")
                    .withStyle(Style.EMPTY.withItalic(true).withColor(TextColor.fromRgb(COLOR_SUBTLE)));
            int emptyW = this.font.width(empty);
            graphics.drawString(this.font, empty, parchCenterX - emptyW / 2, y + 20, COLOR_SUBTLE, false);
        }

        // Page navigation (drawn from texture sprites)
        renderPageNav(graphics, mouseX, mouseY);

        // Render vanilla widgets (toggle button) on top
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntry(GuiGraphics graphics, int x, int y,
                             CodexBookEntry entry, int mouseX, int mouseY) {
        // Status indicator
        if (entry.collected()) {
            graphics.drawString(this.font, "\u2022", x, y + 1, COLOR_COLLECTED, false);
        } else {
            graphics.drawString(this.font, "\u2022", x, y + 1, COLOR_UNCOLLECTED, false);
        }

        int titleX = x + 8;
        int maxTitleWidth = TEXT_WIDTH - 8;

        // Action buttons for collected books (positioned at right edge)
        if (entry.collected()) {
            // Copy label
            if (data.allowCopy) {
                String copyStr = "C";
                int copyW = this.font.width(copyStr) + 2;
                int copyX = x + TEXT_WIDTH - copyW;
                boolean hoverCopy = mouseX >= copyX && mouseX < copyX + copyW
                        && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
                graphics.drawString(this.font, copyStr, copyX, y + 1,
                        hoverCopy ? COLOR_COPY_HOVER : COLOR_COPY, false);
                entryRegions.add(new EntryRegion(copyX, y, copyX + copyW, y + ENTRY_HEIGHT,
                        entry.id(), EntryAction.COPY));
                maxTitleWidth -= copyW + 4;
            }

            // Read label
            String readStr = "Read";
            int readW = this.font.width(readStr);
            int readX = x + TEXT_WIDTH - readW - (data.allowCopy ? this.font.width("C") + 6 : 0);
            boolean hoverRead = mouseX >= readX && mouseX < readX + readW
                    && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            graphics.drawString(this.font, readStr, readX, y + 1,
                    hoverRead ? COLOR_LINK_HOVER : COLOR_LINK, false);
            entryRegions.add(new EntryRegion(readX, y, readX + readW, y + ENTRY_HEIGHT,
                    entry.id(), EntryAction.READ));
            maxTitleWidth -= readW + 6;
        }

        // Book title (truncated to fit)
        Component titleComp;
        if (entry.collected()) {
            int color = COLOR_TEXT;
            if (entry.titleColor() != null) {
                try {
                    color = Integer.parseInt(entry.titleColor(), 16);
                } catch (NumberFormatException ignored) {}
            }
            titleComp = Component.literal(entry.title())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        } else if (data.revealUncollectedNames) {
            titleComp = Component.literal(entry.title())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_UNCOLLECTED)));
        } else {
            titleComp = Component.literal("???")
                    .withStyle(Style.EMPTY.withItalic(true).withColor(TextColor.fromRgb(COLOR_UNCOLLECTED)));
        }

        // Truncate if needed
        FormattedCharSequence trimmed = this.font.split(titleComp, maxTitleWidth).stream()
                .findFirst().orElse(FormattedCharSequence.EMPTY);
        graphics.drawString(this.font, trimmed, titleX, y + 1, COLOR_TEXT, false);

        // Clickable title region for collected books
        if (entry.collected()) {
            int renderedWidth = Math.min(this.font.width(titleComp), maxTitleWidth);
            entryRegions.add(new EntryRegion(titleX, y, titleX + renderedWidth, y + ENTRY_HEIGHT,
                    entry.id(), EntryAction.READ));
        }
    }

    private void renderPageNav(GuiGraphics graphics, int mouseX, int mouseY) {
        int parchCenterX = guiLeft + PARCHMENT_X + PARCHMENT_WIDTH / 2;
        int navY = prevBtnY;

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check page nav sprite buttons
            if (currentPage > 0 && mouseX >= prevBtnX && mouseX < prevBtnX + BTN_SPRITE_W
                    && mouseY >= prevBtnY && mouseY < prevBtnY + BTN_SPRITE_H) {
                currentPage--;
                return true;
            }
            if (currentPage < totalPages - 1 && mouseX >= nextBtnX && mouseX < nextBtnX + BTN_SPRITE_W
                    && mouseY >= nextBtnY && mouseY < nextBtnY + BTN_SPRITE_H) {
                currentPage++;
                return true;
            }

            // Check entry action regions
            for (EntryRegion region : entryRegions) {
                if (mouseX >= region.x1 && mouseX < region.x2
                        && mouseY >= region.y1 && mouseY < region.y2) {
                    if (region.action == EntryAction.READ) {
                        ModNetwork.sendToServer(new ServerboundCodexOpenBookPacket(region.bookId));
                        return true;
                    } else if (region.action == EntryAction.COPY) {
                        ModNetwork.sendToServer(new ServerboundCodexCopyBookPacket(region.bookId));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Data types ---

    public record CodexBookEntry(
            String id,
            String title,
            String author,
            boolean collected,
            @Nullable String titleColor,
            @Nullable String category
    ) {}

    public record CodexScreenData(
            List<CodexBookEntry> catalog,
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

    private record EntryRegion(int x1, int y1, int x2, int y2, String bookId, EntryAction action) {}

    private enum EntryAction { READ, COPY }
}
