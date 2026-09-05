package io.robrichardson.banklessbank.ui;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * All geometry constants and pure rect maths for the bank overlay. Static only. All rects are in
 * overlay-local coordinates (0,0 = overlay top-left).
 *
 * <p>The numbers are taken from a 400x275 screenshot of the real bank interface, which is the
 * game's 488x334 bank widget scaled by 400/488 = 0.8197. Dividing the measured pixels back out
 * gives a 48x36 item slot on an 8-column grid, an 8px steel frame, a 16px scrollbar and a ~30px
 * button bar along the bottom, which is what the constants below encode. Two places deliberately
 * differ: the real bank keeps a ~58px gutter down the left for its deposit buttons, which a viewer
 * has no use for, and its tab buttons are 48 wide, which we cannot afford because
 * {@code [All] + 10 tabs + [+]} has to fit inside {@link #GRID_W} (384 / 12 = 32).
 */
public final class BankGeometry
{
	public static final int BORDER = 8;
	public static final int TITLE_H = 26;
	public static final int CLOSE_SIZE = 25;
	public static final int TAB_STRIP_H = 36;
	public static final int TAB_W = 32;
	public static final int TAB_H = 32;
	public static final int SLOT_W = 48;
	public static final int SLOT_H = 36;
	public static final int COLS = 8;
	public static final int HEADER_H = 16;
	/** Button bar along the bottom: search button, search text, view-mode button. */
	public static final int BOTTOM_H = 30;
	public static final int BOTTOM_BUTTON = 24;
	public static final int MODE_BUTTON_W = 58;
	public static final int SCROLLBAR_W = 16;
	/** Height of each of the scrollbar's two arrow buttons; also their width. */
	public static final int SCROLL_ARROW = 16;
	public static final int SCROLL_MIN_THUMB = 20;
	public static final int DEFAULT_ROWS = 6;
	public static final int MIN_ROWS = 3;
	public static final int MAX_ROWS = 14;
	public static final int GRID_W = COLS * SLOT_W;
	public static final int WIDTH = BORDER * 2 + GRID_W + SCROLLBAR_W;
	public static final int ITEM_SPRITE_W = 36;
	public static final int ITEM_SPRITE_H = 32;
	public static final int ITEM_DX = 6;
	public static final int ITEM_DY = 2;
	public static final int SCROLL_STEP = 36;

	// Context menu geometry. Not part of the design's constant table; used only by ContextMenu /
	// BankViewModel's menu building, which needs some fixed metric since Tier 1 has no Graphics2D
	// / FontMetrics to measure label widths with.
	public static final int MENU_ENTRY_H = 15;
	public static final int MENU_PADDING = 16;
	public static final int MENU_CHAR_W = 6;

	private BankGeometry()
	{
	}

	public static int height(int rows)
	{
		return BORDER * 2 + TITLE_H + TAB_STRIP_H + rows * SLOT_H + BOTTOM_H;
	}

	public static Dimension size(int rows)
	{
		return new Dimension(WIDTH, height(rows));
	}

	public static Rectangle titleBar()
	{
		return new Rectangle(BORDER, BORDER, WIDTH - BORDER * 2, TITLE_H);
	}

	public static Rectangle closeButton()
	{
		Rectangle bar = titleBar();
		int x = WIDTH - BORDER - 2 - CLOSE_SIZE;
		int y = bar.y + (TITLE_H - CLOSE_SIZE) / 2;
		return new Rectangle(x, y, CLOSE_SIZE, CLOSE_SIZE);
	}

	public static Rectangle tabStrip()
	{
		return new Rectangle(BORDER, BORDER + TITLE_H, WIDTH - BORDER * 2, TAB_STRIP_H);
	}

	/** 0 = All, 1..n = layout tabs, n+1 = plus. */
	public static Rectangle tabAt(int stripIndex)
	{
		Rectangle strip = tabStrip();
		int x = strip.x + stripIndex * TAB_W;
		int y = strip.y + (TAB_STRIP_H - TAB_H) / 2;
		return new Rectangle(x, y, TAB_W, TAB_H);
	}

	/** Viewport, excludes the scrollbar. */
	public static Rectangle grid(int rows)
	{
		int y = BORDER + TITLE_H + TAB_STRIP_H;
		return new Rectangle(BORDER, y, GRID_W, rows * SLOT_H);
	}

	/** The whole scrollbar column: up arrow, track, down arrow. */
	public static Rectangle scrollbar(int rows)
	{
		Rectangle grid = grid(rows);
		return new Rectangle(grid.x + grid.width, grid.y, SCROLLBAR_W, grid.height);
	}

	public static Rectangle scrollUp(int rows)
	{
		Rectangle bar = scrollbar(rows);
		return new Rectangle(bar.x, bar.y, SCROLLBAR_W, SCROLL_ARROW);
	}

	public static Rectangle scrollDown(int rows)
	{
		Rectangle bar = scrollbar(rows);
		return new Rectangle(bar.x, bar.y + bar.height - SCROLL_ARROW, SCROLLBAR_W, SCROLL_ARROW);
	}

	/** The draggable part of the scrollbar, between the two arrow buttons. */
	public static Rectangle scrollTrack(int rows)
	{
		Rectangle bar = scrollbar(rows);
		int h = Math.max(0, bar.height - SCROLL_ARROW * 2);
		return new Rectangle(bar.x, bar.y + SCROLL_ARROW, SCROLLBAR_W, h);
	}

	/** The whole bottom button bar, full inner width. */
	public static Rectangle bottomBar(int rows)
	{
		Rectangle grid = grid(rows);
		return new Rectangle(BORDER, grid.y + grid.height, GRID_W + SCROLLBAR_W, BOTTOM_H);
	}

	public static Rectangle searchButton(int rows)
	{
		Rectangle bar = bottomBar(rows);
		return new Rectangle(bar.x + 2, bar.y + (BOTTOM_H - BOTTOM_BUTTON) / 2,
			BOTTOM_BUTTON, BOTTOM_BUTTON);
	}

	public static Rectangle modeButton(int rows)
	{
		Rectangle bar = bottomBar(rows);
		return new Rectangle(bar.x + bar.width - 2 - MODE_BUTTON_W,
			bar.y + (BOTTOM_H - BOTTOM_BUTTON) / 2, MODE_BUTTON_W, BOTTOM_BUTTON);
	}

	/** The search text field: everything on the bar between the two buttons. */
	public static Rectangle searchBox(int rows)
	{
		Rectangle button = searchButton(rows);
		Rectangle mode = modeButton(rows);
		int x = button.x + button.width + 3;
		return new Rectangle(x, button.y, Math.max(0, mode.x - 3 - x), BOTTOM_BUTTON);
	}

	public static Rectangle slotInRow(int rowLocalY, int col)
	{
		return new Rectangle(BORDER + col * SLOT_W, rowLocalY, SLOT_W, SLOT_H);
	}

	public static Rectangle thumb(Rectangle track, int scroll, int maxScroll, int contentH, int viewportH)
	{
		int thumbH = track.height;
		if (contentH > 0 && contentH > viewportH)
		{
			thumbH = Math.max(SCROLL_MIN_THUMB, track.height * viewportH / contentH);
			thumbH = Math.min(thumbH, track.height);
		}

		int thumbY = track.y;
		if (maxScroll > 0)
		{
			int range = track.height - thumbH;
			thumbY = track.y + (int) ((long) range * scroll / maxScroll);
		}

		return new Rectangle(track.x, thumbY, track.width, thumbH);
	}
}
