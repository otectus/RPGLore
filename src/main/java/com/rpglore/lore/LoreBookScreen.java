package com.rpglore.lore;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.List;

/**
 * Custom book screen that prepends an auto-generated title page before the
 * book's actual content pages. The title page displays the book's title
 * (scaled up, bold, colored) and author (normal size, bold, colored),
 * both centered horizontally and vertically on the page.
 *
 * <p>All content pages are offset by +1 so that the title page is page 0
 * and the first real content page is page 1. Navigation, page numbering,
 * and caching all work transparently via a {@link TitlePageBookAccess}
 * wrapper that intercepts the vanilla {@link BookAccess} interface.
 */
@OnlyIn(Dist.CLIENT)
public class LoreBookScreen extends BookViewScreen {

    // Custom book GUI texture (replaces vanilla's textures/gui/book.png)
    private static final ResourceLocation LORE_BOOK_TEXTURE =
            new ResourceLocation("rpg_lore", "textures/gui/book.png");

    // These constants are sourced from BookViewScreen.java (1.20.1 official mappings).
    // They are private in vanilla and cannot be referenced directly.
    // If porting to a new Minecraft version, verify these values against the
    // decompiled BookViewScreen source.
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_HEIGHT = 128;
    private static final int IMAGE_WIDTH = 192;
    private static final int PAGE_TEXT_X_OFFSET = 36;
    private static final int PAGE_TEXT_Y_OFFSET = 30;

    // --- Title page layout ---
    private static final float TITLE_SCALE = 1.5f;
    private static final String ORNAMENT = "* * *";

    // --- Book metadata (extracted from NBT) ---
    private final String bookTitle;
    private final String bookAuthor;
    private final int titleColorRgb;
    private final int authorColorRgb;
    private final BookViewScreen.BookAccess wrappedAccess;

    // Shadow of the private super.currentPage field, kept in sync via
    // overrides of pageForward(), pageBack(), and forcePage().
    // FRAGILE: if Mojang adds new navigation methods that modify currentPage
    // in a future version, this shadow will desync. Verify on version upgrades.
    private int trackedPage;

    // Local page cache (mirrors vanilla's private cache for content pages)
    private List<FormattedCharSequence> cachedLines = Collections.emptyList();
    private int localCachedPage = -1;

    public LoreBookScreen(ItemStack stack) {
        this(stack, new TitlePageBookAccess(new WrittenBookAccess(stack)));
    }

    private LoreBookScreen(ItemStack stack, BookViewScreen.BookAccess access) {
        super(access);
        this.wrappedAccess = access;

        CompoundTag tag = stack.getTag();
        if (tag != null) {
            this.bookTitle = tag.getString("title");
            this.bookAuthor = tag.getString("author");
            this.titleColorRgb = parseHexColor(
                    tag.contains("lore_title_color") ? tag.getString("lore_title_color") : null,
                    ChatFormatting.YELLOW);
            this.authorColorRgb = parseHexColor(
                    tag.contains("lore_author_color") ? tag.getString("lore_author_color") : null,
                    ChatFormatting.DARK_GRAY);
        } else {
            this.bookTitle = "";
            this.bookAuthor = "";
            this.titleColorRgb = 0xFFFF55;
            this.authorColorRgb = 0x555555;
        }
    }

    // ------------------------------------------------------------------
    // Page tracking — keep our shadow field in sync with the private super field
    // ------------------------------------------------------------------

    @Override
    protected void pageForward() {
        if (trackedPage < wrappedAccess.getPageCount() - 1) {
            super.pageForward();
            trackedPage++;
        }
    }

    @Override
    protected void pageBack() {
        if (trackedPage > 0) {
            super.pageBack();
            trackedPage--;
        }
    }

    @Override
    protected boolean forcePage(int page) {
        boolean result = super.forcePage(page);
        if (result) trackedPage = page;
        return result;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Full render override so that ALL pages (title and content) use our custom
     * book texture instead of the vanilla one. The vanilla super.render() hardcodes
     * {@link BookViewScreen#BOOK_LOCATION} which cannot be overridden, so we
     * replicate its rendering logic here with {@link #LORE_BOOK_TEXTURE}.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int bookLeft = (this.width - IMAGE_WIDTH) / 2;
        int bookTop = 2;

        // Draw our custom book texture for all pages
        graphics.blit(LORE_BOOK_TEXTURE, bookLeft, bookTop, 0, 0, IMAGE_WIDTH, IMAGE_WIDTH);

        if (trackedPage == 0) {
            renderTitlePage(graphics, bookLeft, bookTop);
        } else {
            renderContentPage(graphics, bookLeft, bookTop);
        }

        // Render widgets (navigation buttons, Done button) on top
        for (Renderable widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderTitlePage(GuiGraphics graphics, int bookLeft, int bookTop) {
        int textX = bookLeft + PAGE_TEXT_X_OFFSET;
        int textY = bookTop + PAGE_TEXT_Y_OFFSET;
        int textCenterX = textX + TEXT_WIDTH / 2;
        int textCenterY = textY + TEXT_HEIGHT / 2;

        PoseStack pose = graphics.pose();

        // --- Title (scaled up, bold, centered) ---
        if (!bookTitle.isEmpty()) {
            Component titleComp = Component.literal(bookTitle)
                    .withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(titleColorRgb)));

            int titleWidth = this.font.width(titleComp);
            float scaledWidth = titleWidth * TITLE_SCALE;

            // If the scaled title is wider than the text area, reduce scale to fit
            float effectiveScale = TITLE_SCALE;
            if (scaledWidth > TEXT_WIDTH) {
                effectiveScale = (float) TEXT_WIDTH / titleWidth;
            }

            // Vertical position: roughly 35% down the text area
            float titleDrawY = textCenterY - 20;

            pose.pushPose();
            float titleDrawX = textCenterX - (titleWidth * effectiveScale) / 2.0f;
            pose.translate(titleDrawX, titleDrawY, 0);
            pose.scale(effectiveScale, effectiveScale, 1.0f);
            graphics.drawString(this.font, titleComp, 0, 0, titleColorRgb, false);
            pose.popPose();
        }

        // --- Ornament separator ---
        Component ornament = Component.literal(ORNAMENT)
                .withStyle(ChatFormatting.DARK_GRAY);
        int ornamentWidth = this.font.width(ornament);
        int ornamentY = textCenterY + 2;
        graphics.drawString(this.font, ornament,
                textCenterX - ornamentWidth / 2, ornamentY, 0x555555, false);

        // --- Author (normal size, bold, centered) ---
        if (!bookAuthor.isEmpty()) {
            Component authorComp = Component.translatable("book.byAuthor", bookAuthor)
                    .withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(authorColorRgb)));

            int authorWidth = this.font.width(authorComp);
            int authorY = ornamentY + 14;
            graphics.drawString(this.font, authorComp,
                    textCenterX - authorWidth / 2, authorY, authorColorRgb, false);
        }
    }

    /**
     * Renders a content page (page 1+) using the same layout as vanilla's
     * BookViewScreen.render(), but with our custom texture already drawn.
     */
    private void renderContentPage(GuiGraphics graphics, int bookLeft, int bookTop) {
        // Rebuild line cache if page changed
        if (localCachedPage != trackedPage) {
            FormattedText pageText = wrappedAccess.getPage(trackedPage);
            cachedLines = this.font.split(pageText, TEXT_WIDTH);
            localCachedPage = trackedPage;
        }

        // Page indicator (e.g. "1 / 3" — offset by -1 so title page isn't counted)
        int contentPage = trackedPage - 1;
        int totalContentPages = wrappedAccess.getPageCount() - 1;
        Component pageMsg = Component.translatable("book.pageIndicator",
                contentPage + 1, Math.max(totalContentPages, 1));
        int pageMsgWidth = this.font.width(pageMsg);
        graphics.drawString(this.font, pageMsg,
                bookLeft - pageMsgWidth + IMAGE_WIDTH - 44, 18, 0x000000, false);

        // Draw text lines
        int maxLines = Math.min(TEXT_HEIGHT / 9, cachedLines.size());
        for (int line = 0; line < maxLines; line++) {
            graphics.drawString(this.font, cachedLines.get(line),
                    bookLeft + PAGE_TEXT_X_OFFSET, 32 + line * 9, 0x000000, false);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int parseHexColor(String hex, ChatFormatting fallback) {
        if (hex != null && !hex.isEmpty()) {
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {}
        }
        TextColor tc = TextColor.fromLegacyFormat(fallback);
        return tc != null ? tc.getValue() : 0xFFFFFF;
    }

    // ------------------------------------------------------------------
    // BookAccess wrapper that injects the title page as page 0
    // ------------------------------------------------------------------

    /**
     * Wraps a real {@link BookAccess} and inserts a dummy page 0 (the title
     * page, which is rendered manually). All real pages are shifted by +1.
     */
    private static class TitlePageBookAccess implements BookViewScreen.BookAccess {
        private final BookViewScreen.BookAccess delegate;

        TitlePageBookAccess(BookViewScreen.BookAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getPageCount() {
            return delegate.getPageCount() + 1;
        }

        @Override
        public FormattedText getPageRaw(int page) {
            if (page == 0) {
                // Title page — content is drawn manually in renderTitlePage()
                return FormattedText.EMPTY;
            }
            return delegate.getPageRaw(page - 1);
        }
    }
}
