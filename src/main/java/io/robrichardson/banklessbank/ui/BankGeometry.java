package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.model.BankTab;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * All geometry constants and pure rect maths for the bank overlay. All rects are in overlay-local
 * coordinates (0,0 = overlay top-left).
 *
 * <p>The numbers are taken from a 400x275 screenshot of the real bank interface, which is the
 * game's 488x334 bank widget scaled by 400/488 = 0.8197. Dividing the measured pixels back out
 * gives a 48x36 item slot on an 8-column grid, an 8px steel frame, a 16px scrollbar and a ~30px
 * button bar along the bottom, which is what the constants below encode. Two places deliberately
 * differ: the real bank keeps a ~58px gutter down the left for its deposit buttons, which a viewer
 * has no use for, and its tab buttons are 36 wide rather than the real bank's 48.
 *
 * <p>Unlike the real bank, the window is resizable on both axes, so the column and row counts are
 * per-view values rather than constants: an instance carries one {@code (cols, rows)} pair and
 * every rect is derived from it. Columns run {@link #MIN_COLS}..{@link #MAX_COLS} in whole slots,
 * rows {@link #MIN_ROWS}..{@link #MAX_ROWS}. These are the window's columns, i.e. a viewport: a
 * slot's flat index maps to {@code (index / tabCols, index % tabCols)} at the tab's own layout
 * width, so resizing never moves an item. A window wider than the tab shows blank columns to the
 * right of it; a narrower one scrolls horizontally, which is why the grid has a scrollbar along the
 * bottom as well as down the right.
 *
 * <p>The tab strip holds {@code [All] + 9 tabs + [+]} = 11 buttons. At {@value #DEFAULT_COLS}
 * columns or wider they are all {@value #TAB_W} wide, which is exactly {@link #ITEM_SPRITE_W} so
 * tab icons draw unscaled. Narrower than that the buttons shrink to share the strip evenly (see
 * {@link #tabWidth(int)}), which keeps every tab and the [+] reachable at any width rather than
 * clipping some of them off the right edge.
 */
public final class BankGeometry
{
	public static final int BORDER = 8;
	public static final int TITLE_H = 26;
	public static final int CLOSE_SIZE = 25;
	public static final int TAB_STRIP_H = 40;
	public static final int TAB_W = 36;
	public static final int TAB_H = 36;
	/** Floor on a shrunk tab button, so the strip never collapses to slivers. */
	public static final int MIN_TAB_W = 12;
	public static final int SLOT_W = 48;
	public static final int SLOT_H = 36;
	/** Header/divider row height. Tall enough to fit a small tab icon in the All-view dividers. */
	public static final int HEADER_H = 24;
	/** Left margin reserved for a divider's tab icon, before its label starts. */
	public static final int DIVIDER_ICON_W = 20;
	/** Button bar along the bottom: search button, search text, view-mode button. */
	public static final int BOTTOM_H = 30;
	public static final int BOTTOM_BUTTON = 24;
	public static final int MODE_BUTTON_W = 58;
	public static final int SCROLLBAR_W = 16;
	/** Height of each of the scrollbar's two arrow buttons; also their width. */
	public static final int SCROLL_ARROW = 16;
	public static final int SCROLL_MIN_THUMB = 20;
	/** Window column range, kept identical to a tab's layout width range. */
	public static final int DEFAULT_COLS = BankTab.DEFAULT_COLS;
	public static final int MIN_COLS = BankTab.MIN_COLS;
	public static final int MAX_COLS = BankTab.MAX_COLS;
	public static final int DEFAULT_ROWS = 6;
	public static final int MIN_ROWS = 3;
	public static final int MAX_ROWS = 20;
	/** Self-drawn bottom-right resize grip; square, sits over the frame corner. */
	public static final int GRIP = 16;
	public static final int ITEM_SPRITE_W = 36;
	public static final int ITEM_SPRITE_H = 32;
	public static final int ITEM_DX = 6;
	public static final int ITEM_DY = 2;
	public static final int SCROLL_STEP = 36;

	// Context menu geometry, copied from the game's own "Choose Option" menu so ours is
	// indistinguishable from it: a 1px frame, a 18px header band, then 15px rows, with the text
	// inset 3px from the left and the whole box 8px wider than its widest row. Not part of the
	// design's constant table; used only by ContextMenu / BankViewModel's menu building, which
	// needs some fixed metric since Tier 1 has no Graphics2D / FontMetrics to measure with.
	public static final int MENU_ENTRY_H = 15;
	/** Height of the header band holding {@link ContextMenu#TITLE}, frame included. */
	public static final int MENU_HEADER_H = 18;
	/** Gap between the header band and the first row, as the game leaves. */
	public static final int MENU_BODY_GAP = 2;
	/** Slack added below the last row so the frame closes, matching the game's {@code 15n + 22}. */
	public static final int MENU_BOTTOM_PAD = 2;
	/** Text inset from the menu's left edge, for the header and every row alike. */
	public static final int MENU_TEXT_X = 3;
	/** Total padding added to the widest row's text width. */
	public static final int MENU_PADDING = 8;
	/**
	 * Per-character width estimate for the RuneScape font at the size the menu draws it. Tier 1 has
	 * no FontMetrics, so row widths are estimated; this deliberately over-estimates (real mixed-case
	 * text averages nearer 6px) so a row can never render wider than the box that was sized for it.
	 */
	public static final int MENU_CHAR_W = 7;

	/** The menu box's height for {@code n} rows: the game's {@code 15n + 22}. */
	public static int menuHeight(int entryCount)
	{
		return MENU_HEADER_H + MENU_BODY_GAP + entryCount * MENU_ENTRY_H + MENU_BOTTOM_PAD;
	}

	private final int cols;
	private final int rows;

	private BankGeometry(int cols, int rows)
	{
		this.cols = cols;
		this.rows = rows;
	}

	/** A geometry for this many columns and rows, both clamped to their legal range. */
	public static BankGeometry of(int cols, int rows)
	{
		return new BankGeometry(clamp(cols, MIN_COLS, MAX_COLS), clamp(rows, MIN_ROWS, MAX_ROWS));
	}

	public static BankGeometry defaults()
	{
		return of(DEFAULT_COLS, DEFAULT_ROWS);
	}

	public int getCols()
	{
		return cols;
	}

	public int getRows()
	{
		return rows;
	}

	// ---- static size maths -------------------------------------------------------------------

	/** Window width for a column count: both borders, the grid and the scrollbar. */
	public static int width(int cols)
	{
		return chromeWidth() + cols * SLOT_W;
	}

	public static int height(int rows)
	{
		return chromeHeight() + rows * SLOT_H;
	}

	/** Chrome width with no columns at all: both borders and the scrollbar. */
	public static int chromeWidth()
	{
		return BORDER * 2 + SCROLLBAR_W;
	}

	/**
	 * Chrome height with no rows at all: both borders, title, tab strip and the bottom bar. No row is
	 * reserved for the horizontal scrollbar: unlike the vertical bar's always-present column, it is
	 * drawn overlaying the bottom of the grid only when the active tab needs it, so the window's
	 * height depends only on {@link #DEFAULT_ROWS} / the viewport row count, never on which tab is
	 * showing or how wide its layout happens to be.
	 */
	public static int chromeHeight()
	{
		return BORDER * 2 + TITLE_H + TAB_STRIP_H + BOTTOM_H;
	}

	public static Dimension size(int cols, int rows)
	{
		return new Dimension(width(cols), height(rows));
	}

	/** Columns that a window of this pixel width would have, unclamped. */
	public static int colsForWidth(int pixelWidth)
	{
		return Math.round((pixelWidth - chromeWidth()) / (float) SLOT_W);
	}

	/** Rows that a window of this pixel height would have, unclamped. */
	public static int rowsForHeight(int pixelHeight)
	{
		return Math.round((pixelHeight - chromeHeight()) / (float) SLOT_H);
	}

	// ---- instance rects ----------------------------------------------------------------------

	public int width()
	{
		return width(cols);
	}

	public int height()
	{
		return height(rows);
	}

	public int gridWidth()
	{
		return cols * SLOT_W;
	}

	public Dimension size()
	{
		return new Dimension(width(), height());
	}

	/** The resize grip, in overlay-local coordinates. */
	public Rectangle resizeGrip()
	{
		return new Rectangle(width() - GRIP, height() - GRIP, GRIP, GRIP);
	}

	public Rectangle titleBar()
	{
		return new Rectangle(BORDER, BORDER, width() - BORDER * 2, TITLE_H);
	}

	public Rectangle closeButton()
	{
		Rectangle bar = titleBar();
		int x = width() - BORDER - 2 - CLOSE_SIZE;
		int y = bar.y + (TITLE_H - CLOSE_SIZE) / 2;
		return new Rectangle(x, y, CLOSE_SIZE, CLOSE_SIZE);
	}

	public Rectangle tabStrip()
	{
		return new Rectangle(BORDER, BORDER + TITLE_H, width() - BORDER * 2, TAB_STRIP_H);
	}

	/**
	 * Width of one tab button when the strip holds {@code stripLength} of them: the natural
	 * {@value #TAB_W} whenever they all fit, otherwise an even share of the strip so nothing is
	 * pushed off the right edge. Never below {@value #MIN_TAB_W}.
	 */
	public int tabWidth(int stripLength)
	{
		if (stripLength <= 0)
		{
			return TAB_W;
		}
		int share = tabStrip().width / stripLength;
		return clamp(share, MIN_TAB_W, TAB_W);
	}

	/** 0 = All, 1..n = layout tabs, n+1 = plus. */
	public Rectangle tabAt(int stripIndex, int stripLength)
	{
		Rectangle strip = tabStrip();
		int w = tabWidth(stripLength);
		int x = strip.x + stripIndex * w;
		int y = strip.y + (TAB_STRIP_H - TAB_H) / 2;
		return new Rectangle(x, y, w, TAB_H);
	}

	/** Viewport, excludes the scrollbar. */
	public Rectangle grid()
	{
		int y = BORDER + TITLE_H + TAB_STRIP_H;
		return new Rectangle(BORDER, y, gridWidth(), rows * SLOT_H);
	}

	/** The whole scrollbar column: up arrow, track, down arrow. */
	public Rectangle scrollbar()
	{
		Rectangle grid = grid();
		return new Rectangle(grid.x + grid.width, grid.y, SCROLLBAR_W, grid.height);
	}

	public Rectangle scrollUp()
	{
		Rectangle bar = scrollbar();
		return new Rectangle(bar.x, bar.y, SCROLLBAR_W, SCROLL_ARROW);
	}

	public Rectangle scrollDown()
	{
		Rectangle bar = scrollbar();
		return new Rectangle(bar.x, bar.y + bar.height - SCROLL_ARROW, SCROLLBAR_W, SCROLL_ARROW);
	}

	/** The draggable part of the scrollbar, between the two arrow buttons. */
	public Rectangle scrollTrack()
	{
		Rectangle bar = scrollbar();
		int h = Math.max(0, bar.height - SCROLL_ARROW * 2);
		return new Rectangle(bar.x, bar.y + SCROLL_ARROW, SCROLLBAR_W, h);
	}

	/**
	 * The whole horizontal scrollbar row: left arrow, track, right arrow. Unlike the vertical
	 * scrollbar's own column, this is not reserved space - it overlays the bottom 16px of the grid
	 * area, so it is only meaningful to draw or hit-test when the caller knows a wider-than-viewport
	 * tab makes it visible ({@code BankViewModel.isHScrollbarVisible()}).
	 */
	public Rectangle hScrollbar()
	{
		Rectangle grid = grid();
		return new Rectangle(grid.x, grid.y + grid.height - SCROLLBAR_W, gridWidth(), SCROLLBAR_W);
	}

	public Rectangle hScrollLeft()
	{
		Rectangle bar = hScrollbar();
		return new Rectangle(bar.x, bar.y, SCROLL_ARROW, SCROLLBAR_W);
	}

	public Rectangle hScrollRight()
	{
		Rectangle bar = hScrollbar();
		return new Rectangle(bar.x + bar.width - SCROLL_ARROW, bar.y, SCROLL_ARROW, SCROLLBAR_W);
	}

	/** The draggable part of the horizontal scrollbar, between its two arrow buttons. */
	public Rectangle hScrollTrack()
	{
		Rectangle bar = hScrollbar();
		int w = Math.max(0, bar.width - SCROLL_ARROW * 2);
		return new Rectangle(bar.x + SCROLL_ARROW, bar.y, w, SCROLLBAR_W);
	}

	/** The whole bottom button bar, full inner width. Sits directly under the grid. */
	public Rectangle bottomBar()
	{
		Rectangle grid = grid();
		return new Rectangle(BORDER, grid.y + grid.height, gridWidth() + SCROLLBAR_W, BOTTOM_H);
	}

	public Rectangle searchButton()
	{
		Rectangle bar = bottomBar();
		return new Rectangle(bar.x + 2, bar.y + (BOTTOM_H - BOTTOM_BUTTON) / 2,
			BOTTOM_BUTTON, BOTTOM_BUTTON);
	}

	public Rectangle modeButton()
	{
		Rectangle bar = bottomBar();
		return new Rectangle(bar.x + bar.width - 2 - MODE_BUTTON_W,
			bar.y + (BOTTOM_H - BOTTOM_BUTTON) / 2, MODE_BUTTON_W, BOTTOM_BUTTON);
	}

	/**
	 * The "add an item" button, immediately left of the view-mode button. Square like the search
	 * button, and the reason {@link #searchBox()} measures its right edge from here rather than from
	 * the mode button.
	 */
	public Rectangle addButton()
	{
		Rectangle mode = modeButton();
		return new Rectangle(mode.x - 3 - BOTTOM_BUTTON, mode.y, BOTTOM_BUTTON, BOTTOM_BUTTON);
	}

	/** The search text field: everything on the bar between the search button and the add button. */
	public Rectangle searchBox()
	{
		Rectangle button = searchButton();
		Rectangle add = addButton();
		int x = button.x + button.width + 3;
		return new Rectangle(x, button.y, Math.max(0, add.x - 3 - x), BOTTOM_BUTTON);
	}

	public Rectangle slotInRow(int rowLocalY, int col)
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

	/** {@link #thumb} turned on its side, for the horizontal scrollbar. */
	public static Rectangle hThumb(Rectangle track, int scroll, int maxScroll, int contentW, int viewportW)
	{
		int thumbW = track.width;
		if (contentW > 0 && contentW > viewportW)
		{
			thumbW = Math.max(SCROLL_MIN_THUMB, track.width * viewportW / contentW);
			thumbW = Math.min(thumbW, track.width);
		}

		int thumbX = track.x;
		if (maxScroll > 0)
		{
			int range = track.width - thumbW;
			thumbX = track.x + (int) ((long) range * scroll / maxScroll);
		}

		return new Rectangle(thumbX, track.y, thumbW, track.height);
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
