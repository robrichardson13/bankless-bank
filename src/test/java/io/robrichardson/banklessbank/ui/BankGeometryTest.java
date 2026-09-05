package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import org.junit.Test;

/** Behaviour tests for {@link BankGeometry}'s pure rect maths. */
public class BankGeometryTest
{
	@Test
	public void tabStripFitsAllTwelveStripEntriesExactly()
	{
		assertEquals(BankGeometry.GRID_W, BankGeometry.TAB_W * 12);
	}

	@Test
	public void heightGrowsBySlotHeightPerRow()
	{
		int h6 = BankGeometry.height(6);
		int h7 = BankGeometry.height(7);
		assertEquals(BankGeometry.SLOT_H, h7 - h6);
	}

	@Test
	public void sizeMatchesFixedWidthAndComputedHeight()
	{
		assertEquals(BankGeometry.WIDTH, BankGeometry.size(6).width);
		assertEquals(BankGeometry.height(6), BankGeometry.size(6).height);
	}

	@Test
	public void closeButtonSitsInsideTitleBarNearRightEdge()
	{
		Rectangle title = BankGeometry.titleBar();
		Rectangle close = BankGeometry.closeButton();
		assertTrue(title.contains(close));
		assertEquals(BankGeometry.WIDTH - BankGeometry.BORDER - 2 - BankGeometry.CLOSE_SIZE, close.x);
	}

	@Test
	public void tabAtStripIndexZeroStartsAtTabStripLeftEdge()
	{
		Rectangle strip = BankGeometry.tabStrip();
		Rectangle tab0 = BankGeometry.tabAt(0);
		assertEquals(strip.x, tab0.x);
	}

	@Test
	public void tabAtAdvancesByTabWidthPerIndex()
	{
		Rectangle tab0 = BankGeometry.tabAt(0);
		Rectangle tab1 = BankGeometry.tabAt(1);
		assertEquals(BankGeometry.TAB_W, tab1.x - tab0.x);
	}

	@Test
	public void gridExcludesScrollbar()
	{
		Rectangle grid = BankGeometry.grid(6);
		Rectangle scrollbar = BankGeometry.scrollbar(6);
		assertEquals(grid.x + grid.width, scrollbar.x);
		assertEquals(BankGeometry.GRID_W, grid.width);
		assertEquals(BankGeometry.SCROLLBAR_W, scrollbar.width);
	}

	@Test
	public void gridHeightScalesWithRowCount()
	{
		assertEquals(6 * BankGeometry.SLOT_H, BankGeometry.grid(6).height);
		assertEquals(10 * BankGeometry.SLOT_H, BankGeometry.grid(10).height);
	}

	@Test
	public void bottomBarSitsDirectlyBelowGridAndSpansGridPlusScrollbar()
	{
		Rectangle grid = BankGeometry.grid(6);
		Rectangle bar = BankGeometry.bottomBar(6);
		assertEquals(grid.y + grid.height, bar.y);
		assertEquals(BankGeometry.BOTTOM_H, bar.height);
		assertEquals(BankGeometry.GRID_W + BankGeometry.SCROLLBAR_W, bar.width);
	}

	@Test
	public void bottomBarHoldsSearchButtonThenFieldThenModeButton()
	{
		Rectangle bar = BankGeometry.bottomBar(6);
		Rectangle button = BankGeometry.searchButton(6);
		Rectangle field = BankGeometry.searchBox(6);
		Rectangle mode = BankGeometry.modeButton(6);

		assertTrue(bar.contains(button));
		assertTrue(bar.contains(field));
		assertTrue(bar.contains(mode));
		assertTrue("the field starts right of the search button", field.x > button.x + button.width - 1);
		assertTrue("the field ends left of the mode button", field.x + field.width <= mode.x);
		assertEquals(BankGeometry.MODE_BUTTON_W, mode.width);
	}

	@Test
	public void geometryMatchesTheRealBankSlotPitchAndFrame()
	{
		// 48x36 slots on 8 columns, an 8px steel frame and a 16px scrollbar, measured off the real
		// bank interface. Guards against the constants drifting back to hand-picked values.
		assertEquals(48, BankGeometry.SLOT_W);
		assertEquals(36, BankGeometry.SLOT_H);
		assertEquals(8, BankGeometry.COLS);
		assertEquals(8, BankGeometry.BORDER);
		assertEquals(16, BankGeometry.SCROLLBAR_W);
		assertEquals(416, BankGeometry.WIDTH);
	}

	@Test
	public void scrollbarSplitsIntoTwoArrowsAndATrackBetweenThem()
	{
		Rectangle bar = BankGeometry.scrollbar(6);
		Rectangle up = BankGeometry.scrollUp(6);
		Rectangle down = BankGeometry.scrollDown(6);
		Rectangle track = BankGeometry.scrollTrack(6);

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
		Rectangle col0 = BankGeometry.slotInRow(100, 0);
		Rectangle col1 = BankGeometry.slotInRow(100, 1);
		assertEquals(BankGeometry.BORDER, col0.x);
		assertEquals(BankGeometry.SLOT_W, col1.x - col0.x);
		assertEquals(100, col0.y);
		assertEquals(BankGeometry.SLOT_W, col0.width);
		assertEquals(BankGeometry.SLOT_H, col0.height);
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
}
