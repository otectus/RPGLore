package com.rpglore.lore;

/**
 * Pure layout arithmetic shared by the lore book reader. Kept free of client
 * classes so it can be unit tested without a Minecraft instance; the screen
 * supplies the font measurements through {@link LineCounter}.
 */
public final class BookTextLayout {

    /** Maximum number of lines the generated title page will draw for a title. */
    public static final int MAX_TITLE_LINES = 2;

    /** Scale multipliers tried in order before the title is truncated instead. */
    public static final float[] TITLE_SCALE_STEPS = {1.0f, 0.95f, 0.9f, 0.85f, 0.8f, 0.75f};

    /** Floor of {@link #TITLE_SCALE_STEPS}; below this we ellipsize rather than shrink. */
    public static final float MIN_TITLE_SCALE = 0.75f;

    /** Counts how many wrapped lines a title occupies at a given pixel wrap width. */
    @FunctionalInterface
    public interface LineCounter {
        int linesAt(int wrapWidth);
    }

    private BookTextLayout() {}

    /**
     * Translates a {@code change_page} click event value into a page index for the
     * wrapped book access, which carries the synthetic title page at index 0.
     *
     * @param value     raw click event value; a 1-based content page number
     * @param pageCount page count of the wrapped access (content pages + title page)
     * @return the target index, or -1 when the value is not a number
     */
    public static int changePageTarget(String value, int pageCount) {
        int page;
        try {
            page = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
        // 1-based content page -> content index (page - 1) -> +1 for the title page
        return clamp(page, 0, Math.max(pageCount - 1, 0));
    }

    /**
     * Pixel width a title may occupy in unscaled font space, given the scale it
     * will be drawn at.
     */
    public static int titleWrapWidth(int textWidth, float baseScale, float scaleStep) {
        int width = (int) (textWidth / (baseScale * scaleStep));
        return Math.max(width, 1);
    }

    /**
     * Picks the largest scale step at which the title fits within
     * {@link #MAX_TITLE_LINES}. Falls back to {@link #MIN_TITLE_SCALE}, where the
     * caller is expected to truncate the second line instead of shrinking further.
     */
    public static float chooseTitleScale(int textWidth, float baseScale, LineCounter counter) {
        for (float step : TITLE_SCALE_STEPS) {
            if (counter.linesAt(titleWrapWidth(textWidth, baseScale, step)) <= MAX_TITLE_LINES) {
                return step;
            }
        }
        return MIN_TITLE_SCALE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
