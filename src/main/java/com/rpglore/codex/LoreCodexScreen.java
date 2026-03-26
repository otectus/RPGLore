package com.rpglore.codex;

import com.rpglore.network.ModNetwork;
import com.rpglore.network.ServerboundCodexCopyBookPacket;
import com.rpglore.network.ServerboundCodexOpenBookPacket;
import com.rpglore.network.ServerboundCodexToggleDuplicatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class LoreCodexScreen extends Screen {

    private static final ResourceLocation CODEX_TEXTURE =
            new ResourceLocation("rpg_lore", "textures/gui/codex.png");

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 200;
    private static final int ENTRIES_PER_PAGE = 8;
    private static final int ENTRY_HEIGHT = 16;
    private static final int LIST_START_Y = 48;
    private static final int LIST_X_OFFSET = 20;
    private static final int LIST_WIDTH = 216;

    private CodexScreenData data;
    private List<CodexBookEntry> filteredEntries;
    private int currentPage = 0;
    private int totalPages = 1;
    private String searchFilter = "";

    private Button prevPageButton;
    private Button nextPageButton;
    private Button toggleButton;
    private EditBox searchBox;

    // Clickable entry regions for hit-testing
    private final List<EntryRegion> entryRegions = new ArrayList<>();

    public LoreCodexScreen(CodexScreenData data) {
        super(Component.translatable("rpg_lore.codex.title"));
        this.data = data;
        this.filteredEntries = new ArrayList<>(data.catalog);
    }

    public void refreshData(CodexScreenData data) {
        this.data = data;
        applyFilter();
        updateButtons();
    }

    @Override
    protected void init() {
        super.init();
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // Search box
        searchBox = new EditBox(this.font, guiLeft + LIST_X_OFFSET, guiTop + 32, LIST_WIDTH - 60, 12,
                Component.literal("Search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setResponder(text -> {
            searchFilter = text.toLowerCase();
            currentPage = 0;
            applyFilter();
        });
        addWidget(searchBox);

        // Toggle duplicate prevention button
        if (data.allowDuplicatePrevention) {
            toggleButton = Button.builder(getToggleLabel(), btn -> {
                ModNetwork.sendToServer(new ServerboundCodexToggleDuplicatePacket());
            }).bounds(guiLeft + GUI_WIDTH - 68, guiTop + 4, 60, 14).build();
            addRenderableWidget(toggleButton);
        }

        // Page navigation
        prevPageButton = Button.builder(Component.literal("<"), btn -> {
            if (currentPage > 0) {
                currentPage--;
                updateButtons();
            }
        }).bounds(guiLeft + GUI_WIDTH / 2 - 50, guiTop + GUI_HEIGHT - 20, 20, 16).build();
        addRenderableWidget(prevPageButton);

        nextPageButton = Button.builder(Component.literal(">"), btn -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateButtons();
            }
        }).bounds(guiLeft + GUI_WIDTH / 2 + 30, guiTop + GUI_HEIGHT - 20, 20, 16).build();
        addRenderableWidget(nextPageButton);

        applyFilter();
        updateButtons();
    }

    private void applyFilter() {
        filteredEntries = new ArrayList<>();
        for (CodexBookEntry entry : data.catalog) {
            if (searchFilter.isEmpty()) {
                filteredEntries.add(entry);
            } else {
                String displayName = entry.collected() || data.revealUncollectedNames
                        ? entry.title().toLowerCase() : "???";
                if (displayName.contains(searchFilter) ||
                        (entry.category() != null && entry.category().toLowerCase().contains(searchFilter))) {
                    filteredEntries.add(entry);
                }
            }
        }
        totalPages = Math.max(1, (int) Math.ceil((double) filteredEntries.size() / ENTRIES_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;
    }

    private void updateButtons() {
        prevPageButton.active = currentPage > 0;
        nextPageButton.active = currentPage < totalPages - 1;
        if (toggleButton != null) {
            toggleButton.setMessage(getToggleLabel());
        }
    }

    private Component getToggleLabel() {
        return data.preventDuplicates
                ? Component.literal("Unlocked").withStyle(ChatFormatting.RED)
                : Component.literal("Locked").withStyle(ChatFormatting.GREEN);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // Background
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xCC1A0A2E);
        // Border
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 1, 0xFF6B3FA0);
        graphics.fill(guiLeft, guiTop + GUI_HEIGHT - 1, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF6B3FA0);
        graphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + GUI_HEIGHT, 0xFF6B3FA0);
        graphics.fill(guiLeft + GUI_WIDTH - 1, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF6B3FA0);

        // Title
        Component title = Component.literal("LORE CODEX").withStyle(
                Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0xD4AF37)));
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, guiLeft + (GUI_WIDTH - titleWidth) / 2, guiTop + 6, 0xD4AF37, true);

        // Collection counter
        Component counter = Component.literal("Collected: " + data.collectedCount + " / " + data.totalCount)
                .withStyle(ChatFormatting.GRAY);
        graphics.drawString(this.font, counter, guiLeft + LIST_X_OFFSET, guiTop + 20, 0xAAAAAA, false);

        // Duplicate prevention indicator
        if (data.preventDuplicates) {
            Component indicator = Component.literal("Blocking duplicates")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC);
            int indicatorWidth = this.font.width(indicator);
            graphics.drawString(this.font, indicator, guiLeft + GUI_WIDTH - indicatorWidth - 8, guiTop + 20,
                    0x55AAAA, false);
        }

        // Search box
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // Book entries
        entryRegions.clear();
        int startIdx = currentPage * ENTRIES_PER_PAGE;
        int endIdx = Math.min(startIdx + ENTRIES_PER_PAGE, filteredEntries.size());

        for (int i = startIdx; i < endIdx; i++) {
            CodexBookEntry entry = filteredEntries.get(i);
            int entryY = guiTop + LIST_START_Y + (i - startIdx) * ENTRY_HEIGHT;

            renderEntry(graphics, guiLeft, entryY, entry, mouseX, mouseY);
        }

        // Page indicator
        Component pageIndicator = Component.literal("Page " + (currentPage + 1) + " / " + totalPages)
                .withStyle(ChatFormatting.GRAY);
        int pageWidth = this.font.width(pageIndicator);
        graphics.drawString(this.font, pageIndicator,
                guiLeft + (GUI_WIDTH - pageWidth) / 2, guiTop + GUI_HEIGHT - 18, 0xAAAAAA, false);

        // Render widgets (buttons)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntry(GuiGraphics graphics, int guiLeft, int entryY,
                             CodexBookEntry entry, int mouseX, int mouseY) {
        int x = guiLeft + LIST_X_OFFSET;

        // Status indicator
        if (entry.collected()) {
            graphics.drawString(this.font, "\u2713", x, entryY + 2, 0x55FF55, false); // checkmark
        } else {
            graphics.drawString(this.font, "?", x + 1, entryY + 2, 0x888888, false);
        }

        // Book title
        int titleX = x + 14;
        Component titleComp;
        if (entry.collected()) {
            Style titleStyle = Style.EMPTY;
            if (entry.titleColor() != null) {
                try {
                    titleStyle = titleStyle.withColor(TextColor.fromRgb(Integer.parseInt(entry.titleColor(), 16)));
                } catch (NumberFormatException ignored) {
                    titleStyle = titleStyle.withColor(ChatFormatting.YELLOW);
                }
            } else {
                titleStyle = titleStyle.withColor(ChatFormatting.YELLOW);
            }
            titleComp = Component.literal(entry.title()).withStyle(titleStyle);
        } else if (data.revealUncollectedNames) {
            titleComp = Component.literal(entry.title()).withStyle(ChatFormatting.DARK_GRAY);
        } else {
            titleComp = Component.literal("???").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }

        int titleWidth = this.font.width(titleComp);
        graphics.drawString(this.font, titleComp, titleX, entryY + 2, 0xFFFFFF, false);

        // Action buttons for collected books
        if (entry.collected()) {
            int readX = guiLeft + GUI_WIDTH - 62;
            int copyX = guiLeft + GUI_WIDTH - 32;

            // Read button
            boolean hoverRead = mouseX >= readX && mouseX < readX + 26 && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
            Component readLabel = Component.literal("[Read]").withStyle(
                    hoverRead ? ChatFormatting.WHITE : ChatFormatting.AQUA);
            graphics.drawString(this.font, readLabel, readX, entryY + 2, 0x55FFFF, false);
            entryRegions.add(new EntryRegion(readX, entryY, readX + 26, entryY + ENTRY_HEIGHT, entry.id(), EntryAction.READ));

            // Copy button (if allowed)
            if (data.allowCopy) {
                boolean hoverCopy = mouseX >= copyX && mouseX < copyX + 24 && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
                Component copyLabel = Component.literal("[C]").withStyle(
                        hoverCopy ? ChatFormatting.WHITE : ChatFormatting.GOLD);
                graphics.drawString(this.font, copyLabel, copyX, entryY + 2, 0xFFAA00, false);
                entryRegions.add(new EntryRegion(copyX, entryY, copyX + 24, entryY + ENTRY_HEIGHT, entry.id(), EntryAction.COPY));
            }

            // Clickable title region
            entryRegions.add(new EntryRegion(titleX, entryY, titleX + titleWidth, entryY + ENTRY_HEIGHT, entry.id(), EntryAction.READ));
        }

        // Category tag
        if (entry.category() != null && entry.collected()) {
            Component categoryComp = Component.literal(" [" + entry.category() + "]")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            graphics.drawString(this.font, categoryComp, titleX + titleWidth, entryY + 2, 0x555555, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (EntryRegion region : entryRegions) {
                if (mouseX >= region.x1 && mouseX < region.x2 && mouseY >= region.y1 && mouseY < region.y2) {
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
