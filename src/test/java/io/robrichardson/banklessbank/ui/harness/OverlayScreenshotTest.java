package io.robrichardson.banklessbank.ui.harness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.robrichardson.banklessbank.ui.BankGeometry;
import io.robrichardson.banklessbank.ui.BankSlot;
import io.robrichardson.banklessbank.ui.ContextMenuEntry;
import io.robrichardson.banklessbank.ui.ViewMode;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
	private static final Path SCRATCH_DIR = Paths.get(
		"/private/tmp/claude-501/-Users-robrichardson--nib-repos-bankless-bank-init",
		"dd570016-fe25-427e-9fb8-180db2859bee", "scratchpad", "screenshots");

	private static final List<Path> OUT_DIRS = Arrays.asList(BUILD_DIR, SCRATCH_DIR);

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
		assertEquals(BankGeometry.size(BankGeometry.DEFAULT_ROWS), size);
		assertEquals(-1, harness.model().getActiveTab());
		assertEquals(0, harness.model().getScroll());
		assertTrue("more content than fits, so there is something to scroll",
			harness.model().getMaxScroll() > 0);

		final Rectangle firstSlot = harness.slotRectOnCanvas(0);
		assertTrue("the first slot must be painted over the game background",
			harness.nonBackgroundPixels(firstSlot) > 0);
		assertTrue("the first slot must contain a drawn sprite, not just chrome",
			harness.distinctColours(firstSlot) >= 5);

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
		assertEquals(3, harness.model().getDropCaretIndex());
		shoot(harness, "04-mid-drag-caret-and-ghost");

		harness.release(to.x, to.y);
		harness.render();
		assertFalse(harness.model().isDragging());

		final List<Integer> afterDrag = harness.layout().getMainTab().getSlots();
		assertFalse("dropping onto another slot must reorder the tab", beforeDrag.equals(afterDrag));
		assertEquals(beforeDrag.get(1), afterDrag.get(0));
		assertEquals(beforeDrag.get(0), afterDrag.get(2));

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
		shoot(harness, "06-custom-tab-gear");

		// ---- i. tooltip for an item held in two storages ------------------------------------
		harness.clickTab(3);
		harness.render(2);
		assertEquals(2, harness.model().getActiveTab());

		harness.clearTooltips();
		final Point airRune = harness.centreOfSlot(0);
		harness.moveTo(airRune);
		harness.render();

		assertFalse("hovering a slot must produce a tooltip", harness.tooltips().isEmpty());
		final String tooltip = harness.tooltips().get(0).getText();
		final List<String> tooltipLines = Arrays.asList(tooltip.split("</br>"));
		assertEquals("Air rune", tooltipLines.get(0));
		assertTrue("the tooltip breaks the total down per storage: " + tooltip,
			tooltipLines.contains("Inventory: 4000") && tooltipLines.contains("Rune pouch: 3000"));

		harness.annotate("TooltipManager received:", tooltipLines);
		shoot(harness, "07-tooltip-multi-storage");

		// ---- e. search, typed through the input listener ------------------------------------
		harness.clickTab(0);
		harness.render(2);
		final int rowsBeforeSearch = harness.model().getRows().size();

		harness.clickSearchBox();
		harness.render();
		assertTrue("clicking the search box focuses it", harness.model().isSearchFocused());

		harness.type("rune");
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

		harness.keyPress(KeyEvent.VK_ESCAPE);
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
		final Point insideGrid = harness.canvas(BankGeometry.GRID_W / 2, BankGeometry.grid(6).y + 40);
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
		assertEquals(BankGeometry.size(BankGeometry.DEFAULT_ROWS), sprited.lastRenderedSize());
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

		assertEquals(BankGeometry.WIDTH, size.width);
		assertEquals(BankGeometry.height(BankGeometry.DEFAULT_ROWS), size.height);
		assertEquals(BankGeometry.size(BankGeometry.DEFAULT_ROWS), size);
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
	public void clickOutsideIsConsumedUnlessAltIsHeld()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point outside = new Point(BankHarness.CANVAS_W - 10, 10);
		assertFalse("the test point must be outside the window",
			new Rectangle(harness.origin(), harness.model().size()).contains(outside));

		// Alt held: RuneLite's own alt-drag must keep working, so we stand aside.
		harness.setAltHeld(true);
		harness.render();
		assertFalse(harness.press(outside.x, outside.y).isConsumed());

		// Alt released: the click is ours, to close any open context menu.
		harness.setAltHeld(false);
		harness.render();
		assertTrue(harness.press(outside.x, outside.y).isConsumed());
	}

	@Test
	public void wheelScrollsAndClampsAtBothEnds()
	{
		final BankHarness harness = openedHarness();
		harness.render();

		final Point inside = harness.canvas(BankGeometry.GRID_W / 2, BankGeometry.grid(6).y + 40);

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
	public void dragFromSlotZeroToSlotThreeReordersTheLayout()
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
		assertEquals(before.get(1), after.get(0));
		assertEquals(before.get(2), after.get(1));
		assertEquals(before.get(0), after.get(2));
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
	public void bottomBarSearchButtonFocusesTheSearchField()
	{
		final BankHarness harness = openedHarness();
		harness.render();
		assertFalse(harness.model().isSearchFocused());

		harness.click(centreOnCanvas(harness, harness.model().searchButtonRect()));
		harness.render(2);
		assertTrue(harness.model().isSearchFocused());

		harness.type("rune");
		harness.render(2);
		assertEquals("rune", harness.model().getSearch());
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
			BankGeometry.WIDTH, BankGeometry.BORDER);
		assertTrue("the frame band must be painted without sprites",
			fallback.nonBackgroundPixels(frame) > frame.width);
		assertTrue("the frame band must be painted with sprites",
			sprited.nonBackgroundPixels(frame) > frame.width);
	}

	private static Point centreOnCanvas(BankHarness harness, Rectangle local)
	{
		final Rectangle r = harness.rectOnCanvas(local);
		return new Point(r.x + r.width / 2, r.y + r.height / 2);
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
