package io.robrichardson.banklessbank.ui.harness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
import io.robrichardson.banklessbank.ui.BankGeometry;
import io.robrichardson.banklessbank.ui.BankSlot;
import io.robrichardson.banklessbank.ui.ContextMenu;
import io.robrichardson.banklessbank.ui.ContextMenuEntry;
import io.robrichardson.banklessbank.ui.MenuAction;
import io.robrichardson.banklessbank.ui.ViewMode;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.client.ui.JagexColors;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Renders the bank overlay headlessly and drives it with synthetic AWT input, with no game client
 * and no account. Every scene goes through the real wiring: input enters through
 * {@code BankInputListener}, lands on the controller's action queue, and takes effect on the next
 * {@code BankOverlay.render} call, which is also what produces the frame.
 *
 * <p>PNGs land in {@code build/screenshots} and are copied to the session scratchpad.
 */
public class OverlayScreenshotTest
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	private static final Path BUILD_DIR = Paths.get(System.getProperty("user.dir"), "build", "screenshots");

	/**
	 * Where the frames are written. {@code build/screenshots} always, plus any extra directory named
	 * by the {@code banklessbank.screenshotDir} system property - previously a session scratchpad
	 * path was hardcoded here, which went stale the moment the session that wrote it ended.
	 */
	private static final List<Path> OUT_DIRS = outDirs();

	private static List<Path> outDirs()
	{
		final String extra = System.getProperty("banklessbank.screenshotDir");
		return extra == null || extra.isEmpty()
			? Collections.singletonList(BUILD_DIR)
			: Arrays.asList(BUILD_DIR, Paths.get(extra));
	}

	private static final List<String> WRITTEN = new ArrayList<>();

	@BeforeClass
	public static void headless()
	{
		System.setProperty("java.awt.headless", "true");
	}

	private void shoot(BankHarness harness, String name) throws IOException
	{
		for (Path path : harness.writePng(name, OUT_DIRS))
		{
			WRITTEN.add(path.toString());
		}
	}

	// =========================================================================================
	// The screenshot walkthrough
	// =========================================================================================

	@Test
	public void rendersEveryScene() throws IOException
	{
		final BankHarness harness = new BankHarness();

		// ---- a. closed: only the HUD button is on the canvas -------------------------------
		Dimension closed = harness.render();
		assertNull("a closed view must not draw", closed);
		assertTrue("the HUD button must have painted", harness.nonBackgroundPixels(harness.hudRect()) > 20);
		shoot(harness, "01-closed-hud-button");

		// ---- b. open on the [All] tab, scrolled to the top ---------------------------------
		harness.openViaHudButton();
		Dimension size = harness.render(2);
		assertEquals(BankGeometry.size(BankGeometry.DEFAULT_COLS, BankGeometry.DEFAULT_ROWS), size);
		assertEquals(-1, harness.model().getActiveTab());
		assertEquals(0, harness.model().getScroll());
		assertTrue("more content than fits, so there is something to scroll",
			harness.model().getMaxScroll() > 0);

		final Rectangle firstSlot = harness.slotRectOnCanvas(0);
		assertTrue("the first slot must be painted over the game background",
			harness.nonBackgroundPixels(firstSlot) > 0);
		assertTrue("the first slot must contain a drawn sprite, not just chrome",
			harness.distinctColours(firstSlot) >= 5);

		assertTrue("the vertical scrollbar must be visible when there is content to scroll",
			harness.model().isVScrollbarVisible());
		final Rectangle scrollbar = harness.rectOnCanvas(harness.model().scrollbarRect());
		assertTrue("the scrollbar must be painted", harness.nonBackgroundPixels(scrollbar) > 0);
		assertTrue("the scrollbar must show a thumb against its track",
			harness.distinctColours(scrollbar) >= 2);
		shoot(harness, "02-open-all-tab-top");

		// ---- c. the main tab, where the unowned ids show as placeholders --------------------
		harness.clickTab(1);
		harness.render(2);
		assertEquals(0, harness.model().getActiveTab());
		final List<BankSlot> mainSlots = harness.model().getSlots();
		assertTrue("the main tab holds the placeholder-only ids", mainSlots.get(0).isPlaceholder());
		assertEquals("Dragon dagger", mainSlots.get(0).getName());
		shoot(harness, "03-main-tab-placeholders");

		// ---- g. mid-drag: item lifted off slot 0, hovering slot 3 ---------------------------
		final List<Integer> beforeDrag = new ArrayList<>(harness.layout().getMainTab().getSlots());
		final Point from = harness.centreOfSlot(0);
		final Point to = harness.centreOfSlot(3);

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(to.x, to.y);
		harness.render();

		assertTrue("the drag must be live before the frame is captured", harness.model().isDragging());
		assertEquals(3, harness.model().getDropSlotIndex());
		shoot(harness, "04-mid-drag-caret-and-ghost");

		harness.release(to.x, to.y);
		harness.render();
		assertFalse(harness.model().isDragging());

		final List<Integer> afterDrag = harness.layout().getMainTab().getSlots();
		assertFalse("dropping onto another slot must swap the two items", beforeDrag.equals(afterDrag));
		assertEquals(beforeDrag.get(3), afterDrag.get(0));
		assertEquals(beforeDrag.get(0), afterDrag.get(3));

		// ---- h. right-click context menu on a placeholder slot ------------------------------
		final Point placeholder = harness.centreOfSlot(0);
		harness.moveTo(placeholder);
		harness.rightPress(placeholder.x, placeholder.y);
		harness.render();

		assertTrue("right-clicking a slot opens the menu", harness.model().isMenuOpen());
		final List<String> labels = new ArrayList<>();
		for (ContextMenuEntry entry : harness.model().getMenu().getEntries())
		{
			labels.add(entry.getLabel());
		}
		assertTrue("a placeholder offers its release actions: " + labels,
			labels.contains("Release placeholder"));
		shoot(harness, "05-context-menu-placeholder");

		harness.keyPress(KeyEvent.VK_ESCAPE);
		harness.render();
		assertFalse(harness.model().isMenuOpen());

		// ---- d. a custom tab ---------------------------------------------------------------
		harness.clickTab(2);
		harness.render(2);
		assertEquals(1, harness.model().getActiveTab());
		assertEquals(FakeStorages.RUNE_PLATEBODY, harness.model().getTabIconItemId(1));
		assertFalse("the gear tab's six items fit in one row, nothing to scroll vertically",
			harness.model().isVScrollbarVisible());
		final Rectangle noScrollbar = harness.rectOnCanvas(harness.model().scrollbarRect());
		assertEquals("with nothing to scroll the bar's column shows no thumb/track colours, just panel steel",
			1, harness.distinctColours(noScrollbar));
		shoot(harness, "06-custom-tab-gear");

		// ---- i. self-drawn tooltip for an item held in two storages -------------------------
		// We no longer route through RuneLite's TooltipManager, whose anchor
		// (client.getMouseCanvasPosition()) freezes the instant the cursor enters our bounds because
		// we consume every mouseMoved event inside them. Instead BankOverlay draws its own tooltip
		// at the position we track ourselves, so it must appear right next to the live cursor.
		harness.clickTab(3);
		harness.render(2);
		assertEquals(2, harness.model().getActiveTab());

		final Point airRune = harness.centreOfSlot(0);
		final Point origin = harness.origin();
		harness.moveTo(airRune);
		harness.render();

		final List<String> tooltipLines = harness.model()
			.tooltipLines(airRune.x - origin.x, airRune.y - origin.y);
		assertFalse("hovering a slot must produce tooltip lines", tooltipLines.isEmpty());
		assertEquals("Air rune", tooltipLines.get(0));
		assertTrue("the tooltip breaks the total down per storage: " + tooltipLines,
			tooltipLines.contains("Inventory: 4000") && tooltipLines.contains("Rune pouch: 3000"));

		final Rectangle tooltipArea = new Rectangle(airRune.x + 10, airRune.y + 18, 60, 20);
		assertTrue("the self-drawn tooltip must be painted right next to the cursor",
			harness.nonBackgroundPixels(tooltipArea) > 0);

		harness.annotate("Self-drawn tooltip lines:", tooltipLines);
		shoot(harness, "07-tooltip-multi-storage");

		// ---- e. search, typed into the chatbox prompt ---------------------------------------
		harness.clickTab(0);
		harness.render(2);
		final int rowsBeforeSearch = harness.model().getRows().size();

		harness.clickSearchBox();
		harness.render();
		assertTrue("clicking the search box opens the chatbox prompt", harness.searchPromptOpened());
		assertTrue("and marks the box active while it is up", harness.model().isSearchFocused());

		harness.typeInSearch("rune");
		harness.render(2);

		assertEquals("rune", harness.model().getSearch());
		assertTrue("filtering must remove rows: " + rowsBeforeSearch + " -> " + harness.model().getRows().size(),
			harness.model().getRows().size() < rowsBeforeSearch);
		assertFalse("the filter must still match something", harness.model().getSlots().isEmpty());
		for (BankSlot slot : harness.model().getSlots())
		{
			assertTrue("unexpected slot in a 'rune' search: " + slot.getName(),
				slot.getName().toLowerCase(Locale.ROOT).contains("rune"));
		}
		shoot(harness, "08-search-rune");

		// Card 31: the search icon is what clears an active filter now.
		harness.clickSearchButton();
		harness.render(2);
		assertEquals("", harness.model().getSearch());

		// ---- f. by-storage mode, with headers ----------------------------------------------
		harness.controller().post(() -> harness.model().setMode(ViewMode.BY_STORAGE));
		harness.render(2);

		assertEquals(ViewMode.BY_STORAGE, harness.model().getMode());
		assertTrue("by-storage mode must emit header rows",
			harness.model().getRows().stream().anyMatch(r -> r.getKind() == io.robrichardson.banklessbank.ui.BankRow.Kind.HEADER));
		assertTrue("the death pile header must carry its expiry subtitle",
			harness.model().getRows().stream()
				.anyMatch(r -> "Expires in 42:17".equals(r.getHeaderSubtitle())));
		shoot(harness, "09-by-storage-headers");

		harness.controller().post(() -> harness.model().setMode(ViewMode.TABS));
		harness.render(2);

		// ---- j. scrolled to the bottom, thumb at the end ------------------------------------
		final Point insideGrid = harness.canvas(harness.model().gridRect().width / 2, harness.model().gridRect().y + 40);
		for (int i = 0; i < 30; i++)
		{
			harness.wheel(insideGrid.x, insideGrid.y, 1);
		}
		harness.render(2);

		final int maxScroll = harness.model().getMaxScroll();
		assertTrue(maxScroll > 0);
		assertEquals("the wheel must clamp at the bottom", maxScroll, harness.model().getScroll());

		final Rectangle track = harness.model().scrollTrackRect();
		final Rectangle thumb = harness.model().scrollThumbRect();
		assertTrue("the thumb must sit at the end of the track",
			Math.abs((track.y + track.height) - (thumb.y + thumb.height)) <= 1);
		shoot(harness, "10-scrolled-to-bottom");

		// ---- k. the same open view, but with interface sprites available ------------------
		// The other scenes all take the null-sprite path, which is what a test JVM without the game
		// cache can serve. This one hands the overlay stand-in sprites of the real ones' sizes, so
		// the 9-sliced frame, the tiled scrollbar dragger and the fitted icons are drawn too.
		final BankHarness sprited = new BankHarness(true);
		sprited.render();
		sprited.openViaHudButton();
		sprited.render(2);
		assertEquals(BankGeometry.size(BankGeometry.DEFAULT_COLS, BankGeometry.DEFAULT_ROWS), sprited.lastRenderedSize());
		shoot(sprited, "11-interface-sprites");

		System.out.println("Bankless Bank render harness wrote:");
		for (String path : WRITTEN)
		{
			System.out.println("  " + path);
		}
	}

	// =========================================================================================
	// Regression assertions, each on a fresh harness
	// =========================================================================================

	@Test
	public void windowSizeMatchesGeometry()
	{
		final BankHarness harness = openedHarness();
		final Dimension size = harness.render();

		assertEquals(BankGeometry.width(BankGeometry.DEFAULT_COLS), size.width);
		assertEquals(BankGeometry.height(BankGeometry.DEFAULT_ROWS), size.height);
		assertEquals(BankGeometry.size(BankGeometry.DEFAULT_COLS, BankGeometry.DEFAULT_ROWS), size);
	}

	@Test
	public void escapeClosesTheViewAndNothingIsDrawn()
	{
		final BankHarness harness = openedHarness();
		assertNotNull(harness.render());

		harness.keyPress(KeyEvent.VK_ESCAPE);
		harness.render();

		assertFalse(harness.controller().isOpen());
		assertNull("a closed overlay reports no size", harness.render());
	}

	@Test
	public void clickOutsideBelongsToTheGameUnlessDismissingAnOpenMenu()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point outside = new Point(BankHarness.CANVAS_W - 10, 10);
		assertFalse("the test point must be outside the window",
			new Rectangle(harness.origin(), harness.model().size()).contains(outside));

		// No menu open: every click outside the window belongs to the game.
		assertFalse("an outside click with nothing to dismiss must pass through",
			harness.press(outside.x, outside.y).isConsumed());
		harness.release(outside.x, outside.y);
		harness.render();

		// Open a context menu, then confirm alt-held clicks still pass through regardless of menu
		// state - RuneLite's own alt-drag must keep working no matter what we are doing.
		final Point slot = harness.centreOfSlot(0);
		harness.moveTo(slot);
		harness.rightPress(slot.x, slot.y);
		harness.render();
		assertTrue(harness.model().isMenuOpen());

		harness.setAltHeld(true);
		harness.render();
		assertFalse("alt held must stand aside even with a menu open",
			harness.press(outside.x, outside.y).isConsumed());
		harness.setAltHeld(false);
		harness.render();
		assertTrue("a menu must still be open; the alt-held press must not have dismissed it",
			harness.model().isMenuOpen());

		// Alt released, menu open: the outside click dismisses the menu and is eaten, as any menu's
		// outside click would be.
		assertTrue(harness.press(outside.x, outside.y).isConsumed());
		harness.render();
		assertFalse(harness.model().isMenuOpen());
	}

	/**
	 * Card 32: focus is now nothing more than "our chatbox prompt is the panel on show", so a click
	 * in the world neither steals it nor closes the prompt - the prompt owns its own lifecycle
	 * (Enter, Escape, or the game killing the panel).
	 */
	@Test
	public void clickingOutsideLeavesTheSearchPromptAlone()
	{
		final BankHarness harness = openedHarness();
		harness.clickSearchBox();
		harness.render();
		assertTrue(harness.model().isSearchFocused());

		final Point outside = new Point(BankHarness.CANVAS_W - 10, 10);
		harness.press(outside.x, outside.y);
		harness.render();

		assertTrue("a world click must not silently unfocus a prompt that is still open",
			harness.model().isSearchFocused());

		harness.closeSearchPrompt();
		harness.render(2);
		assertFalse("closing the prompt is what drops the focus", harness.model().isSearchFocused());
	}

	/** Live filtering: the prompt's onChanged fires per keystroke, so the grid narrows as you type. */
	@Test
	public void typingInTheChatboxPromptFiltersLive()
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(0);
		harness.render(2);
		final int rowsBefore = harness.model().getRows().size();

		harness.clickSearchBox();
		harness.render();
		harness.typeInSearch("rune");
		harness.render(2);

		assertEquals("rune", harness.model().getSearch());
		assertTrue("the filter must apply before Enter is ever pressed",
			harness.model().getRows().size() < rowsBefore);

		// Enter closes the panel and leaves the filter applied, exactly as the real bank does.
		harness.submitSearch("rune");
		harness.render(2);
		assertEquals("rune", harness.model().getSearch());
		assertFalse(harness.controller().isChatboxInputOpen());
		assertFalse(harness.model().isSearchFocused());
	}

	/** Raw keystrokes at our listener are never ours now, focused or not (card 32). */
	@Test
	public void rawTypingNeverReachesTheSearchFilter()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		harness.type("abc");
		harness.render();
		assertEquals("", harness.model().getSearch());

		harness.clickSearchBox();
		harness.render();
		harness.type("abc");
		harness.render();
		assertEquals("typing goes to the chatbox prompt, never to a field of ours",
			"", harness.model().getSearch());

		final KeyEvent backspace = harness.keyPress(KeyEvent.VK_BACK_SPACE);
		harness.render();
		assertFalse("backspace belongs to the game again", backspace.isConsumed());
	}

	@Test
	public void escapeWithAnOpenMenuClosesTheMenuNotTheWindow()
	{
		final BankHarness harness = openedHarness();
		final Point slot = harness.centreOfSlot(0);
		harness.moveTo(slot);
		harness.rightPress(slot.x, slot.y);
		harness.render();
		assertTrue(harness.model().isMenuOpen());

		harness.keyPress(KeyEvent.VK_ESCAPE);
		harness.render();

		assertFalse(harness.model().isMenuOpen());
		assertTrue("escape must close only the menu while one is open", harness.controller().isOpen());
	}

	/**
	 * Card 31: the search icon toggles. With a filter active it clears it (and the prompt with it);
	 * with nothing to clear it opens the prompt again.
	 */
	@Test
	public void searchIconTogglesTheFilter()
	{
		final BankHarness harness = openedHarness();
		harness.clickSearchButton();
		harness.render(2);
		assertEquals(1, harness.searchPromptOpenCount());

		harness.typeInSearch("rune");
		harness.render(2);
		assertEquals("rune", harness.model().getSearch());

		harness.clickSearchButton();
		harness.render(2);
		assertEquals("clicking the icon with a filter active clears it", "", harness.model().getSearch());
		assertFalse(harness.model().isSearchFocused());
		assertEquals("and does not re-open the prompt", 1, harness.searchPromptOpenCount());
		assertTrue("the open prompt is closed with it", harness.searchPromptClosedByUs());

		harness.clickSearchButton();
		harness.render(2);
		assertEquals("with nothing to clear it opens the prompt again", 2, harness.searchPromptOpenCount());
	}

	/** Board decision: Escape closes the window whenever it is open. The filter is the icon's job. */
	@Test
	public void escapeClosesTheWindowAndLeavesTheFilterApplied()
	{
		final BankHarness harness = openedHarness();
		harness.clickSearchBox();
		harness.render();
		harness.typeInSearch("rune");
		harness.submitSearch("rune");
		harness.render(2);
		assertEquals("rune", harness.model().getSearch());

		harness.keyPress(KeyEvent.VK_ESCAPE);
		harness.render(2);

		assertFalse(harness.controller().isOpen());
		assertEquals("rune", harness.model().getSearch());
	}

	@Test
	public void resizeGripDragChangesVisibleRowsInWholeRowsAndPersists() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.render();
		assertEquals(BankGeometry.DEFAULT_ROWS, harness.model().getVisibleRows());

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();

		final int down3Rows = grip.y + BankGeometry.SLOT_H * 3;
		harness.drag(grip.x, down3Rows);
		harness.render();
		shoot(harness, "13-resize-grip-drag");

		assertEquals(BankGeometry.DEFAULT_ROWS + 3, harness.model().getVisibleRows());
		assertEquals(BankGeometry.height(BankGeometry.DEFAULT_ROWS + 3), harness.lastRenderedSize().height);

		harness.release(grip.x, down3Rows);
		harness.render();

		assertEquals(BankGeometry.DEFAULT_ROWS + 3, harness.model().getVisibleRows());
	}

	@Test
	public void resizeGripDragClampsAtMinimumRows()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();

		final int wayUp = grip.y - BankGeometry.SLOT_H * 50;
		harness.drag(grip.x, wayUp);
		harness.render();

		assertEquals(BankGeometry.MIN_ROWS, harness.model().getVisibleRows());

		harness.release(grip.x, wayUp);
		harness.render();
		assertEquals(BankGeometry.MIN_ROWS, harness.model().getVisibleRows());
	}

	@Test
	public void resizeGripDragChangesVisibleColumnsInWholeColumns() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.render();
		assertEquals(BankGeometry.DEFAULT_COLS, harness.model().getVisibleCols());

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();

		final int right4Cols = grip.x + BankGeometry.SLOT_W * 4;
		harness.drag(right4Cols, grip.y);
		harness.render(2);

		assertEquals(BankGeometry.DEFAULT_COLS + 4, harness.model().getVisibleCols());
		assertEquals(BankGeometry.DEFAULT_ROWS, harness.model().getVisibleRows());
		assertEquals(BankGeometry.width(12), harness.lastRenderedSize().width);
		// The window is a viewport, so a 12-column window over an 8-wide tab draws all 12 cells of a
		// row inside the grid - the last four of them blank, past the tab's own width.
		for (int i = 0; i < 12; i++)
		{
			assertTrue("column " + i + " must be inside the widened grid",
				harness.model().gridRect().contains(harness.model().slotRect(i)));
		}
		shoot(harness, "17-twelve-columns");

		harness.release(right4Cols, grip.y);
		harness.render();
		assertEquals(BankGeometry.DEFAULT_COLS + 4, harness.model().getVisibleCols());
	}

	@Test
	public void resizeGripDragClampsAtMinimumColumnsAndKeepsTheWholeTabStripOnScreen() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();

		final int wayLeft = grip.x - BankGeometry.SLOT_W * 20;
		harness.drag(wayLeft, grip.y);
		harness.render(2);

		assertEquals(BankGeometry.MIN_COLS, harness.model().getVisibleCols());
		assertEquals(BankGeometry.width(BankGeometry.MIN_COLS), harness.lastRenderedSize().width);

		// The strip wraps onto extra rows (and, once the row cap binds, shrinks its buttons) rather
		// than pushing any of them off the right edge, so every tab and the [+] stay clickable at the
		// narrowest window.
		final Rectangle strip = harness.model().geometry().tabStrip();
		final int stripLength = harness.model().getStripLength();
		assertTrue("a narrow window must not widen the tab buttons past their natural size",
			harness.model().tabWidth() <= BankGeometry.TAB_W);
		for (int i = 0; i < stripLength; i++)
		{
			assertTrue("strip entry " + i + " must stay inside the strip",
				strip.contains(harness.model().tabRect(i)));
		}
		shoot(harness, "18-four-columns");

		harness.release(wayLeft, grip.y);
		harness.render();
		assertEquals(BankGeometry.MIN_COLS, harness.model().getVisibleCols());
	}

	@Test
	public void aResizeOnBothAxesPersistsItsColumnsAndRowsToConfig()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();

		final int x = grip.x + BankGeometry.SLOT_W * 2;
		final int y = grip.y + BankGeometry.SLOT_H * 2;
		harness.drag(x, y);
		harness.render(2);
		harness.release(x, y);
		harness.render(2);

		assertEquals(BankGeometry.DEFAULT_COLS + 2, harness.model().getVisibleCols());
		assertEquals(BankGeometry.DEFAULT_ROWS + 2, harness.model().getVisibleRows());
		assertEquals("10", harness.configValue("viewCols"));
		assertEquals("8", harness.configValue("viewRows"));
	}

	@Test
	public void wheelScrollsAndClampsAtBothEnds()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point inside = harness.canvas(harness.model().gridRect().width / 2, harness.model().gridRect().y + 40);

		harness.wheel(inside.x, inside.y, 1);
		harness.render();
		assertEquals(BankGeometry.SCROLL_STEP, harness.model().getScroll());

		for (int i = 0; i < 50; i++)
		{
			harness.wheel(inside.x, inside.y, 1);
		}
		harness.render();
		assertEquals(harness.model().getMaxScroll(), harness.model().getScroll());

		for (int i = 0; i < 60; i++)
		{
			harness.wheel(inside.x, inside.y, -1);
		}
		harness.render();
		assertEquals(0, harness.model().getScroll());
	}

	@Test
	public void dragFromSlotZeroToSlotThreeSwapsTheTwoItems()
	{
		final BankHarness harness = openedHarness();
		harness.render();
		harness.clickTab(1);
		harness.render(2);

		final List<Integer> before = new ArrayList<>(harness.layout().getMainTab().getSlots());
		final Point from = harness.centreOfSlot(0);
		final Point to = harness.centreOfSlot(3);

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(to.x, to.y);
		harness.render();
		harness.release(to.x, to.y);
		harness.render();

		final List<Integer> after = harness.layout().getMainTab().getSlots();
		assertEquals(before.size(), after.size());
		assertEquals(before.get(3), after.get(0));
		assertEquals(before.get(0), after.get(3));
		assertEquals(before.get(1), after.get(1));
		assertEquals(before.get(2), after.get(2));
	}

	@Test
	public void dragTabStripReordersCustomTabsWithADropIndicator() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.render();

		assertEquals(Arrays.asList("Main", "Gear", "Runes", "Seeds"), tabNames(harness));

		// Strip index 3 = layout tab 2 ("Runes"); strip index 4 = layout tab 3 ("Seeds").
		final Point from = centreOnCanvas(harness, harness.model().tabRect(3));
		final Point to = centreOnCanvas(harness, harness.model().tabRect(4));

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(to.x, to.y);
		harness.render();

		assertTrue("a tab drag must be live before release", harness.model().isTabDragging());
		assertEquals(3, harness.model().getTabDropIndex());
		shoot(harness, "12-tab-reorder-in-progress");

		harness.release(to.x, to.y);
		harness.render();

		assertFalse(harness.model().isTabDragging());
		assertEquals(Arrays.asList("Main", "Gear", "Seeds", "Runes"), tabNames(harness));
	}

	@Test
	public void dragMainTabToTheEndOfTheStripReordersItLikeAnyOtherTab() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.render();

		assertEquals(Arrays.asList("Main", "Gear", "Runes", "Seeds"), tabNames(harness));

		// Strip index 1 = Main; strip index 4 = layout tab 3 ("Seeds"), the last real tab.
		final Point from = centreOnCanvas(harness, harness.model().tabRect(1));
		final Point to = centreOnCanvas(harness, harness.model().tabRect(4));

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(to.x, to.y);
		harness.render();

		assertTrue("a tab drag must be live before release", harness.model().isTabDragging());
		assertEquals(0, harness.model().getTabDragFrom());
		shoot(harness, "19-main-tab-drag-in-progress");

		harness.release(to.x, to.y);
		harness.render();

		assertFalse(harness.model().isTabDragging());
		assertEquals(Arrays.asList("Gear", "Runes", "Seeds", "Main"), tabNames(harness));
		assertEquals(3, harness.layout().indexOfMainTab());
		assertTrue("Main is still the one flagged main tab, just relocated",
			harness.layout().getTab(3).isMain());
	}

	@Test
	public void dragASearchResultOntoATabButtonMovesItThere() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.render();

		// Strip: 0 = All, 1 = Main, 2 = Gear, 3 = Runes, 4 = Seeds.
		harness.clickTab(3);
		harness.render(2);

		harness.clickSearchBox();
		harness.render();
		harness.typeInSearch("rune");
		harness.render(2);

		assertFalse("the 'rune' search on the Runes tab must find something",
			harness.model().getSlots().isEmpty());
		final int itemId = harness.model().getSlots().get(0).getCanonicalId();
		assertTrue("the dragged item must start out in the Runes tab", harness.layout().getTab(2).contains(itemId));

		final Point from = harness.centreOfSlot(0);
		final Point gearTab = centreOnCanvas(harness, harness.model().tabRect(2));

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(gearTab.x, gearTab.y);
		harness.render();

		assertTrue("a drag from a search result must be live", harness.model().isDragging());
		shoot(harness, "20-search-drag-to-tab");

		harness.release(gearTab.x, gearTab.y);
		harness.render(2);

		assertFalse(harness.model().isDragging());
		// A search-result drag onto a tab MOVES the item out of its source tab.
		assertFalse("the item must no longer be in the Runes tab", harness.layout().getTab(2).contains(itemId));
		assertTrue("the item must now be in the Gear tab", harness.layout().getTab(1).contains(itemId));
		assertEquals("rune", harness.model().getSearch());
	}

	@Test
	public void setAsTabIconContextMenuEntryChangesTheIconWithoutMovingTheItem()
	{
		final BankHarness harness = openedHarness();
		harness.render();
		harness.clickTab(2); // "Gear", layout tab 1
		harness.render(2);

		assertEquals(FakeStorages.RUNE_PLATEBODY, harness.model().getTabIconItemId(1));

		final int targetFlatIndex = 1;
		final int targetItemId = harness.model().getSlots().get(targetFlatIndex).getCanonicalId();
		assertTrue("the target slot must not already be the icon", targetItemId != FakeStorages.RUNE_PLATEBODY);
		final List<Integer> beforeSlots = new ArrayList<>(harness.layout().getTabs().get(1).getSlots());

		final Point slot = harness.centreOfSlot(targetFlatIndex);
		harness.moveTo(slot);
		harness.rightPress(slot.x, slot.y);
		harness.render();
		assertTrue(harness.model().isMenuOpen());

		int entryIndex = -1;
		final List<ContextMenuEntry> entries = harness.model().getMenu().getEntries();
		for (int i = 0; i < entries.size(); i++)
		{
			if (entries.get(i).getAction() == MenuAction.SET_TAB_ICON)
			{
				entryIndex = i;
				break;
			}
		}
		assertTrue("a 'Set as tab icon' entry must be offered", entryIndex >= 0);

		final Rectangle entryRect = harness.rectOnCanvas(harness.model().getMenu().entryRect(entryIndex));
		harness.press(entryRect.x + entryRect.width / 2, entryRect.y + entryRect.height / 2);
		harness.render();

		assertFalse(harness.model().isMenuOpen());
		assertEquals(targetItemId, harness.model().getTabIconItemId(1));
		assertEquals("the item itself must not move", beforeSlots, harness.layout().getTabs().get(1).getSlots());
	}

	@Test
	public void collapseBlankSpacesContextMenuEntryPacksTheMainTabDown() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(1); // Main - the fixture's saved layout has a deliberate interior gap here.
		harness.render(2);
		assertEquals(0, harness.model().getActiveTab());

		final List<Integer> beforeSlots = new ArrayList<>(harness.layout().getMainTab().getSlots());
		assertTrue("the fixture must start with an interior gap on Main", beforeSlots.contains(null));
		final List<Integer> expectedIds = harness.layout().getMainTab().itemIds();

		final Point tab = centreOnCanvas(harness, harness.model().tabRect(1));
		harness.moveTo(tab);
		harness.rightPress(tab.x, tab.y);
		harness.render();
		assertTrue(harness.model().isMenuOpen());

		int entryIndex = -1;
		final List<ContextMenuEntry> entries = harness.model().getMenu().getEntries();
		for (int i = 0; i < entries.size(); i++)
		{
			if (entries.get(i).getAction() == MenuAction.COMPACT_TAB)
			{
				entryIndex = i;
				break;
			}
		}
		assertTrue("a 'Collapse blank spaces' entry must be offered on a tab button", entryIndex >= 0);

		final Rectangle entryRect = harness.rectOnCanvas(harness.model().getMenu().entryRect(entryIndex));
		shoot(harness, "21-collapse-blanks");
		harness.press(entryRect.x + entryRect.width / 2, entryRect.y + entryRect.height / 2);
		harness.render();

		assertFalse(harness.model().isMenuOpen());
		assertFalse("no interior gaps must remain", harness.layout().getMainTab().getSlots().contains(null));
		assertEquals("item order must be preserved", expectedIds, harness.layout().getMainTab().getSlots());
	}

	@Test
	public void renamingATabShowsUpInTheTitleAndTheAllViewDivider() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(2); // a custom tab, so the All-view divider is exercised too.
		harness.render(2);
		assertEquals(1, harness.model().getActiveTab());

		final Point tab = centreOnCanvas(harness, harness.model().tabRect(2));
		harness.moveTo(tab);
		harness.rightPress(tab.x, tab.y);
		harness.render();
		assertTrue(harness.model().isMenuOpen());

		int entryIndex = -1;
		final List<ContextMenuEntry> entries = harness.model().getMenu().getEntries();
		for (int i = 0; i < entries.size(); i++)
		{
			if (entries.get(i).getAction() == MenuAction.RENAME_TAB)
			{
				entryIndex = i;
				break;
			}
		}
		assertTrue("a 'Rename tab' entry must be offered on a tab button", entryIndex >= 0);

		final Rectangle entryRect = harness.rectOnCanvas(harness.model().getMenu().entryRect(entryIndex));
		harness.press(entryRect.x + entryRect.width / 2, entryRect.y + entryRect.height / 2);
		harness.render();
		assertFalse(harness.model().isMenuOpen());
		assertTrue("the chatbox text input must be open", harness.controller().isChatboxInputOpen());

		harness.submitTabRename("Ores");
		harness.render();

		assertFalse("submitting closes the input", harness.controller().isChatboxInputOpen());
		assertEquals("Ores", harness.layout().getTab(1).getName());
		assertEquals("the title must show the new name", "Ores", harness.model().titleBaseName());
		shoot(harness, "26-renamed-tab");

		harness.clickTab(0); // All view - the divider label must show the new name too.
		harness.render(2);
		final String dividerLabel = harness.model().getRows().stream()
			.filter(row -> row.getKind() == io.robrichardson.banklessbank.ui.BankRow.Kind.HEADER
				&& row.getTabIndex() == 1)
			.map(io.robrichardson.banklessbank.ui.BankRow::getHeaderText)
			.findFirst()
			.orElse(null);
		assertEquals("Ores", dividerLabel);

		// Renaming back to blank must reset to the default name for this tab's position ("Tab 2" for
		// strip index 2), not leave it empty and not restore whatever custom name it had before.
		harness.clickTab(2);
		harness.render(2);
		harness.rightPress(tab.x, tab.y);
		harness.render();
		harness.press(entryRect.x + entryRect.width / 2, entryRect.y + entryRect.height / 2);
		harness.render();
		harness.submitTabRename("   ");
		harness.render();

		assertEquals("Tab 2", harness.layout().getTab(1).getName());
	}

	@Test
	public void aWiderWindowShowsBlankColumnsAndADropOntoOneWidensTheTab() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(1); // Main, an 8-wide tab
		harness.render(2);
		assertEquals(BankTab.DEFAULT_COLS, harness.layout().getMainTab().getCols());

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();
		final int right4Cols = grip.x + BankGeometry.SLOT_W * 4;
		harness.drag(right4Cols, grip.y);
		harness.render(2);
		harness.release(right4Cols, grip.y);
		harness.render(2);

		assertEquals(12, harness.model().getVisibleCols());
		assertEquals("a resize must not touch the layout", BankTab.DEFAULT_COLS,
			harness.layout().getMainTab().getCols());
		assertEquals("nothing to scroll sideways when the window is the wider one",
			0, harness.model().getMaxHScroll());

		final List<BankSlot> firstRow = harness.model().getRows().stream()
			.filter(r -> r.getKind() == io.robrichardson.banklessbank.ui.BankRow.Kind.ITEMS)
			.findFirst().get().getSlots();
		assertEquals(12, firstRow.size());
		for (int c = 8; c < 12; c++)
		{
			assertTrue("column " + c + " must be a blank beyond-width cell",
				firstRow.get(c).isBeyondWidth());
		}
		shoot(harness, "22-wider-blank-columns");

		// Drag the first item onto the blank column 10 of row 0: the tab widens to 11 and everything
		// already placed keeps the (row, col) it was drawn at.
		final int itemId = harness.model().getSlots().get(0).getCanonicalId();
		final Integer rowOneStart = harness.layout().getMainTab().itemAt(BankTab.DEFAULT_COLS);
		final Point from = harness.centreOfSlot(0);
		final Point onto = harness.centreOfSlot(10);

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(onto.x, onto.y);
		harness.render();
		harness.release(onto.x, onto.y);
		harness.render(2);

		assertEquals("the tab widened to hold the dropped column", 11,
			harness.layout().getMainTab().getCols());
		assertEquals(Integer.valueOf(itemId), harness.layout().getMainTab().itemAt(10));
		assertEquals("the first item of row 1 is still the first item of row 1",
			rowOneStart, harness.layout().getMainTab().itemAt(11));
	}

	@Test
	public void aNarrowerWindowScrollsTheGridSidewaysInsteadOfRewrappingIt() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(1); // Main, an 8-wide tab
		harness.render(2);
		final List<Integer> before = new ArrayList<>(harness.layout().getMainTab().getSlots());

		final Point grip = harness.gripCentre();
		harness.moveTo(grip);
		harness.press(grip.x, grip.y);
		harness.render();
		final int left4Cols = grip.x - BankGeometry.SLOT_W * 4;
		harness.drag(left4Cols, grip.y);
		harness.render(2);
		harness.release(left4Cols, grip.y);
		harness.render(2);

		assertEquals(BankGeometry.MIN_COLS, harness.model().getVisibleCols());
		assertEquals("the arrangement itself must be untouched", before,
			harness.layout().getMainTab().getSlots());
		assertEquals("half the 8-wide tab is off-screen, so half of it can be scrolled to",
			BankGeometry.MIN_COLS * BankGeometry.SLOT_W, harness.model().getMaxHScroll());

		final Rectangle hbar = harness.rectOnCanvas(harness.model().hScrollbarRect());
		assertTrue("the horizontal scrollbar must be painted", harness.nonBackgroundPixels(hbar) > 0);
		assertTrue("it must show a thumb against its track", harness.distinctColours(hbar) >= 3);

		// Slot 4 is the first cell past a 4-column viewport: off-screen now, on-screen once scrolled.
		assertFalse("slot 4 starts out beyond the right edge", harness.model().isSlotVisible(4));

		final Point overGrid = harness.canvas(harness.model().gridRect().x + 10,
			harness.model().gridRect().y + 10);
		for (int i = 0; i < 8; i++)
		{
			harness.shiftWheel(overGrid.x, overGrid.y, 1);
		}
		harness.render(2);

		assertEquals("shift + wheel scrolls sideways and clamps at the end",
			harness.model().getMaxHScroll(), harness.model().getHScroll());
		assertTrue("the scrolled-to cell is now on screen", harness.model().isSlotVisible(4));
		shoot(harness, "23-narrower-hscroll");

		// A plain wheel still scrolls vertically, not sideways.
		final int sideways = harness.model().getHScroll();
		harness.wheel(overGrid.x, overGrid.y, 1);
		harness.render(2);
		assertEquals(sideways, harness.model().getHScroll());
	}

	@Test
	public void storageModeOnALayoutTabOnlyShowsThatTabsItemsGroupedByStorage() throws IOException
	{
		final BankHarness harness = openedHarness();

		// The fixture's "Runes" tab (strip index 3) holds five ids split across exactly two of the
		// ten fixture storages: Inventory (air/law/death/blood rune) and Rune pouch (cosmic rune) -
		// a clean way to show storage mode narrowed to one tab's items instead of everything owned.
		harness.clickTab(3);
		harness.render(2);
		assertEquals("Runes", 2, harness.model().getActiveTab());
		final List<Integer> runesTabIds = harness.layout().getTabs().get(2).itemIds();

		harness.controller().post(() -> harness.model().setMode(ViewMode.BY_STORAGE));
		harness.render(2);

		assertEquals(ViewMode.BY_STORAGE, harness.model().getMode());
		final List<io.robrichardson.banklessbank.ui.BankRow> headers = harness.model().getRows().stream()
			.filter(r -> r.getKind() == io.robrichardson.banklessbank.ui.BankRow.Kind.HEADER)
			.collect(java.util.stream.Collectors.toList());
		assertEquals("only the two storages holding a Runes-tab item must appear: " + headers, 2, headers.size());
		assertEquals("Inventory", headers.get(0).getHeaderText());
		assertEquals("Rune pouch", headers.get(1).getHeaderText());

		// Air rune and Law rune are tracked in both Inventory and Rune pouch, so the two headers'
		// slots outnumber the tab's five distinct ids - what matters is that no id outside the tab
		// leaks in, and every one of the tab's ids is represented at least once.
		final java.util.Set<Integer> seenIds = new java.util.LinkedHashSet<>();
		for (BankSlot slot : harness.model().getSlots())
		{
			assertTrue("every grouped slot must be one of the Runes tab's own items: " + slot.getName(),
				runesTabIds.contains(slot.getCanonicalId()));
			seenIds.add(slot.getCanonicalId());
		}
		assertEquals("every one of the tab's ids must be accounted for across the storage groups",
			new java.util.LinkedHashSet<>(runesTabIds), seenIds);

		shoot(harness, "24-storage-mode-scoped");

		// Switching tabs while still in storage mode refilters to the new tab.
		harness.controller().post(() -> harness.model().setActiveTab(-1));
		harness.render(2);
		final long allHeaderCount = harness.model().getRows().stream()
			.filter(r -> r.getKind() == io.robrichardson.banklessbank.ui.BankRow.Kind.HEADER).count();
		assertTrue("the All tab must show every storage again, not just the Runes tab's two",
			allHeaderCount > headers.size());
	}

	@Test
	public void dragAStorageModeItemOntoATabButtonCopiesItThere() throws IOException
	{
		final BankHarness harness = openedHarness();

		// Strip: 0 = All, 1 = Main, 2 = Gear, 3 = Runes, 4 = Seeds.
		harness.clickTab(3);
		harness.render(2);
		harness.controller().post(() -> harness.model().setMode(ViewMode.BY_STORAGE));
		harness.render(2);

		assertEquals(ViewMode.BY_STORAGE, harness.model().getMode());
		assertFalse("storage mode on the Runes tab must show something",
			harness.model().getSlots().isEmpty());
		final int itemId = harness.model().getSlots().get(0).getCanonicalId();
		assertTrue("the dragged item must start out in the Runes tab", harness.layout().getTab(2).contains(itemId));

		final Point from = harness.centreOfSlot(0);
		final Point gearTab = centreOnCanvas(harness, harness.model().tabRect(2));

		harness.moveTo(from);
		harness.press(from.x, from.y);
		harness.render();
		harness.drag(gearTab.x, gearTab.y);
		harness.render();

		assertTrue("a drag from a storage-mode row must be live", harness.model().isDragging());
		shoot(harness, "28-storage-mode-drag-to-tab");

		harness.release(gearTab.x, gearTab.y);
		harness.render(2);

		assertFalse(harness.model().isDragging());
		// Card 27: a storage-mode drag onto a tab COPIES too (both paths share endStripOnlyDrag).
		assertTrue("the item must still be in the Runes tab", harness.layout().getTab(2).contains(itemId));
		assertTrue("the item must now also be in the Gear tab", harness.layout().getTab(1).contains(itemId));
		assertEquals("the view must still be in storage mode", ViewMode.BY_STORAGE, harness.model().getMode());
		boolean stillInRows = false;
		for (BankSlot slot : harness.model().getSlots())
		{
			stillInRows |= slot.getCanonicalId() == itemId;
		}
		assertTrue("the copied item's original is still in the Runes-tab-scoped storage rows", stillInRows);
	}

	@Test
	public void copyingAnItemToAnotherTabShowsItInBothAndDimsOnlyTheDraggedCopy() throws IOException
	{
		// Card 27: the two-step "Copy to" menu, the resulting duplicate in the All view, and a
		// grid drag of just one copy (which must dim only the dragged cell - see BankOverlay's
		// drag-source comparison, retrofitted from an id comparison to a (tabIndex, indexInTab) one).
		final BankHarness harness = openedHarness();

		// Strip: 0 = All, 1 = Main, 2 = Gear, 3 = Runes, 4 = Seeds.
		harness.clickTab(2);
		harness.render(2);
		assertTrue("the Gear tab must start out holding the platebody",
			harness.layout().getTab(1).contains(FakeStorages.RUNE_PLATEBODY));

		// Step 1: right-click the platebody and activate "Copy to / another tab".
		final Point plateSlot = harness.centreOfSlot(0);
		harness.moveTo(plateSlot);
		harness.rightPress(plateSlot.x, plateSlot.y);
		harness.render();
		assertTrue(harness.model().isMenuOpen());

		int copyToIdx = -1;
		List<ContextMenuEntry> firstMenu = harness.model().getMenu().getEntries();
		for (int i = 0; i < firstMenu.size(); i++)
		{
			if (firstMenu.get(i).getAction() == MenuAction.COPY_TO_TAB_MENU)
			{
				copyToIdx = i;
				break;
			}
		}
		assertTrue("a 'Copy to' row must be offered: " + firstMenu, copyToIdx >= 0);
		Rectangle copyToRect = harness.rectOnCanvas(harness.model().getMenu().entryRect(copyToIdx));
		harness.press(copyToRect.x + copyToRect.width / 2, copyToRect.y + copyToRect.height / 2);
		harness.render();

		// Step 2: the target-tab chooser, reopened at the same anchor rather than a submenu.
		assertTrue("activating Copy to must open the step-2 menu, not close it outright",
			harness.model().isMenuOpen());
		List<ContextMenuEntry> secondMenu = harness.model().getMenu().getEntries();
		assertTrue("the step-2 menu must offer at least one real target plus Cancel", secondMenu.size() >= 2);
		assertEquals(MenuAction.COPY_TO_TAB, secondMenu.get(0).getAction());
		shoot(harness, "29-duplicate-item");

		final int targetTab = secondMenu.get(0).getArg();
		Rectangle targetRect = harness.rectOnCanvas(harness.model().getMenu().entryRect(0));
		harness.press(targetRect.x + targetRect.width / 2, targetRect.y + targetRect.height / 2);
		harness.render();
		assertFalse(harness.model().isMenuOpen());

		assertTrue("the original copy must stay in Gear",
			harness.layout().getTab(1).contains(FakeStorages.RUNE_PLATEBODY));
		assertTrue("the new copy must land in the chosen tab",
			harness.layout().getTab(targetTab).contains(FakeStorages.RUNE_PLATEBODY));

		// The All view shows one cell per copy, each under its own tab's divider.
		harness.controller().post(() -> harness.model().setActiveTab(-1));
		harness.render(2);
		long copies = harness.model().getSlots().stream()
			.filter(s -> s != null && !s.isEmpty() && s.getCanonicalId() == FakeStorages.RUNE_PLATEBODY)
			.count();
		assertEquals("both copies must render in the All view", 2, copies);

		// Dragging one copy dims only that cell, never its sibling copy elsewhere. Do this back on
		// the single-tab Gear view (guaranteed in the viewport, unlike a distant All-view row) -
		// the drag-source comparison the fix covers is per-cell, not dependent on what else is shown.
		harness.clickTab(2);
		harness.render(2);
		BankSlot gearCopy = null;
		for (BankSlot s : harness.model().getSlots())
		{
			if (s != null && !s.isEmpty() && s.getCanonicalId() == FakeStorages.RUNE_PLATEBODY)
			{
				gearCopy = s;
				break;
			}
		}
		assertNotNull("the Gear copy must still be present", gearCopy);
		final int dragIndex = harness.model().getSlots().indexOf(gearCopy);
		final Point dragFrom = harness.centreOfSlot(dragIndex);

		harness.moveTo(dragFrom);
		harness.press(dragFrom.x, dragFrom.y);
		harness.render();
		harness.drag(dragFrom.x + 40, dragFrom.y);
		harness.render();

		assertTrue("the drag must be live before release", harness.model().isDragging());
		assertEquals(1, harness.model().getDragSlot().getTabIndex());

		harness.release(dragFrom.x + 40, dragFrom.y);
		harness.render();
		assertFalse(harness.model().isDragging());
	}

	@Test
	public void allTabDividersShowATabIconAndSeparatorPerNonEmptyTab() throws IOException
	{
		final BankHarness harness = openedHarness();
		assertEquals("the default open view is the All tab", -1, harness.model().getActiveTab());

		final List<io.robrichardson.banklessbank.ui.BankRow> dividers = harness.model().getRows().stream()
			.filter(r -> r.getKind() == io.robrichardson.banklessbank.ui.BankRow.Kind.HEADER)
			.collect(java.util.stream.Collectors.toList());
		assertTrue("the fixture's four tabs must all produce a divider: " + dividers.size(), dividers.size() >= 4);

		final Rectangle grid = harness.rectOnCanvas(harness.model().gridRect());
		final io.robrichardson.banklessbank.ui.BankRow first = dividers.get(0);
		final Rectangle band = new Rectangle(grid.x, grid.y + first.getY() - harness.model().getScroll(),
			grid.width, first.getHeight());
		assertTrue("the divider band must be painted", harness.nonBackgroundPixels(band) > band.width);

		final Rectangle iconArea = new Rectangle(band.x + 2, band.y + 2,
			BankGeometry.DIVIDER_ICON_W - 4, band.height - 4);
		assertTrue("the divider's icon area must hold a drawn sprite, not just the band fill",
			harness.distinctColours(iconArea) >= 3);

		shoot(harness, "14-all-tab-dividers");
	}

	@Test
	public void titleShowsTheGeValueOfTheActiveTabAndFollowsItAcrossTabs() throws IOException
	{
		final BankHarness harness = openedHarness();
		assertTrue("the All tab's total must be non-zero with the fixture's priced items",
			harness.model().titleValue() > 0);
		assertEquals(harness.model().tabValue(-1), harness.model().titleValue());

		harness.clickTab(1); // Main
		harness.render(2);
		assertEquals(0, harness.model().getActiveTab());
		assertEquals("the title value must follow the active tab", harness.model().tabValue(0),
			harness.model().titleValue());

		final Rectangle title = harness.rectOnCanvas(harness.model().titleBarRect());
		assertTrue("the title bar text must be painted", harness.nonBackgroundPixels(title) > title.width);

		shoot(harness, "15-ge-title-value");

		// Counting title-coloured pixels, not just non-background ones: the whole bar sits on the
		// panel, so a plain non-background count never changes. Switching the config item off must
		// shorten the painted string, which is the only proof from here that the value really
		// reaches the title rather than only the view model.
		final int glyphsWithValue = harness.pixelsOfColour(title, JagexColors.DARK_ORANGE_INTERFACE_TEXT);
		harness.setShowValue(false);
		harness.render(2);
		final int glyphsWithout = harness.pixelsOfColour(title, JagexColors.DARK_ORANGE_INTERFACE_TEXT);
		assertTrue("switching off the GE value must paint a shorter title (" + glyphsWithValue
			+ " -> " + glyphsWithout + ")", glyphsWithout < glyphsWithValue);

		harness.setShowValue(true);
		harness.render(2);
	}

	@Test
	public void placeholderIgnoreHidesAPotionDoseChainOnlyWhileUnowned() throws IOException
	{
		// The reported scenario: a potion drunk down to its last dose. Sanfew serum(4)/(3)/(2)
		// are the spent doses, now unowned placeholders; Sanfew serum(1) is the dose still held.
		final BankHarness harness = openedHarness();
		harness.clickTab(1); // Main
		harness.render(2);
		assertEquals(0, harness.model().getActiveTab());

		final int dose4 = flatIndexOf(harness, FakeStorages.SANFEW_SERUM_4);
		final int dose3 = flatIndexOf(harness, FakeStorages.SANFEW_SERUM_3);
		final int dose2 = flatIndexOf(harness, FakeStorages.SANFEW_SERUM_2);
		final int dose1 = flatIndexOf(harness, FakeStorages.SANFEW_SERUM_1);
		assertTrue("the three higher doses must start as placeholders",
			harness.model().getSlots().get(dose4).isPlaceholder()
				&& harness.model().getSlots().get(dose3).isPlaceholder()
				&& harness.model().getSlots().get(dose2).isPlaceholder());
		assertFalse("the last dose must be owned, not a placeholder",
			harness.model().getSlots().get(dose1).isPlaceholder());

		clickMenuEntry(harness, dose4, MenuAction.IGNORE_PLACEHOLDER);
		clickMenuEntry(harness, dose3, MenuAction.IGNORE_PLACEHOLDER);
		clickMenuEntry(harness, dose2, MenuAction.IGNORE_PLACEHOLDER);
		harness.render(2);

		assertTrue("an ignored placeholder renders as an empty cell",
			harness.model().getSlots().get(dose4).isEmpty()
				&& harness.model().getSlots().get(dose3).isEmpty()
				&& harness.model().getSlots().get(dose2).isEmpty());
		assertTrue("the owned last dose keeps showing normally",
			harness.model().getSlots().get(dose1).getCanonicalId() == FakeStorages.SANFEW_SERUM_1);
		assertEquals("the slot stays reserved, so the layout keeps the id in place",
			Integer.valueOf(FakeStorages.SANFEW_SERUM_4), harness.layout().getMainTab().getSlots().get(dose4));
		shoot(harness, "16-placeholder-ignore-list");

		// Re-acquiring an ignored id must put it straight back in its reserved slot, visible again -
		// a hidden cell offers no right-click menu (rightClickOnAnEmptyCellOpensNoMenu), so this is
		// the only way back to "Show placeholder again" for an id that is currently unowned.
		harness.fixture().markOwned(FakeStorages.SANFEW_SERUM_4);
		harness.markStoragesDirty();
		harness.render(2);
		assertFalse("owning an ignored id again must show it, not hide it",
			harness.model().getSlots().get(dose4).isEmpty());
		assertEquals(FakeStorages.SANFEW_SERUM_4, harness.model().getSlots().get(dose4).getCanonicalId());

		// Now that it is visible again, the unignore entry is reachable and undoes the ignore.
		clickMenuEntry(harness, dose4, MenuAction.UNIGNORE_PLACEHOLDER);
		harness.render(2);
		assertFalse(harness.layout().isPlaceholderIgnored(FakeStorages.SANFEW_SERUM_4));

		// The other two doses are still hidden and unowned, with no way to click them - this is
		// what the sidebar's "clear ignored placeholders" action is for.
		assertTrue(harness.model().getSlots().get(dose3).isEmpty());
		// Mirrors what BanklessBankPanel's "clear ignored placeholders" button does: mutate the
		// layout, then invalidate so the next rebuild (rebuild() short-circuits unless dirty) picks
		// it up.
		harness.layout().clearPlaceholderIgnores();
		harness.model().invalidate();
		harness.render(2);
		assertFalse("clearing the ignore list must bring every hidden placeholder back",
			harness.model().getSlots().get(dose3).isEmpty());
		assertTrue(harness.model().getSlots().get(dose3).isPlaceholder());
	}

	/** Flat index into {@code model().getSlots()} of the cell currently showing {@code itemId}. */
	private static int flatIndexOf(BankHarness harness, int itemId)
	{
		final List<BankSlot> slots = harness.model().getSlots();
		for (int i = 0; i < slots.size(); i++)
		{
			if (slots.get(i).getCanonicalId() == itemId)
			{
				return i;
			}
		}
		throw new AssertionError("no slot showing item " + itemId);
	}

	/** Right-clicks the given flat slot and clicks the first menu entry with the given action. */
	private static void clickMenuEntry(BankHarness harness, int flatIndex, MenuAction action)
	{
		final Point cell = harness.centreOfSlot(flatIndex);
		harness.moveTo(cell);
		harness.rightPress(cell.x, cell.y);
		harness.render();
		assertTrue("right-click must open the context menu", harness.model().isMenuOpen());

		final List<ContextMenuEntry> entries = harness.model().getMenu().getEntries();
		int entryIndex = -1;
		for (int i = 0; i < entries.size(); i++)
		{
			if (entries.get(i).getAction() == action)
			{
				entryIndex = i;
				break;
			}
		}
		assertTrue("a " + action + " entry must be offered: " + entries, entryIndex >= 0);

		final Rectangle entryRect = harness.rectOnCanvas(harness.model().getMenu().entryRect(entryIndex));
		harness.press(entryRect.x + entryRect.width / 2, entryRect.y + entryRect.height / 2);
		harness.render();
		assertFalse(harness.model().isMenuOpen());
	}

	private static List<String> tabNames(BankHarness harness)
	{
		final List<String> names = new ArrayList<>();
		for (BankTab tab : harness.layout().getTabs())
		{
			names.add(tab.getName());
		}
		return names;
	}

	@Test
	public void scrollbarArrowButtonsScrollOneRowEachWay()
	{
		final BankHarness harness = openedHarness();
		harness.render();
		assertTrue("the fixture must overflow the viewport", harness.model().getMaxScroll() > 0);

		harness.click(centreOnCanvas(harness, harness.model().scrollDownRect()));
		harness.render(2);
		assertEquals(BankGeometry.SCROLL_STEP, harness.model().getScroll());

		harness.click(centreOnCanvas(harness, harness.model().scrollUpRect()));
		harness.render(2);
		assertEquals(0, harness.model().getScroll());
	}

	@Test
	public void bottomBarModeButtonTogglesBetweenTabsAndByStorage()
	{
		final BankHarness harness = openedHarness();
		harness.render();
		assertEquals(ViewMode.TABS, harness.model().getMode());

		harness.click(centreOnCanvas(harness, harness.model().modeButtonRect()));
		harness.render(2);
		assertEquals(ViewMode.BY_STORAGE, harness.model().getMode());

		harness.click(centreOnCanvas(harness, harness.model().modeButtonRect()));
		harness.render(2);
		assertEquals(ViewMode.TABS, harness.model().getMode());
	}

	@Test
	public void bottomBarSearchButtonOpensTheChatboxPrompt()
	{
		final BankHarness harness = openedHarness();
		harness.render();
		assertFalse(harness.model().isSearchFocused());

		harness.click(centreOnCanvas(harness, harness.model().searchButtonRect()));
		harness.render(2);
		assertTrue("the icon opens RuneLite's chatbox text input", harness.searchPromptOpened());
		assertTrue(harness.controller().isChatboxInputOpen());
		assertTrue(harness.model().isSearchFocused());

		harness.typeInSearch("rune");
		harness.render(2);
		assertEquals("rune", harness.model().getSearch());
	}

	@Test
	public void bottomBarAddButtonOpensTheItemSearchAndAppendsThePickToTheActiveTab() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(1); // Main
		harness.render(2);

		final Rectangle button = harness.rectOnCanvas(harness.model().addButtonRect());
		assertTrue("the add button must be painted, not an empty patch of bar",
			harness.nonBackgroundPixels(button) > button.width);
		assertEquals("the add button must be hittable where it is drawn",
			io.robrichardson.banklessbank.ui.Hit.Type.ADD_BUTTON,
			harness.model().hitTest(harness.model().addButtonRect().x + 2,
				harness.model().addButtonRect().y + 2).getType());

		harness.clickAddButton();
		harness.render(2);
		assertTrue("clicking it must open the game's item search", harness.controller().isChatboxInputOpen());
		assertFalse("and must not leave our own search field focused", harness.model().isSearchFocused());

		final int before = harness.layout().getMainTab().getSlots().size();
		harness.pickInItemSearch(FakeStorages.MANUAL_ADD_ITEM);
		harness.render(2);

		assertFalse("the search closes itself after a pick", harness.controller().isChatboxInputOpen());
		assertEquals("the pick lands at the end of the active tab", Integer.valueOf(FakeStorages.MANUAL_ADD_ITEM),
			harness.layout().getMainTab().getSlots().get(before));

		final int flat = flatIndexOf(harness, FakeStorages.MANUAL_ADD_ITEM);
		assertTrue("an unowned manual add renders as a placeholder",
			harness.model().getSlots().get(flat).isPlaceholder());
		shoot(harness, "25-manual-add-placeholder");

		// Picking it a second time must not duplicate it; the view just jumps to where it already is.
		harness.clickAddButton();
		harness.render(2);
		harness.pickInItemSearch(FakeStorages.MANUAL_ADD_ITEM);
		harness.render(2);
		assertEquals(1, occurrences(harness, FakeStorages.MANUAL_ADD_ITEM));
	}

	@Test
	public void whileTheItemSearchIsOpenOurWindowLeavesEveryKeyAlone()
	{
		final BankHarness harness = openedHarness();
		harness.clickAddButton();
		harness.render(2);
		assertTrue(harness.controller().isChatboxInputOpen());

		// Escape belongs to the chatbox search here: unconsumed, and our window stays open.
		final KeyEvent escape = harness.keyPress(KeyEvent.VK_ESCAPE);
		harness.render(2);
		assertFalse("Escape must reach the chatbox search unconsumed", escape.isConsumed());
		assertTrue("our window stays open behind the search", harness.controller().isOpen());

		harness.type("abyssal");
		harness.render(2);
		assertEquals("typing must go to the chatbox search, not our search box",
			"", harness.model().getSearch());
	}

	private static int occurrences(BankHarness harness, int itemId)
	{
		int n = 0;
		for (BankTab tab : harness.layout().getTabs())
		{
			for (Integer id : tab.getSlots())
			{
				if (id != null && id == itemId)
				{
					n++;
				}
			}
		}
		return n;
	}

	@Test
	public void chromeRendersWithAndWithoutInterfaceSprites()
	{
		// The window must paint the same rect either way: sprites when the cache can serve them,
		// flat fallbacks when it cannot. Only the pixels inside differ.
		final BankHarness fallback = openedHarness();
		fallback.render();

		final BankHarness sprited = new BankHarness(true);
		sprited.render();
		sprited.openViaHudButton();
		sprited.render(2);

		assertEquals(fallback.lastRenderedSize(), sprited.lastRenderedSize());

		final Rectangle frame = new Rectangle(fallback.origin().x, fallback.origin().y,
			fallback.model().size().width, BankGeometry.BORDER);
		assertTrue("the frame band must be painted without sprites",
			fallback.nonBackgroundPixels(frame) > frame.width);
		assertTrue("the frame band must be painted with sprites",
			sprited.nonBackgroundPixels(frame) > frame.width);
	}

	@Test
	public void contextMenuIsDrawnLikeTheGamesChooseOptionMenu() throws IOException
	{
		final BankHarness harness = openedHarness();
		harness.clickTab(1); // Main
		harness.render(2);

		// Right-click an owned item, then park the cursor on the first row so its hover bar shows.
		final Point slot = harness.centreOfSlot(0);
		harness.moveTo(slot);
		harness.rightPress(slot.x, slot.y);
		harness.render();
		assertTrue("right-clicking a slot opens the menu", harness.model().isMenuOpen());

		final ContextMenu menu = harness.model().getMenu();
		final List<ContextMenuEntry> entries = menu.getEntries();
		assertTrue("the item's menu must offer a row with an orange target",
			entries.get(0).hasTarget());
		assertEquals("Cancel", entries.get(entries.size() - 1).getLabel());

		final Rectangle box = harness.rectOnCanvas(menu.getBounds());
		final Rectangle firstRow = harness.rectOnCanvas(menu.entryRect(0));
		harness.moveTo(firstRow.x + firstRow.width / 2, firstRow.y + firstRow.height / 2);
		harness.render();

		// The game's chrome: a 1px 0x5D5447 frame, a black header band, a grey hover bar on the
		// row under the cursor, and the target half of that row in JagexColors.MENU_TARGET.
		final Color frameColour = new Color(0x5D, 0x54, 0x47);
		assertTrue("the menu must have a 0x5D5447 frame along its top edge",
			harness.pixelsOfColour(new Rectangle(box.x, box.y, box.width, 1), frameColour) > box.width / 2);
		assertTrue("the header band must be black behind its title",
			harness.pixelsOfColour(new Rectangle(box.x + 1, box.y + 1, box.width - 2, 16), Color.BLACK)
				> (box.width - 2) * 8);
		assertTrue("the header title must be drawn in the frame's own colour",
			harness.pixelsOfColour(new Rectangle(box.x + 1, box.y + 1, box.width - 2, 16), frameColour) > 0);
		assertTrue("the hovered row must be filled with the game's grey bar",
			harness.pixelsOfColour(firstRow, new Color(0x80, 0x80, 0x80)) > firstRow.width);
		assertTrue("the target half of a row must be drawn in the game's item orange",
			harness.pixelsOfColour(firstRow, new Color(0xFF, 0x90, 0x40)) > 0);

		shoot(harness, "27-context-menu");

		harness.keyPress(KeyEvent.VK_ESCAPE);
		harness.render();
		assertFalse(harness.model().isMenuOpen());
	}

	private static Point centreOnCanvas(BankHarness harness, Rectangle local)
	{
		final Rectangle r = harness.rectOnCanvas(local);
		return new Point(r.x + r.width / 2, r.y + r.height / 2);
	}

	@Test
	public void twentyTabsWrapTheStripOntoASecondRowAndStayClickable() throws IOException
	{
		// Card 34: the tab cap is BankLayout.MAX_TABS, well past the real bank's nine, so the strip
		// wraps onto extra rows and the window grows downwards instead of clipping tabs away.
		final BankHarness harness = openedHarness();
		final int oneRowHeight = harness.render().height;
		assertEquals(1, harness.model().geometry().getStripRows());

		int icon = 1000;
		while (harness.layout().getTabs().size() < BankLayout.MAX_TABS)
		{
			harness.layout().createTabWith(icon++);
		}
		harness.render(2);

		assertEquals(BankLayout.MAX_TABS, harness.layout().getTabs().size());
		// All + 20 tabs, and no [+] because the cap is reached.
		assertEquals(BankLayout.MAX_TABS + 1, harness.model().getStripLength());
		assertEquals(2, harness.model().geometry().getStripRows());

		final Dimension size = harness.render();
		assertEquals("the window grows by exactly one strip row",
			oneRowHeight + BankGeometry.TAB_STRIP_H, size.height);
		assertEquals(BankGeometry.width(BankGeometry.DEFAULT_COLS), size.width);

		final Rectangle strip = harness.model().geometry().tabStrip();
		for (int i = 0; i < harness.model().getStripLength(); i++)
		{
			assertTrue("strip entry " + i + " must stay inside the wrapped strip",
				strip.contains(harness.model().tabRect(i)));
		}

		// A tab on the second row is clickable through the real input path, not just hit-testable.
		final int secondRow = harness.model().geometry().tabsPerRow(harness.model().getStripLength());
		assertTrue(harness.model().tabRect(secondRow).y > harness.model().tabRect(0).y);
		harness.clickTab(secondRow);
		harness.render(2);
		assertEquals("clicking a wrapped tab must select it", secondRow - 1, harness.model().getActiveTab());

		shoot(harness, "30-twenty-tabs");
	}

	private static BankHarness openedHarness()
	{
		final BankHarness harness = new BankHarness();
		harness.render();
		harness.openViaHudButton();
		harness.render(2);
		return harness;
	}
}
