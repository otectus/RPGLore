package com.rpglore.lore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reader layout arithmetic: change_page index translation and title fitting.
 * The screen itself is client-only and is not exercised here.
 */
class BookTextLayoutTest {

    // A book with a title page plus 3 content pages
    private static final int PAGE_COUNT = 4;

    @Test
    void changePageTranslatesOneBasedContentPageToWrappedIndex() {
        assertEquals(1, BookTextLayout.changePageTarget("1", PAGE_COUNT));
        assertEquals(2, BookTextLayout.changePageTarget("2", PAGE_COUNT));
        assertEquals(3, BookTextLayout.changePageTarget("3", PAGE_COUNT));
    }

    @Test
    void changePageClampsOutOfRangeValues() {
        assertEquals(3, BookTextLayout.changePageTarget("99", PAGE_COUNT));
        assertEquals(0, BookTextLayout.changePageTarget("0", PAGE_COUNT));
        assertEquals(0, BookTextLayout.changePageTarget("-5", PAGE_COUNT));
    }

    @Test
    void changePageAcceptsPaddedValuesAndRejectsGarbage() {
        assertEquals(2, BookTextLayout.changePageTarget(" 2 ", PAGE_COUNT));
        assertEquals(-1, BookTextLayout.changePageTarget("page two", PAGE_COUNT));
        assertEquals(-1, BookTextLayout.changePageTarget("", PAGE_COUNT));
    }

    @Test
    void titleWrapWidthGrowsAsTheScaleStepShrinks() {
        int full = BookTextLayout.titleWrapWidth(114, 1.5f, 1.0f);
        int floor = BookTextLayout.titleWrapWidth(114, 1.5f, BookTextLayout.MIN_TITLE_SCALE);
        assertEquals(76, full);
        assertTrue(floor > full, "smaller text must be given more wrap room");
    }

    @Test
    void titleWrapWidthNeverDropsBelowOnePixel() {
        assertEquals(1, BookTextLayout.titleWrapWidth(1, 100.0f, 1.0f));
    }

    @Test
    void shortTitleKeepsFullScale() {
        // Fits on one line at every width
        float scale = BookTextLayout.chooseTitleScale(114, 1.5f, wrapWidth -> 1);
        assertEquals(1.0f, scale);
    }

    @Test
    void longTitleStepsDownUntilItFitsInTwoLines() {
        // Three lines until the wrap width reaches 90px, two lines from there on
        float scale = BookTextLayout.chooseTitleScale(114, 1.5f,
                wrapWidth -> wrapWidth >= 90 ? 2 : 3);
        assertTrue(scale < 1.0f && scale >= BookTextLayout.MIN_TITLE_SCALE,
                "expected a reduced but not sub-floor scale, got " + scale);
        assertTrue(BookTextLayout.titleWrapWidth(114, 1.5f, scale) >= 90);
    }

    @Test
    void titleThatNeverFitsStopsAtTheFloorScale() {
        // Caller ellipsizes at this point instead of shrinking further
        float scale = BookTextLayout.chooseTitleScale(114, 1.5f, wrapWidth -> 5);
        assertEquals(BookTextLayout.MIN_TITLE_SCALE, scale);
    }
}
