package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import org.junit.Test;

/** Behaviour tests for {@link BankGeometry}'s pure rect maths. */
public class BankGeometryTest
{
	/** The default 8-column, 6-row window, which is what most of these assertions are about. */
	private static BankGeometry geom()
	{
		return BankGeometry.defaults();
	}

	private static BankGeometry geom(int cols, int rows)
	{
		return BankGeometry.of(cols, rows);
	}

	@Test
	public void tabStripFitsAllElevenStripEntriesWithNineTabs()
	{
		// [All] + 9 tabs + [+] = 11 buttons (strip indices 0..10). The strip's right edge is the
		// default window's inner width, 400.
		Rectangle strip = geom().tabStrip();
		Rectangle plusButton = geom().tabAt(10, 11);
		assertTrue(plusButton.x + plusButton.width <= strip.x + strip.width);
	}

	@Test
	public void tabButtonsAreAtLeastAsBigAsTheUnscaledItemSprite()
	{
		assertTrue(BankGeometry.TAB_W >= BankGeometry.ITEM_SPRITE_W);
		assertTrue(BankGeometry.TAB_H >= BankGeometry.ITEM_SPRITE_H);
	}

	@Test
	public void tabAtProducesThirtySixSquareRectsThatNeverOverlap()
	{
		Rectangle previous = null;
		for (int i = 0; i <= 10; i++)
		{
			Rectangle r = geom().tabAt(i, 11);
			assertEquals(36, r.width);
			assertEquals(36, r.height);
			if (previous != null)
			{
				assertTrue("tab " + i + " must not overlap the previous tab", !r.intersects(previous));
			}
			previous = r;
		}
	}

	@Test
	public void heightGrowsBySlotHeightPerRow()
	{
		int h6 = BankGeometry.height(6);
		int h7 = BankGeometry.height(7);
		assertEquals(BankGeometry.SLOT_H, h7 - h6);
	}

	@Test
	public void widthGrowsBySlotWidthPerColumn()
	{
		assertEquals(BankGeometry.SLOT_W, BankGeometry.width(9) - BankGeometry.width(8));
	}

	@Test
	public void sizeMatchesComputedWidthAndHeight()
	{
		assertEquals(BankGeometry.width(8), BankGeometry.size(8, 6).width);
		assertEquals(BankGeometry.height(6), BankGeometry.size(8, 6).height);
		assertEquals(BankGeometry.size(8, 6), geom().size());
	}

	@Test
	public void defaultEightColumnSixRowWindowIs416By328()
	{
		// The 40px tab strip (up from 36) adds 4px over the original default window height. No row
		// is reserved for the horizontal scrollbar: it overlays the grid instead of adding height.
		assertEquals(416, BankGeometry.width(BankGeometry.DEFAULT_COLS));
		assertEquals(328, BankGeometry.height(BankGeometry.DEFAULT_ROWS));
	}

	@Test
	public void closeButtonSitsInsideTitleBarNearRightEdge()
	{
		Rectangle title = geom().titleBar();
		Rectangle close = geom().closeButton();
		assertTrue(title.contains(close));
		assertEquals(geom().width() - BankGeometry.BORDER - 2 - BankGeometry.CLOSE_SIZE, close.x);
	}

	@Test
	public void tabAtStripIndexZeroStartsAtTabStripLeftEdge()
	{
		Rectangle strip = geom().tabStrip();
		Rectangle tab0 = geom().tabAt(0, 11);
		assertEquals(strip.x, tab0.x);
	}

	@Test
	public void tabAtAdvancesByTabWidthPerIndex()
	{
		Rectangle tab0 = geom().tabAt(0, 11);
		Rectangle tab1 = geom().tabAt(1, 11);
		assertEquals(BankGeometry.TAB_W, tab1.x - tab0.x);
	}

	@Test
	public void gridExcludesScrollbar()
	{
		Rectangle grid = geom().grid();
		Rectangle scrollbar = geom().scrollbar();
		assertEquals(grid.x + grid.width, scrollbar.x);
		assertEquals(geom().gridWidth(), grid.width);
		assertEquals(BankGeometry.SCROLLBAR_W, scrollbar.width);
	}

	@Test
	public void gridHeightScalesWithRowCount()
	{
		assertEquals(6 * BankGeometry.SLOT_H, geom(8, 6).grid().height);
		assertEquals(10 * BankGeometry.SLOT_H, geom(8, 10).grid().height);
	}

	@Test
	public void gridWidthScalesWithColumnCount()
	{
		assertEquals(4 * BankGeometry.SLOT_W, geom(4, 6).grid().width);
		assertEquals(12 * BankGeometry.SLOT_W, geom(12, 6).grid().width);
	}

	@Test
	public void bottomBarSitsDirectlyBelowGrid()
	{
		Rectangle grid = geom().grid();
		Rectangle bar = geom().bottomBar();
		assertEquals(grid.y + grid.height, bar.y);
		assertEquals(BankGeometry.BOTTOM_H, bar.height);
		assertEquals(geom().gridWidth() + BankGeometry.SCROLLBAR_W, bar.width);
	}

	@Test
	public void horizontalScrollbarOverlaysTheBottomOfTheGridRatherThanReservingARow()
	{
		Rectangle grid = geom().grid();
		Rectangle bar = geom().hScrollbar();
		assertEquals(grid.x, bar.x);
		assertEquals(grid.y + grid.height - BankGeometry.SCROLLBAR_W, bar.y);
		assertEquals(grid.width, bar.width);
		assertEquals(BankGeometry.SCROLLBAR_W, bar.height);
		// It overlays the grid, so the bottom bar sits directly under the grid, not under the bar.
		assertEquals(geom().bottomBar().y, grid.y + grid.height);
	}

	@Test
	public void horizontalScrollbarArrowsBookendItsTrack()
	{
		Rectangle bar = geom().hScrollbar();
		Rectangle left = geom().hScrollLeft();
		Rectangle right = geom().hScrollRight();
		Rectangle track = geom().hScrollTrack();

		assertEquals(bar.x, left.x);
		assertEquals(bar.x + bar.width - BankGeometry.SCROLL_ARROW, right.x);
		assertEquals(left.x + left.width, track.x);
		assertEquals(right.x, track.x + track.width);
		assertTrue(bar.contains(track));
	}

	@Test
	public void horizontalThumbIsProportionalAndSlidesTheFullTrack()
	{
		Rectangle track = geom().hScrollTrack();
		final int viewport = geom().gridWidth();
		final int content = viewport * 2;
		final int max = content - viewport;

		Rectangle atStart = BankGeometry.hThumb(track, 0, max, content, viewport);
		Rectangle atEnd = BankGeometry.hThumb(track, max, max, content, viewport);

		assertEquals(track.x, atStart.x);
		assertEquals(track.height, atStart.height);
		assertEquals("half the content is visible, so the thumb is half the track",
			track.width / 2, atStart.width);
		assertEquals(track.x + track.width, atEnd.x + atEnd.width);
	}

	@Test
	public void horizontalThumbFillsTheTrackWhenThereIsNothingToScroll()
	{
		Rectangle track = geom().hScrollTrack();
		Rectangle thumb = BankGeometry.hThumb(track, 0, 0, geom().gridWidth(), geom().gridWidth());
		assertEquals(track.x, thumb.x);
		assertEquals(track.width, thumb.width);
	}

	@Test
	public void bottomBarHoldsSearchButtonThenFieldThenModeButton()
	{
		Rectangle bar = geom().bottomBar();
		Rectangle button = geom().searchButton();
		Rectangle field = geom().searchBox();
		Rectangle mode = geom().modeButton();

		assertTrue(bar.contains(button));
		assertTrue(bar.contains(field));
		assertTrue(bar.contains(mode));
		assertTrue("the field starts right of the search button", field.x > button.x + button.width - 1);
		assertTrue("the field ends left of the mode button", field.x + field.width <= mode.x);
		assertEquals(BankGeometry.MODE_BUTTON_W, mode.width);
	}

	@Test
	public void bottomBarKeepsTheAddButtonBetweenTheFieldAndTheModeButtonAtEveryWidth()
	{
		for (int cols = BankGeometry.MIN_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			BankGeometry g = geom(cols, BankGeometry.DEFAULT_ROWS);
			String at = cols + " cols";
			Rectangle add = g.addButton();
			Rectangle field = g.searchBox();
			Rectangle mode = g.modeButton();

			assertTrue("the add button must sit on the bar at " + at, g.bottomBar().contains(add));
			assertEquals("square like the search button at " + at, BankGeometry.BOTTOM_BUTTON, add.width);
			assertEquals(BankGeometry.BOTTOM_BUTTON, add.height);
			assertTrue("the add button must end left of the mode button at " + at,
				add.x + add.width <= mode.x);
			assertFalse("the add button must not overlap the search field at " + at,
				add.intersects(field));
			assertTrue("the search field must keep a usable width at " + at, field.width > 0);
		}
	}

	@Test
	public void geometryMatchesTheRealBankSlotPitchAndFrame()
	{
		// 48x36 slots on 8 columns by default, an 8px steel frame and a 16px scrollbar, measured off
		// the real bank interface. Guards against the constants drifting back to hand-picked values.
		assertEquals(48, BankGeometry.SLOT_W);
		assertEquals(36, BankGeometry.SLOT_H);
		assertEquals(8, BankGeometry.DEFAULT_COLS);
		assertEquals(8, BankGeometry.BORDER);
		assertEquals(16, BankGeometry.SCROLLBAR_W);
		assertEquals(416, BankGeometry.width(BankGeometry.DEFAULT_COLS));
	}

	@Test
	public void scrollbarSplitsIntoTwoArrowsAndATrackBetweenThem()
	{
		Rectangle bar = geom().scrollbar();
		Rectangle up = geom().scrollUp();
		Rectangle down = geom().scrollDown();
		Rectangle track = geom().scrollTrack();

		assertEquals(bar.y, up.y);
		assertEquals(BankGeometry.SCROLL_ARROW, up.height);
		assertEquals(bar.y + bar.height, down.y + down.height);
		assertEquals(BankGeometry.SCROLL_ARROW, down.height);
		assertEquals(up.y + up.height, track.y);
		assertEquals(down.y, track.y + track.height);
		assertEquals(bar.x, track.x);
		assertEquals(BankGeometry.SCROLLBAR_W, track.width);
	}

	@Test
	public void slotInRowPlacesColumnsAcrossGridWidth()
	{
		Rectangle col0 = geom().slotInRow(100, 0);
		Rectangle col1 = geom().slotInRow(100, 1);
		assertEquals(BankGeometry.BORDER, col0.x);
		assertEquals(BankGeometry.SLOT_W, col1.x - col0.x);
		assertEquals(100, col0.y);
		assertEquals(BankGeometry.SLOT_W, col0.width);
		assertEquals(BankGeometry.SLOT_H, col0.height);
	}

	@Test
	public void lastColumnOfEveryWidthEndsAtTheGridsRightEdge()
	{
		for (int cols = BankGeometry.MIN_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			BankGeometry g = geom(cols, 6);
			Rectangle last = g.slotInRow(0, cols - 1);
			Rectangle grid = g.grid();
			assertEquals("cols " + cols, grid.x + grid.width, last.x + last.width);
		}
	}

	@Test
	public void thumbFillsTrackWhenContentFitsInViewport()
	{
		Rectangle track = new Rectangle(0, 0, 12, 216);
		Rectangle thumb = BankGeometry.thumb(track, 0, 0, 100, 216);
		assertEquals(track.height, thumb.height);
		assertEquals(track.y, thumb.y);
	}

	@Test
	public void thumbShrinksAndMovesWhenContentOverflows()
	{
		Rectangle track = new Rectangle(0, 0, 12, 216);
		int contentH = 216 * 3;
		int viewportH = 216;
		int maxScroll = contentH - viewportH;

		Rectangle atTop = BankGeometry.thumb(track, 0, maxScroll, contentH, viewportH);
		Rectangle atBottom = BankGeometry.thumb(track, maxScroll, maxScroll, contentH, viewportH);

		assertTrue(atTop.height < track.height);
		assertEquals(track.y, atTop.y);
		assertEquals(track.y + track.height - atBottom.height, atBottom.y);
	}

	@Test
	public void thumbNeverShrinksBelowMinimum()
	{
		Rectangle track = new Rectangle(0, 0, 12, 60);
		Rectangle thumb = BankGeometry.thumb(track, 0, 10000, 100000, 60);
		assertEquals(BankGeometry.SCROLL_MIN_THUMB, thumb.height);
	}

	// ---- column count -----------------------------------------------------------------------

	@Test
	public void widthEqualsChromeWidthPlusColsTimesSlotWidthForEveryColumnCount()
	{
		for (int cols = BankGeometry.MIN_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			assertEquals(BankGeometry.chromeWidth() + cols * BankGeometry.SLOT_W, BankGeometry.width(cols));
		}
		assertEquals(416, BankGeometry.width(8));
	}

	@Test
	public void colsForWidthRoundTripsWithWidthForEveryColumnCount()
	{
		for (int cols = BankGeometry.MIN_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			assertEquals(cols, BankGeometry.colsForWidth(BankGeometry.width(cols)));
		}
	}

	@Test
	public void colsForWidthSnapsToTheNearestWholeColumn()
	{
		int eight = BankGeometry.width(8);
		assertEquals(8, BankGeometry.colsForWidth(eight + BankGeometry.SLOT_W / 2 - 1));
		assertEquals(9, BankGeometry.colsForWidth(eight + BankGeometry.SLOT_W / 2 + 1));
	}

	@Test
	public void ofClampsColumnsAndRowsAtBothEnds()
	{
		assertEquals(BankGeometry.MIN_COLS, BankGeometry.of(1, 6).getCols());
		assertEquals(BankGeometry.MIN_COLS, BankGeometry.of(-40, 6).getCols());
		assertEquals(BankGeometry.MAX_COLS, BankGeometry.of(99, 6).getCols());
		assertEquals(BankGeometry.MIN_ROWS, BankGeometry.of(8, 0).getRows());
		assertEquals(BankGeometry.MAX_ROWS, BankGeometry.of(8, 999).getRows());
		assertEquals(12, BankGeometry.of(12, 9).getCols());
		assertEquals(9, BankGeometry.of(12, 9).getRows());
	}

	@Test
	public void colsForWidthOfATinyWindowIsBelowTheMinimumButOfClampsIt()
	{
		// The derivation itself is unclamped, which is what lets the drag handler hand a raw pixel
		// width straight to the view model and let it do the clamping.
		assertTrue(BankGeometry.colsForWidth(0) < BankGeometry.MIN_COLS);
		assertEquals(BankGeometry.MIN_COLS, BankGeometry.of(BankGeometry.colsForWidth(0), 6).getCols());
	}

	// ---- tab strip at narrow widths -----------------------------------------------------------

	@Test
	public void tabsKeepTheirFullWidthWheneverTheWholeStripFits()
	{
		for (int cols = BankGeometry.DEFAULT_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			assertEquals("cols " + cols, BankGeometry.TAB_W, geom(cols, 6).tabWidth(11));
		}
	}

	@Test
	public void tabsShrinkToShareTheStripWhenTheWindowIsTooNarrowForElevenOfThem()
	{
		BankGeometry narrow = geom(BankGeometry.MIN_COLS, 6);
		int w = narrow.tabWidth(11);
		assertTrue("a narrow window must shrink its tabs", w < BankGeometry.TAB_W);
		assertTrue("but never below the floor", w >= BankGeometry.MIN_TAB_W);
	}

	@Test
	public void everyStripEntryStaysInsideTheStripAtEveryWidth()
	{
		// This is the point of shrinking rather than clipping: at any window width, the [+] button
		// and every tab in between remain fully on screen and therefore clickable.
		for (int cols = BankGeometry.MIN_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			BankGeometry g = geom(cols, 6);
			for (int stripLength = 2; stripLength <= 11; stripLength++)
			{
				Rectangle strip = g.tabStrip();
				Rectangle last = g.tabAt(stripLength - 1, stripLength);
				assertTrue("cols " + cols + " strip " + stripLength,
					last.x + last.width <= strip.x + strip.width);
			}
		}
	}

	// ---- resize grip ------------------------------------------------------------------------

	@Test
	public void heightEqualsChromeHeightPlusRowsTimesSlotHeightForEveryRowCount()
	{
		for (int rows = BankGeometry.MIN_ROWS; rows <= BankGeometry.MAX_ROWS; rows++)
		{
			assertEquals(BankGeometry.chromeHeight() + rows * BankGeometry.SLOT_H, BankGeometry.height(rows));
		}
		assertEquals(328, BankGeometry.height(6));
	}

	@Test
	public void rowsForHeightRoundTripsWithHeightForEveryRowCount()
	{
		for (int rows = BankGeometry.MIN_ROWS; rows <= BankGeometry.MAX_ROWS; rows++)
		{
			assertEquals(rows, BankGeometry.rowsForHeight(BankGeometry.height(rows)));
		}
	}

	@Test
	public void resizeGripSitsInsideTheWindowAndClearsTheSearchBoxAndScrollDownArrow()
	{
		// The grip shares its bottom-right corner with the mode button by a handful of pixels
		// (GRIP=16 vs. the mode button's own corner placement); hitTest checks RESIZE_GRIP first,
		// so the grip always wins there and the mode button loses only that sliver of its hit area.
		// The search box and the scrollbar's down arrow, further from the corner, are genuinely clear.
		for (int cols = BankGeometry.MIN_COLS; cols <= BankGeometry.MAX_COLS; cols++)
		{
			for (int rows = BankGeometry.MIN_ROWS; rows <= BankGeometry.MAX_ROWS; rows++)
			{
				BankGeometry g = geom(cols, rows);
				String at = cols + "x" + rows;
				Rectangle grip = g.resizeGrip();
				Rectangle window = new Rectangle(0, 0, g.width(), g.height());
				assertTrue("grip must sit inside the window at " + at, window.contains(grip));

				assertFalse("grip must not intersect the search box at " + at,
					grip.intersects(g.searchBox()));
				assertFalse("grip must not intersect the scroll-down arrow at " + at,
					grip.intersects(g.scrollDown()));
			}
		}
	}

	@Test
	public void gridTopIsBorderPlusTitleHeightPlusTabStripHeight()
	{
		assertEquals(BankGeometry.BORDER + BankGeometry.TITLE_H + BankGeometry.TAB_STRIP_H,
			geom().grid().y);
		assertEquals(74, geom().grid().y);
	}
}
