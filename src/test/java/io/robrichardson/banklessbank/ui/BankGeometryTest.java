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
		assertEquals(BankGeometry.WIDTH - BankGeometry.BORDER - 4 - BankGeometry.CLOSE_SIZE, close.x);
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
	public void searchBoxSitsDirectlyBelowGrid()
	{
		Rectangle grid = BankGeometry.grid(6);
		Rectangle search = BankGeometry.searchBox(6);
		assertEquals(grid.y + grid.height, search.y);
		assertEquals(BankGeometry.SEARCH_H, search.height);
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
