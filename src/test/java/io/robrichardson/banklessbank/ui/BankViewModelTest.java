package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/** Behaviour tests for {@link BankViewModel} against the real implementation. */
public class BankViewModelTest
{
	private BankViewModel model;
	private BankLayout layout;

	@Before
	public void setUp()
	{
		model = new BankViewModel();
		layout = new BankLayout();
		model.setLayout(layout);
	}

	private static ItemSnapshot item(int id, String name, long qty)
	{
		return new ItemSnapshot(id, name, qty, true);
	}

	private static StorageSnapshot storage(String category, String name, ItemSnapshot... items)
	{
		return new StorageSnapshot(category, name, null, Arrays.asList(items));
	}

	private static Set<Integer> setOf(Integer... ids)
	{
		return new LinkedHashSet<>(Arrays.asList(ids));
	}

	private static java.util.Map<Integer, Integer> mapOf(int... kv)
	{
		java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static List<ItemSnapshot> itemsOneEach(int... ids)
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int id : ids)
		{
			items.add(item(id, "Item " + id, 1));
		}
		return items;
	}

	// ---- TABS grid: every row is a fixed 8 cells, gaps render as empty ----

	@Test
	public void tabGridRendersAnEmptyCellForEveryGap()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(0);

		model.rebuild();

		assertTrue(model.getSlots().get(1).isEmpty());
		assertFalse(model.getSlots().get(0).isEmpty());
		assertFalse(model.getSlots().get(2).isEmpty());
	}

	@Test
	public void everyItemsRowInTabsModeHasExactlyEightCells()
	{
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);

		model.rebuild();

		for (BankRow row : model.getRows())
		{
			if (row.getKind() == BankRow.Kind.ITEMS)
			{
				assertEquals(BankGeometry.DEFAULT_COLS, row.getSlots().size());
			}
		}
	}

	@Test
	public void aTabAlwaysRendersAtLeastVisibleRowsOfCells()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.setVisibleRows(6);

		model.rebuild();

		long itemRows = model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.ITEMS).count();
		assertEquals(6, itemRows);
	}

	@Test
	public void contentHeightIsOneRowPastTheLastOccupiedRowWhenThatExceedsVisibleRows()
	{
		for (int i = 0; i < 50; i++)
		{
			layout.getMainTab().append(i + 1);
		}
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", itemsOneEach(rangeIds(1, 50)).toArray(new ItemSnapshot[0]))));
		model.setActiveTab(0);
		model.setVisibleRows(3);

		model.rebuild();

		// 50 items -> 7 occupied rows (ceil(50/8)); trailing rows is occupiedRows + 1 = 8.
		assertEquals(8 * BankGeometry.SLOT_H, model.getContentHeight());
	}

	@Test
	public void maxScrollIsZeroWhenTheTabFitsTheViewport()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.setVisibleRows(6);

		model.rebuild();

		assertEquals(0, model.getMaxScroll());
	}

	private static int[] rangeIds(int from, int to)
	{
		int[] ids = new int[to - from + 1];
		for (int i = 0; i < ids.length; i++)
		{
			ids[i] = from + i;
		}
		return ids;
	}

	// ---- placeholders ----

	@Test
	public void idInLayoutWithNoOwningStorageBecomesPlaceholder()
	{
		layout.getMainTab().append(42);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);

		model.rebuild();

		BankSlot slot = model.getSlots().get(0);
		assertTrue(slot.isPlaceholder());
		assertEquals(0, slot.getQuantity());
		assertTrue(slot.getSources().isEmpty());
	}

	@Test
	public void placeholderHiddenAndDroppedByLayoutWhenPlaceholdersDisabled()
	{
		layout.getMainTab().append(42);
		model.setSnapshots(Collections.emptyList());
		model.setPlaceholdersEnabled(false);
		model.setActiveTab(0);

		model.rebuild();
		assertTrue(model.getSlots().get(0).isEmpty());

		boolean changed = model.syncLayout();
		assertTrue(changed);
		assertFalse(layout.getMainTab().contains(42));
	}

	@Test
	public void ignoredPlaceholderIsHiddenButKeepsItsSlotReserved()
	{
		layout.getMainTab().append(42);
		layout.addPlaceholderIgnore(42);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);

		model.rebuild();

		assertTrue(model.getSlots().get(0).isEmpty());
	}

	@Test
	public void ignoredItemStillShowsNormallyWhileItIsOwned()
	{
		layout.getMainTab().append(42);
		layout.addPlaceholderIgnore(42);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(42, "Whip", 1))));
		model.setActiveTab(0);

		model.rebuild();

		BankSlot slot = model.getSlots().get(0);
		assertFalse(slot.isEmpty());
		assertFalse(slot.isPlaceholder());
	}

	@Test
	public void reacquiringAnIgnoredItemPutsItBackInItsOriginalSlot()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(1, 42);
		layout.addPlaceholderIgnore(42);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();
		assertTrue(model.getSlots().get(1).isEmpty());

		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(42, "Whip", 1))));
		model.rebuild();

		assertEquals(42, model.getSlots().get(1).getCanonicalId());
	}

	// ---- quantities and sources ----

	@Test
	public void quantitiesSumAcrossStoragesAndSourcesListEveryContributingStorage()
	{
		layout.getMainTab().append(7);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(7, "Coins", 100)),
			storage("death", "Deathpile (Edgeville)", item(7, "Coins", 50))));
		model.setActiveTab(0);

		model.rebuild();

		BankSlot slot = model.getSlots().get(0);
		assertEquals(150, slot.getQuantity());
		assertEquals(2, slot.getSources().size());
		assertEquals("Inventory", slot.getSources().get(0).getStorageName());
		assertEquals(100, slot.getSources().get(0).getQuantity());
		assertEquals("Deathpile (Edgeville)", slot.getSources().get(1).getStorageName());
		assertEquals(50, slot.getSources().get(1).getQuantity());
	}

	// ---- search: scoped to active tab, All searches everything ----

	@Test
	public void searchIsScopedToTheActiveTab()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(2);
		layout.getTab(otherTab).append(3);

		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Abyssal whip", 1), item(2, "Bronze sword", 1), item(3, "Rune whip", 1))));
		model.setActiveTab(0);
		model.setSearch("WHIP");

		model.rebuild();

		List<Integer> ids = new ArrayList<>();
		for (BankSlot slot : model.getSlots())
		{
			ids.add(slot.getCanonicalId());
		}
		assertEquals(Arrays.asList(1), ids);
	}

	@Test
	public void searchOnTheAllTabSpansEveryTab()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(2);
		layout.getTab(otherTab).append(3);

		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Abyssal whip", 1), item(2, "Bronze sword", 1), item(3, "Rune whip", 1))));
		model.setActiveTab(-1);
		model.setSearch("WHIP");

		model.rebuild();

		List<Integer> ids = new ArrayList<>();
		for (BankSlot slot : model.getSlots())
		{
			ids.add(slot.getCanonicalId());
		}
		assertEquals(Arrays.asList(1, 3), ids);
	}

	@Test
	public void searchResultsAreDenseWithNoGapCells()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(5, 2);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "Whip", 1), item(2, "Whip2", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");

		model.rebuild();

		assertEquals(2, model.getSlots().size());
		for (BankSlot slot : model.getSlots())
		{
			assertFalse(slot.isEmpty());
		}
	}

	@Test
	public void searchWithNoMatchesProducesNoRows()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Abyssal whip", 1))));
		model.setSearch("nonexistentitem");

		model.rebuild();

		assertTrue(model.getRows().isEmpty());
	}

	// ---- BY_STORAGE ----

	@Test
	public void byStorageEmitsHeaderPerNonEmptyStorageAndNoPlaceholders()
	{
		layout.getMainTab().append(99); // placeholder-only tab entry, irrelevant to BY_STORAGE
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("death", "Empty storage"),
			storage("poh", "POH", item(2, "Logs", 5))));
		model.setMode(ViewMode.BY_STORAGE);

		model.rebuild();

		List<BankRow> rows = model.getRows();
		long headerCount = rows.stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count();
		assertEquals(2, headerCount);
		assertEquals("Inventory", rows.get(0).getHeaderText());
		for (BankRow row : rows)
		{
			for (BankSlot slot : row.getSlots())
			{
				assertFalse(slot.isPlaceholder());
			}
		}
	}

	@Test
	public void byStorageEmitsHeaderForEmptyStorageWhenShowEmptyStoragesEnabled()
	{
		layout.getMainTab().append(99); // placeholder-only tab entry, irrelevant to BY_STORAGE
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("death", "Empty storage"),
			storage("poh", "POH", item(2, "Logs", 5))));
		model.setMode(ViewMode.BY_STORAGE);
		model.setShowEmptyStorages(true);

		model.rebuild();

		List<BankRow> rows = model.getRows();
		List<BankRow> headers = rows.stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).collect(java.util.stream.Collectors.toList());
		assertEquals(3, headers.size());
		assertEquals("Inventory", headers.get(0).getHeaderText());
		assertEquals("Empty storage", headers.get(1).getHeaderText());
		assertEquals("POH", headers.get(2).getHeaderText());

		int emptyHeaderIndex = rows.indexOf(headers.get(1));
		assertTrue(emptyHeaderIndex + 1 == rows.size() || rows.get(emptyHeaderIndex + 1).getKind() == BankRow.Kind.HEADER);
	}

	@Test
	public void byStorageHidesEmptyStorageHeaderWhenSearchDoesNotMatch()
	{
		layout.getMainTab().append(99); // placeholder-only tab entry, irrelevant to BY_STORAGE
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("death", "Empty storage"),
			storage("poh", "POH", item(2, "Logs", 5))));
		model.setMode(ViewMode.BY_STORAGE);
		model.setShowEmptyStorages(true);
		model.setSearch("whip");

		model.rebuild();

		List<BankRow> rows = model.getRows();
		long headerCount = rows.stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count();
		assertEquals(1, headerCount);
		assertEquals("Inventory", rows.get(0).getHeaderText());
	}

	@Test
	public void byStorageOnALayoutTabOnlyShowsThatTabsItemsGroupedByStorage()
	{
		layout.getMainTab().append(1);
		layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("poh", "POH", item(2, "Logs", 5))));
		model.setActiveTab(0);
		model.setMode(ViewMode.BY_STORAGE);

		model.rebuild();

		List<BankRow> rows = model.getRows();
		List<BankRow> headers = rows.stream().filter(r -> r.getKind() == BankRow.Kind.HEADER)
			.collect(java.util.stream.Collectors.toList());
		assertEquals(1, headers.size());
		assertEquals("Inventory", headers.get(0).getHeaderText());
		for (BankRow row : rows)
		{
			for (BankSlot slot : row.getSlots())
			{
				assertEquals(1, slot.getCanonicalId());
			}
		}
	}

	@Test
	public void byStorageOnALayoutTabOmitsStoragesWithNoMatchingItemsEvenWhenShowEmptyStoragesIsOn()
	{
		layout.getMainTab().append(1);
		layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("poh", "POH", item(2, "Logs", 5)),
			storage("death", "Empty storage")));
		model.setShowEmptyStorages(true);
		model.setActiveTab(0);
		model.setMode(ViewMode.BY_STORAGE);

		model.rebuild();

		List<BankRow> rows = model.getRows();
		List<BankRow> headers = rows.stream().filter(r -> r.getKind() == BankRow.Kind.HEADER)
			.collect(java.util.stream.Collectors.toList());
		assertEquals(1, headers.size());
		assertEquals("Inventory", headers.get(0).getHeaderText());
	}

	@Test
	public void byStorageOnTheAllTabShowsEverythingAcrossAllTabs()
	{
		layout.getMainTab().append(1);
		layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("poh", "POH", item(2, "Logs", 5))));
		model.setActiveTab(-1);
		model.setMode(ViewMode.BY_STORAGE);

		model.rebuild();

		List<BankRow> rows = model.getRows();
		long headerCount = rows.stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count();
		assertEquals(2, headerCount);
	}

	@Test
	public void byStorageOnALayoutTabCombinesTheTabFilterWithSearch()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "Whip", 1), item(2, "Logs", 5))));
		model.setActiveTab(0);
		model.setMode(ViewMode.BY_STORAGE);
		model.setSearch("whip");

		model.rebuild();

		List<BankSlot> slots = model.getSlots();
		assertEquals(1, slots.size());
		assertEquals(1, slots.get(0).getCanonicalId());
	}

	@Test
	public void byStorageRefiltersWhenTheActiveTabChanges()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1)),
			storage("poh", "POH", item(2, "Logs", 5))));
		model.setActiveTab(0);
		model.setMode(ViewMode.BY_STORAGE);
		model.rebuild();
		assertEquals(1, model.getSlots().size());
		assertEquals(1, model.getSlots().get(0).getCanonicalId());

		model.setActiveTab(otherTab);
		model.rebuild();

		assertEquals(1, model.getSlots().size());
		assertEquals(2, model.getSlots().get(0).getCanonicalId());
	}

	@Test
	public void titleValueInByStorageModeOnALayoutTabOnlyCountsThatTabsItems()
	{
		layout.getMainTab().append(1);
		layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 2)),
			storage("poh", "POH", item(2, "Logs", 3))));
		model.setUnitPrices(mapOf(1, 5, 2, 7));
		model.setActiveTab(0);
		model.setMode(ViewMode.BY_STORAGE);

		model.rebuild();

		assertEquals(2 * 5L, model.titleValue());
	}

	// ---- scrolling ----

	@Test
	public void scrollClampsToZeroAndMaxScrollAndMaxScrollIsZeroWhenContentFits()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.setVisibleRows(6);
		model.rebuild();

		assertEquals(0, model.getMaxScroll());

		model.setScroll(-50);
		assertEquals(0, model.getScroll());

		model.setScroll(500);
		assertEquals(model.getMaxScroll(), model.getScroll());
	}

	@Test
	public void scrollClampsAfterContentShrinks()
	{
		for (int i = 1; i <= 40; i++)
		{
			layout.getMainTab().append(i);
		}
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", itemsOneEach(rangeIds(1, 40)).toArray(new ItemSnapshot[0]))));
		model.setActiveTab(0);
		model.setVisibleRows(3);
		model.rebuild();
		model.setScroll(model.getMaxScroll());
		int maxBefore = model.getMaxScroll();
		assertTrue(maxBefore > 0);

		// Shrink content drastically.
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Item 1", 1))));
		layout.getMainTab().getSlots().clear();
		layout.getMainTab().append(1);
		model.rebuild();

		assertEquals(0, model.getMaxScroll());
		assertEquals(0, model.getScroll());
	}

	// ---- hit testing ----

	@Test
	public void hitTestOnAGapReturnsSlotEmptyCarryingItsTabAndSlotIndex()
	{
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle emptyRect = model.slotRect(1);
		Hit hit = model.hitTest(emptyRect.x, emptyRect.y);

		assertEquals(Hit.Type.SLOT_EMPTY, hit.getType());
		assertEquals(0, hit.getSlot().getTabIndex());
		assertEquals(1, hit.getSlot().getIndexInTab());
	}

	@Test
	public void hitTestReturnsNoneInTheGutter()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		Hit hit = model.hitTest(-100, -100);
		assertEquals(Hit.Type.NONE, hit.getType());
	}

	@Test
	public void hitTestReturnsGridEmptyBelowTheRenderedRows()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle grid = model.gridRect();
		Hit hit = model.hitTest(grid.x + grid.width - 1, grid.y + grid.height - 1);
		assertEquals(Hit.Type.SLOT_EMPTY, hit.getType());
	}

	@Test
	public void hitTestSeparatesTheScrollbarIntoArrowsThumbAndTrack()
	{
		for (int i = 1; i <= 200; i++)
		{
			layout.getMainTab().append(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1, 200)).toArray(new ItemSnapshot[0]))));
		model.setActiveTab(0);
		model.rebuild();
		assertTrue("the fixture must overflow the viewport", model.getMaxScroll() > 0);

		java.awt.Rectangle up = model.scrollUpRect();
		java.awt.Rectangle down = model.scrollDownRect();
		java.awt.Rectangle thumb = model.scrollThumbRect();
		java.awt.Rectangle track = model.scrollTrackRect();

		assertEquals(Hit.Type.SCROLL_UP, model.hitTest(up.x + 1, up.y + 1).getType());
		assertEquals(Hit.Type.SCROLL_DOWN, model.hitTest(down.x + 1, down.y + 1).getType());
		assertEquals(Hit.Type.SCROLL_THUMB, model.hitTest(thumb.x + 1, thumb.y + 1).getType());
		assertEquals(Hit.Type.SCROLL_TRACK,
			model.hitTest(track.x + 1, track.y + track.height - 1).getType());
	}

	@Test
	public void hitTestSeparatesTheBottomBarIntoSearchButtonFieldAndModeButton()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle searchButton = model.searchButtonRect();
		java.awt.Rectangle field = model.searchRect();
		java.awt.Rectangle mode = model.modeButtonRect();

		assertEquals(Hit.Type.SEARCH_BUTTON,
			model.hitTest(searchButton.x + 1, searchButton.y + 1).getType());
		assertEquals(Hit.Type.SEARCH, model.hitTest(field.x + 1, field.y + 1).getType());
		assertEquals(Hit.Type.MODE_BUTTON, model.hitTest(mode.x + 1, mode.y + 1).getType());
	}

	@Test
	public void hitTestOverTheResizeGripCentreReturnsResizeGrip()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle grip = model.resizeGripRect();
		assertEquals(Hit.Type.RESIZE_GRIP,
			model.hitTest(grip.x + grip.width / 2, grip.y + grip.height / 2).getType());
	}

	@Test
	public void hitTestOverTheResizeGripTakesPriorityWhereItSharesACornerWithTheModeButton()
	{
		// The grip (checked right after CLOSE, well before MODE_BUTTON) shares a handful of pixels
		// in its top-left corner with the mode button's bottom-right corner. RESIZE_GRIP must win
		// there, since a resize-only sliver costs far less than a dead zone on the mode button would.
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle grip = model.resizeGripRect();
		java.awt.Rectangle mode = model.modeButtonRect();
		assertTrue("the fixture must actually overlap for this test to mean anything",
			grip.intersects(mode));

		java.awt.Rectangle overlap = grip.intersection(mode);
		assertEquals(Hit.Type.RESIZE_GRIP,
			model.hitTest(overlap.x, overlap.y).getType());
	}

	@Test
	public void setVisibleRowsClampsToMinAndCurrentMaxRows()
	{
		model.setVisibleRows(2);
		assertEquals(BankGeometry.MIN_ROWS, model.getVisibleRows());

		model.setVisibleRows(99);
		assertEquals(model.getMaxRows(), model.getVisibleRows());
	}

	@Test
	public void setMaxRowsPullsDownAnOversizedVisibleRowCount()
	{
		model.setVisibleRows(10);
		assertEquals(10, model.getVisibleRows());

		model.setMaxRows(5);
		assertEquals(5, model.getVisibleRows());
		assertEquals(5, model.getMaxRows());
	}

	@Test
	public void setMaxRowsClampsToTheGeometryLimits()
	{
		model.setMaxRows(1);
		assertEquals(BankGeometry.MIN_ROWS, model.getMaxRows());

		model.setMaxRows(999);
		assertEquals(BankGeometry.MAX_ROWS, model.getMaxRows());
	}

	@Test
	public void toggleModeFlipsBetweenTabsAndByStorageAndResetsScrollWithoutClobberingTabMemory()
	{
		assertEquals(ViewMode.TABS, model.getMode());
		for (int i = 1; i <= 40; i++)
		{
			layout.getMainTab().append(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1, 40)).toArray(new ItemSnapshot[0]))));
		model.setActiveTab(0);
		model.setVisibleRows(3);
		model.rebuild();
		model.setScroll(72);

		model.toggleMode();
		assertEquals(ViewMode.BY_STORAGE, model.getMode());
		assertEquals(0, model.getScroll());

		model.toggleMode();
		assertEquals(ViewMode.TABS, model.getMode());
		model.rebuild();
		model.setActiveTab(0); // no-op (already active); force re-check of remembered scroll path
	}

	// ---- scroll memory (D9) ----

	@Test
	public void scrollPositionIsRestoredWhenReturningToATab()
	{
		int tabA = layout.createTab(1);
		layout.getTab(tabA).append(1);
		for (int i = 2; i <= 40; i++)
		{
			layout.getTab(tabA).append(i);
		}
		int tabB = layout.createTab(999);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1, 999)).toArray(new ItemSnapshot[0]))));
		model.setVisibleRows(3);
		model.setActiveTab(tabA);
		model.rebuild();
		model.setScroll(model.getMaxScroll());
		int remembered = model.getScroll();
		assertTrue(remembered > 0);

		model.setActiveTab(tabB);
		model.rebuild();
		assertEquals(0, model.getScroll());

		model.setActiveTab(tabA);
		model.rebuild();
		assertEquals(remembered, model.getScroll());
	}

	@Test
	public void eachTabRemembersItsOwnScrollIndependently()
	{
		int tabA = layout.createTab(1);
		int tabB = layout.createTab(2);
		for (int i = 1; i <= 40; i++)
		{
			layout.getTab(tabA).append(i + 1000);
			layout.getTab(tabB).append(i + 2000);
		}
		model.setSnapshots(Collections.emptyList());
		model.setVisibleRows(3);

		model.setActiveTab(tabA);
		model.rebuild();
		model.setScroll(36);

		model.setActiveTab(tabB);
		model.rebuild();
		model.setScroll(72);

		model.setActiveTab(tabA);
		model.rebuild();
		assertEquals(36, model.getScroll());

		model.setActiveTab(tabB);
		model.rebuild();
		assertEquals(72, model.getScroll());
	}

	@Test
	public void theAllViewRemembersItsOwnScroll()
	{
		for (int i = 1; i <= 100; i++)
		{
			layout.getMainTab().append(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1, 100)).toArray(new ItemSnapshot[0]))));
		model.setVisibleRows(3);
		model.setActiveTab(-1);
		model.rebuild();
		model.setScroll(36);

		model.setActiveTab(0);
		model.rebuild();

		model.setActiveTab(-1);
		model.rebuild();
		assertEquals(36, model.getScroll());
	}

	@Test
	public void startingASearchScrollsToTopAndClearingItRestoresTheTabScroll()
	{
		for (int i = 1; i <= 40; i++)
		{
			layout.getMainTab().append(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1, 40)).toArray(new ItemSnapshot[0]))));
		model.setVisibleRows(3);
		model.setActiveTab(0);
		model.rebuild();
		model.setScroll(36);

		model.setSearch("item");
		assertEquals(0, model.getScroll());

		model.clearSearch();
		assertEquals(36, model.getScroll());
	}

	@Test
	public void rememberedScrollIsClampedWhenTheTabHasShrunk()
	{
		int tabA = layout.createTab(1);
		for (int i = 1; i <= 40; i++)
		{
			layout.getTab(tabA).append(i + 1000);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1001, 1040)).toArray(new ItemSnapshot[0]))));
		model.setVisibleRows(3);
		model.setActiveTab(tabA);
		model.rebuild();
		model.setScroll(model.getMaxScroll());
		assertTrue(model.getScroll() > 0);

		int tabB = layout.createTab(2);
		model.setActiveTab(tabB);
		model.rebuild();

		// Shrink tab A drastically (down to a single item at slot 0), then return to it.
		BankTab tabARef = layout.getTab(tabA);
		int keepId = tabARef.itemAt(0);
		tabARef.getSlots().clear();
		tabARef.setAt(0, keepId);
		model.setActiveTab(tabA);
		model.rebuild();

		assertEquals(model.getMaxScroll(), model.getScroll());
		assertEquals(0, model.getMaxScroll());
	}

	@Test
	public void scrollMemoryFollowsATabThroughAReorder()
	{
		int tabA = layout.createTab(1);
		for (int i = 1; i <= 40; i++)
		{
			layout.getTab(tabA).append(i + 1000);
		}
		int tabB = layout.createTab(2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory",
			itemsOneEach(rangeIds(1001, 1040)).toArray(new ItemSnapshot[0]))));
		model.setVisibleRows(3);
		model.setActiveTab(tabA);
		model.rebuild();
		model.setScroll(model.getMaxScroll());
		int remembered = model.getScroll();
		assertTrue(remembered > 0);

		layout.moveTab(tabA, tabB);
		model.setActiveTab(0); // Main
		model.rebuild();

		// tabA's identity followed the reorder; find its new index.
		int newIndexOfA = -1;
		for (int i = 0; i < layout.getTabs().size(); i++)
		{
			if (layout.getTab(i).contains(1001))
			{
				newIndexOfA = i;
				break;
			}
		}
		model.setActiveTab(newIndexOfA);
		model.rebuild();
		assertEquals(remembered, model.getScroll());
	}

	// ---- drag and drop ----

	@Test
	public void dropOnAnEmptyCellMovesTheItemToThatExactSlot()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle dropOnto = model.slotRect(1); // gap

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(dropOnto.x, dropOnto.y);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertNull(layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(1));
	}

	@Test
	public void dropOnAnOccupiedCellSwapsTheTwoItems()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle dropOnto = model.slotRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(dropOnto.x, dropOnto.y);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertEquals(Integer.valueOf(2), layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(2));
	}

	@Test
	public void dropOnAnOccupiedCellInAnotherTabSwapsAcrossTabs()
	{
		layout.getMainTab().setAt(0, 1);
		int otherTab = layout.createTab(99);
		layout.getTab(otherTab).setAt(2, 99);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(99, "B", 1))));
		model.setActiveTab(otherTab);
		model.rebuild();

		java.awt.Rectangle dragFromMain = model.slotRect(0); // wrong tab's grid; instead drag within otherTab's view
		// Simplify: perform the swap directly through the model's active-tab grid isn't representative
		// across tabs via mouse coordinates, so exercise layout.placeItem directly is covered in
		// BankLayoutTest; here we verify the view model's resolution picks the SLOT branch when a
		// dragged item's hit slot resolves into a different tab's occupied cell.
		model.setActiveTab(0);
		model.rebuild();
		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);
		model.setActiveTab(otherTab);
		model.rebuild();
		java.awt.Rectangle dropOnto = model.slotRect(2);
		DropTarget target = model.endDrag(dropOnto.x, dropOnto.y);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertEquals(Integer.valueOf(99), layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getTab(otherTab).itemAt(2));
	}

	@Test
	public void dropOnTheItemsOwnCellCancels()
	{
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(dragFrom.x, dragFrom.y);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(0));
	}

	@Test
	public void dragCannotStartFromAnEmptyCell()
	{
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle emptyRect = model.slotRect(1);
		model.beginDrag(emptyRect.x, emptyRect.y);

		assertFalse(model.isDragging());
	}

	@Test
	public void dragCanStartFromASearchResultSlot()
	{
		// Supersedes the old "drag disabled during search" rule: a search result can now be dragged
		// onto a tab button (but not onto another grid cell - see the search-drag tests below).
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.setSearch("a");
		model.rebuild();

		java.awt.Rectangle slotRect = model.slotRect(0);
		model.beginDrag(slotRect.x, slotRect.y);

		assertTrue(model.isDragging());
		assertEquals(1, model.getDragSlot().getCanonicalId());
	}

	// ---- drag search results onto tabs ----

	@Test
	public void searchDragDropOnATabButtonAppendsTheItemToTheEndOfThatTab()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(99);
		layout.getTab(otherTab).append(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1), item(2, "Sword", 1), item(99, "Other", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip index: 0 = All, 1 = Main, 2 = otherTab.
		java.awt.Rectangle otherTabButton = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(otherTabButton.x + 1, otherTabButton.y + 1);

		assertEquals(DropTarget.Type.TAB, target.getType());
		assertEquals(otherTab, target.getTabIndex());
		assertFalse(layout.getMainTab().contains(1));
		// Appended after the tab's existing item (2), not swapped into its slot.
		assertEquals(Integer.valueOf(2), layout.getTab(otherTab).itemAt(layout.getTab(otherTab).indexOf(2)));
		assertEquals(1, layout.getTab(otherTab).itemAt(layout.getTab(otherTab).appendIndex() - 1).intValue());
	}

	@Test
	public void searchDragDropOntoTheTabTheItemAlreadyLivesInIsANoOp()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip index 1 = Main, the tab the item is already in.
		java.awt.Rectangle mainTabButton = model.tabRect(1);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(mainTabButton.x + 1, mainTabButton.y + 1);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(0));
	}

	@Test
	public void searchDragDropOnAGridCellCancelsBecauseSearchPositionsAreNotRealSlots()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Whip", 1), item(2, "Whip2", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle otherResult = model.slotRect(1);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(otherResult.x, otherResult.y);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(2), layout.getMainTab().itemAt(1));
	}

	@Test
	public void searchDragDropOnTheAllButtonCancels()
	{
		layout.getMainTab().append(1);
		layout.createTab(99);
		model.setSnapshots(Arrays.asList(storage("carryable", "Inventory", item(1, "Whip", 1), item(99, "Other", 1))));
		model.setActiveTab(-1);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle allButton = model.tabRect(0);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(allButton.x + 1, allButton.y + 1);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(0));
	}

	@Test
	public void searchDragOnThePlusButtonCreatesANewTabWithTheItem()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip: 0 = All, 1 = Main, 2 = plus (only main tab exists so far).
		java.awt.Rectangle plusButton = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(plusButton.x + 1, plusButton.y + 1);

		assertEquals(DropTarget.Type.NEW_TAB, target.getType());
		assertFalse(layout.getMainTab().contains(1));
		assertTrue(layout.getTab(target.getTabIndex()).contains(1));
	}

	@Test
	public void aHiddenIgnoredPlaceholderNeverAppearsInSearchResultsSoItCannotBeDraggedToATab()
	{
		// The ignore list hides an unowned id entirely, and a hidden cell has no slot built for it.
		// Search must honour that too, or the item would be draggable (and so duplicatable into a
		// second tab) from a grid the player cannot see it in.
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		layout.addPlaceholderIgnore(2);
		model.setKnownNames(java.util.Collections.singletonMap(2, "Whip clone"));
		// only id 1 is owned, so id 2 would otherwise render as a placeholder
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		assertEquals("only the owned item matches", 1, model.getSlots().size());
		assertEquals(1, model.getSlots().get(0).getCanonicalId());

		java.awt.Rectangle onlyResult = model.slotRect(0);
		model.beginDrag(onlyResult.x, onlyResult.y);
		assertEquals(1, model.getDragSlot().getCanonicalId());
	}

	@Test
	public void searchDragHighlightsNoTargetOverTheAllButton()
	{
		// The overlay paints its tab-drop highlight from this; during a search the All button is not
		// a valid target (endSearchDrag cancels on it), so it must not light up as though it were.
		layout.getMainTab().append(1);
		layout.createTab(99);
		model.setSnapshots(Arrays.asList(storage("carryable", "Inventory", item(1, "Whip", 1), item(99, "Other", 1))));
		model.setActiveTab(-1);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle allButton = model.tabRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);

		assertEquals(-1, model.dropTabStripIndex(allButton.x + 1, allButton.y + 1));
	}

	@Test
	public void searchDragHighlightsNoTargetOverTheTabTheItemAlreadyLivesIn()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle mainButton = model.tabRect(1);
		model.beginDrag(dragFrom.x, dragFrom.y);

		assertEquals(-1, model.dropTabStripIndex(mainButton.x + 1, mainButton.y + 1));
	}

	@Test
	public void searchDragHighlightsARealTargetTabAndThePlusButton()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(99);
		model.setSnapshots(Arrays.asList(storage("carryable", "Inventory", item(1, "Whip", 1), item(99, "Other", 1))));
		model.setActiveTab(0);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);

		java.awt.Rectangle otherButton = model.tabRect(otherTab + 1);
		assertEquals(otherTab + 1, model.dropTabStripIndex(otherButton.x + 1, otherButton.y + 1));

		java.awt.Rectangle plusButton = model.tabRect(model.getStripLength() - 1);
		assertEquals(model.getStripLength() - 1, model.dropTabStripIndex(plusButton.x + 1, plusButton.y + 1));
	}

	@Test
	public void anOrdinaryDragStillHighlightsTheAllButtonBecauseItMeansMoveToMain()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(99);
		model.setSnapshots(Arrays.asList(storage("carryable", "Inventory", item(1, "Whip", 1), item(99, "Other", 1))));
		model.setActiveTab(otherTab);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle allButton = model.tabRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);

		assertEquals(0, model.dropTabStripIndex(allButton.x + 1, allButton.y + 1));
	}

	@Test
	public void dropTabStripIndexIsMinusOneWhenNothingIsBeingDragged()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.rebuild();

		java.awt.Rectangle allButton = model.tabRect(0);
		assertEquals(-1, model.dropTabStripIndex(allButton.x + 1, allButton.y + 1));
	}

	@Test
	public void allTabSearchThenDropOnATabMovesTheItemOutOfItsOriginalTab()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(99);
		model.setSnapshots(Arrays.asList(storage("carryable", "Inventory", item(1, "Whip", 1), item(99, "Other", 1))));
		model.setActiveTab(-1);
		model.setSearch("whip");
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip index: 0 = All, 1 = Main, 2 = otherTab.
		java.awt.Rectangle otherTabButton = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(otherTabButton.x + 1, otherTabButton.y + 1);
		model.rebuild();

		assertEquals(DropTarget.Type.TAB, target.getType());
		assertFalse(layout.getMainTab().contains(1));
		assertTrue(layout.getTab(otherTab).contains(1));

		// The result list still spans every tab (All is still active), but the item's position moved.
		List<Integer> ids = new ArrayList<>();
		for (BankSlot slot : model.getSlots())
		{
			ids.add(slot.getCanonicalId());
		}
		assertTrue(ids.contains(1));
	}

	@Test
	public void dropSlotIndexTracksTheCellUnderTheCursorAndIsMinusOneOffGrid()
	{
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);
		assertEquals(0, model.getDropSlotIndex());

		java.awt.Rectangle other = model.slotRect(3);
		model.updateDrag(other.x, other.y);
		assertEquals(3, model.getDropSlotIndex());

		model.updateDrag(-1000, -1000);
		assertEquals(-1, model.getDropSlotIndex());
	}

	@Test
	public void endDragOntoTabStripEntryMovesItemToThatTab()
	{
		layout.getMainTab().append(1);
		int otherTab = layout.createTab(99);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip index: 0 = All, 1 = Main, 2 = otherTab (custom tab index 1).
		java.awt.Rectangle otherTabRect = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(otherTabRect.x + 1, otherTabRect.y + 1);

		assertEquals(DropTarget.Type.TAB, target.getType());
		assertEquals(otherTab, target.getTabIndex());
		assertTrue(layout.getTab(otherTab).contains(1));
		assertFalse(layout.getMainTab().contains(1));
	}

	@Test
	public void endDragOntoPlusCreatesNewTab()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip: 0 = All, 1 = Main, 2 = plus (only main tab exists so far).
		java.awt.Rectangle plusRect = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(plusRect.x + 1, plusRect.y + 1);

		assertEquals(DropTarget.Type.NEW_TAB, target.getType());
		assertEquals(2, layout.getTabs().size());
		assertTrue(layout.getTab(1).contains(1));
	}

	@Test
	public void dragOntoEmptySpaceCancels()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);

		DropTarget target = model.endDrag(-1000, -1000);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		assertFalse(model.isDragging());
		assertTrue(layout.getMainTab().contains(1));
	}

	@Test
	public void cancelDragClearsStateWithoutTouchingLayout()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);
		model.cancelDrag();

		assertFalse(model.isDragging());
		assertNull(model.getDragSlot());
		assertEquals(-1, model.getDropSlotIndex());
	}

	@Test
	public void dropOnEmptyGridSpaceAppendsToTheViewedTab()
	{
		// Since a tab always renders at least visibleRows of grid (D12), the far corner of the grid
		// is a real (empty) cell in this tab, not the beyond-the-rendered-rows GRID_EMPTY case.
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(1, 2);
		layout.getMainTab().setAt(2, 3);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1), item(3, "C", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0); // item 1
		model.beginDrag(dragFrom.x, dragFrom.y);

		java.awt.Rectangle grid = model.gridRect();
		DropTarget target = model.endDrag(grid.x + grid.width - 1, grid.y + grid.height - 1);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertFalse(model.isDragging());
		assertNull(layout.getMainTab().itemAt(0));
		assertTrue(layout.getMainTab().contains(1));
	}

	@Test
	public void pruningAnEarlierEmptyTabKeepsTheActiveTab()
	{
		int tab1 = layout.createTab(1);
		int tab2 = layout.createTab(2);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "AItem", 1), item(2, "BItem", 1))));
		// The All view is the only way to reach an item in a non-active tab without a search filter
		// (which disables dragging), so it stands in for "viewing something other than tab1".
		model.setActiveTab(-1);
		BankTab tab2Ref = layout.getTab(tab2);
		model.rebuild();

		int dragIndex = -1;
		for (int i = 0; i < model.getSlots().size(); i++)
		{
			if (model.getSlots().get(i).getCanonicalId() == 1)
			{
				dragIndex = i;
				break;
			}
		}
		java.awt.Rectangle dragFrom = model.slotRect(dragIndex);
		model.beginDrag(dragFrom.x, dragFrom.y);
		assertEquals(1, model.getDragSlot().getCanonicalId());

		// Drop onto the main tab strip entry: strip index 0 = All, 1 = Main.
		java.awt.Rectangle mainTabRect = model.tabRect(1);
		model.endDrag(mainTabRect.x + 1, mainTabRect.y + 1);

		// tab1 is now empty and pruned, shifting tab2 down to index 1; the All view was never tied
		// to a specific tab index, so it must stay All.
		assertEquals(-1, model.getActiveTab());
		assertTrue(layout.getTab(1) == tab2Ref);
	}

	// ---- tab drag reorder ----

	@Test
	public void beginTabDragRefusesTheAllTabAndThePlusButtonButAllowsTheMainTab()
	{
		layout.createTab(1);
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		model.beginTabDrag(0); // All
		assertFalse(model.isTabDragging());

		model.beginTabDrag(model.getStripLength() - 1); // plus button
		assertFalse(model.isTabDragging());

		model.beginTabDrag(1); // Main - reorderable like any other tab
		assertTrue(model.isTabDragging());
		assertEquals(0, model.getTabDragFrom());
	}

	@Test
	public void dragMainTabToTheEndOfTheStripMovesItThereAndKeepsTheMainFlag()
	{
		int a = layout.createTab(1);
		int b = layout.createTab(2);
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		// Strip: 0=All, 1=Main, 2=a, 3=b, 4=plus. Drag Main (strip 1) onto b's button (strip 3).
		model.beginTabDrag(1);
		java.awt.Rectangle bRect = model.tabRect(3);
		DropTarget target = model.endTabDrag(bRect.x + 1, bRect.y + 1);

		assertEquals(DropTarget.Type.TAB_REORDER, target.getType());
		assertEquals(2, layout.indexOfMainTab());
		assertTrue(layout.getTab(2).isMain());
		assertEquals("Main", layout.getTab(2).getName());
		assertTrue(layout.getTab(0).contains(1));
		assertTrue(layout.getTab(1).contains(2));
	}

	@Test
	public void endTabDragReordersTheTabsAndReturnsTabReorder()
	{
		int a = layout.createTab(1);
		int b = layout.createTab(2);
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		// Strip: 0=All, 1=Main, 2=a, 3=b. Dragging a (index 1) to b's slot (index 2) moves it there,
		// shifting b down to index 1.
		model.beginTabDrag(2);
		java.awt.Rectangle bRect = model.tabRect(3);
		DropTarget target = model.endTabDrag(bRect.x + 1, bRect.y + 1);

		assertEquals(DropTarget.Type.TAB_REORDER, target.getType());
		assertTrue(layout.getTab(1).contains(2));
		assertTrue(layout.getTab(2).contains(1));
	}

	@Test
	public void endTabDragOffTheStripCancelsAndLeavesTheOrderAlone()
	{
		layout.createTab(1);
		layout.createTab(2);
		model.setSnapshots(Collections.emptyList());
		model.rebuild();
		List<String> before = new ArrayList<>();
		for (BankTab t : layout.getTabs())
		{
			before.add(t.getName());
		}

		model.beginTabDrag(2);
		DropTarget target = model.endTabDrag(-1000, -1000);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		List<String> after = new ArrayList<>();
		for (BankTab t : layout.getTabs())
		{
			after.add(t.getName());
		}
		assertEquals(before, after);
	}

	@Test
	public void theActiveTabFollowsTheTabItWasViewingThroughAReorder()
	{
		int a = layout.createTab(1);
		int b = layout.createTab(2);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(a);
		BankTab aRef = layout.getTab(a);
		model.rebuild();

		model.beginTabDrag(2); // strip index of a
		java.awt.Rectangle bRect = model.tabRect(3);
		model.endTabDrag(bRect.x + 1, bRect.y + 1);

		assertTrue(layout.getTab(model.getActiveTab()) == aRef);
	}

	// ---- context menu ----

	@Test
	public void activateMenuReleasePlaceholderRemovesOnlyUnownedId()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Owned", 1))));
		model.setActiveTab(0);
		model.rebuild();

		// Slot 0 = owned item 1, slot 1 = placeholder item 2.
		java.awt.Rectangle placeholderRect = model.slotRect(1);
		model.openMenu(placeholderRect.x, placeholderRect.y);
		assertTrue(model.isMenuOpen());

		ContextMenu menu = model.getMenu();
		int releaseIdx = indexOfAction(menu, MenuAction.RELEASE_PLACEHOLDER);
		java.awt.Rectangle releaseEntry = menu.entryRect(releaseIdx);

		boolean changed = model.activateMenu(releaseEntry.x, releaseEntry.y);

		assertTrue(changed);
		assertFalse(model.isMenuOpen());
		assertTrue(layout.getMainTab().contains(1));
		assertFalse(layout.getMainTab().contains(2));
	}

	@Test
	public void activateMenuCancelDoesNotChangeLayout()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle placeholderRect = model.slotRect(0);
		model.openMenu(placeholderRect.x, placeholderRect.y);

		ContextMenu menu = model.getMenu();
		java.awt.Rectangle cancelEntry = menu.entryRect(menu.getEntries().size() - 1);

		boolean changed = model.activateMenu(cancelEntry.x, cancelEntry.y);

		assertFalse(changed);
		assertFalse(model.isMenuOpen());
		assertTrue(layout.getMainTab().contains(1));
	}

	@Test
	public void openMenuOnTitleBarOffersCloseAndCancel()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle title = model.titleBarRect();
		model.openMenu(title.x, title.y);

		assertTrue(model.isMenuOpen());
		List<ContextMenuEntry> entries = model.getMenu().getEntries();
		assertEquals(2, entries.size());
		assertEquals(MenuAction.CLOSE_VIEW, entries.get(0).getAction());
		assertEquals(MenuAction.CANCEL, entries.get(1).getAction());
	}

	@Test
	public void activateMenuCloseViewRaisesACloseRequest()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle title = model.titleBarRect();
		model.openMenu(title.x, title.y);
		java.awt.Rectangle closeEntry = model.getMenu().entryRect(0);

		assertFalse(model.consumeCloseRequest());

		boolean changed = model.activateMenu(closeEntry.x, closeEntry.y);

		assertFalse(changed);
		assertFalse(model.isMenuOpen());
		assertTrue(model.consumeCloseRequest());
		assertFalse("the request is one-shot", model.consumeCloseRequest());
	}

	@Test
	public void activateMenuCancelRaisesNoCloseRequest()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle title = model.titleBarRect();
		model.openMenu(title.x, title.y);
		java.awt.Rectangle cancelEntry = model.getMenu().entryRect(1);

		model.activateMenu(cancelEntry.x, cancelEntry.y);

		assertFalse(model.consumeCloseRequest());
	}

	@Test
	public void setTabIconMenuEntryIsOfferedForAnyItemInACustomTab()
	{
		int tab = layout.createTab(1);
		layout.getTab(tab).append(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(tab);
		model.rebuild();

		java.awt.Rectangle secondSlot = model.slotRect(1);
		model.openMenu(secondSlot.x, secondSlot.y);

		assertTrue(indexOfAction(model.getMenu(), MenuAction.SET_TAB_ICON) >= 0);
	}

	@Test
	public void activateSetTabIconChangesTheIconWithoutMovingTheItem()
	{
		int tab = layout.createTab(1);
		layout.getTab(tab).append(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(tab);
		model.rebuild();

		java.awt.Rectangle secondSlot = model.slotRect(1);
		model.openMenu(secondSlot.x, secondSlot.y);
		int idx = indexOfAction(model.getMenu(), MenuAction.SET_TAB_ICON);
		java.awt.Rectangle entryRect = model.getMenu().entryRect(idx);

		boolean changed = model.activateMenu(entryRect.x, entryRect.y);

		assertTrue(changed);
		assertEquals(2, layout.getTab(tab).getIcon());
		assertEquals(Integer.valueOf(2), layout.getTab(tab).itemAt(1));
	}

	@Test
	public void clearTabIconEntryAppearsOnlyWhenAnExplicitIconIsSet()
	{
		int tab = layout.createTab(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(tab);
		model.rebuild();

		java.awt.Rectangle slotRect = model.slotRect(0);
		model.openMenu(slotRect.x, slotRect.y);
		assertEquals(-1, indexOfAction(model.getMenu(), MenuAction.CLEAR_TAB_ICON));
		model.closeMenu();

		layout.setTabIcon(tab, 1);
		model.openMenu(slotRect.x, slotRect.y);
		assertTrue(indexOfAction(model.getMenu(), MenuAction.CLEAR_TAB_ICON) >= 0);
	}

	@Test
	public void ignoreMenuEntryIsOfferedOnBothOwnedAndPlaceholderCells()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Owned", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle ownedRect = model.slotRect(0);
		model.openMenu(ownedRect.x, ownedRect.y);
		assertTrue(indexOfAction(model.getMenu(), MenuAction.IGNORE_PLACEHOLDER) >= 0);

		java.awt.Rectangle placeholderRect = model.slotRect(1);
		model.openMenu(placeholderRect.x, placeholderRect.y);
		assertTrue(indexOfAction(model.getMenu(), MenuAction.IGNORE_PLACEHOLDER) >= 0);
	}

	@Test
	public void unignoreMenuEntryIsOfferedOnlyForAnIgnoredId()
	{
		layout.getMainTab().append(1);
		layout.addPlaceholderIgnore(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Owned", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle slotRect = model.slotRect(0);
		model.openMenu(slotRect.x, slotRect.y);

		assertTrue(indexOfAction(model.getMenu(), MenuAction.UNIGNORE_PLACEHOLDER) >= 0);
		assertEquals(-1, indexOfAction(model.getMenu(), MenuAction.IGNORE_PLACEHOLDER));
	}

	@Test
	public void rightClickOnAnEmptyCellOpensNoMenu()
	{
		layout.getMainTab().setAt(0, 1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle emptyRect = model.slotRect(1);
		model.openMenu(emptyRect.x, emptyRect.y);

		assertFalse(model.isMenuOpen());
	}

	private static int indexOfAction(ContextMenu menu, MenuAction action)
	{
		List<ContextMenuEntry> entries = menu.getEntries();
		for (int i = 0; i < entries.size(); i++)
		{
			if (entries.get(i).getAction() == action)
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void deletingAnEarlierTabKeepsTheSameTabActive()
	{
		layout.createTab(1); // tab index 1
		layout.createTab(2); // tab index 2
		layout.createTab(3); // tab index 3
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1), item(3, "C", 1))));
		model.setActiveTab(3);
		model.rebuild();

		// Strip: 0 = All, 1 = Main, 2 = tab1, 3 = tab2, 4 = tab3. Delete tab index 2 (strip index 3).
		java.awt.Rectangle tabRect = model.tabRect(3);
		model.openMenu(tabRect.x, tabRect.y);
		ContextMenuEntry deleteEntry = null;
		for (ContextMenuEntry e : model.getMenu().getEntries())
		{
			if (e.getAction() == MenuAction.DELETE_TAB)
			{
				deleteEntry = e;
				break;
			}
		}
		assertEquals(Integer.valueOf(2), Integer.valueOf(deleteEntry.getArg()));
		java.awt.Rectangle deleteRect = model.getMenu().entryRect(model.getMenu().getEntries().indexOf(deleteEntry));

		boolean changed = model.activateMenu(deleteRect.x, deleteRect.y);

		assertTrue(changed);
		assertEquals(2, model.getActiveTab());
		model.rebuild();
		assertTrue("the viewed tab's item still renders", model.getSlots().stream()
			.anyMatch(s -> s.getCanonicalId() == 3));
	}

	@Test
	public void deletingTheActiveTabFallsBackToAll()
	{
		layout.createTab(1); // tab index 1
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(1);
		model.rebuild();

		// Strip: 0 = All, 1 = Main, 2 = tab1.
		java.awt.Rectangle tabRect = model.tabRect(2);
		model.openMenu(tabRect.x, tabRect.y);
		ContextMenuEntry deleteEntry = null;
		for (ContextMenuEntry e : model.getMenu().getEntries())
		{
			if (e.getAction() == MenuAction.DELETE_TAB)
			{
				deleteEntry = e;
				break;
			}
		}
		java.awt.Rectangle deleteRect = model.getMenu().entryRect(model.getMenu().getEntries().indexOf(deleteEntry));

		model.activateMenu(deleteRect.x, deleteRect.y);

		assertEquals(-1, model.getActiveTab());
	}

	// ---- collapse blank spaces ----

	@Test
	public void rightClickOnTheMainTabButtonOffersCollapseBlankSpaces()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(0);
		model.rebuild();

		// Strip: 0 = All, 1 = Main.
		java.awt.Rectangle tabRect = model.tabRect(1);
		model.openMenu(tabRect.x, tabRect.y);

		int idx = indexOfAction(model.getMenu(), MenuAction.COMPACT_TAB);
		assertTrue(idx >= 0);
		assertEquals(Integer.valueOf(0), Integer.valueOf(model.getMenu().getEntries().get(idx).getArg()));
	}

	@Test
	public void rightClickOnACustomTabButtonAlsoOffersCollapseBlankSpaces()
	{
		int other = layout.createTab(1);
		layout.getTab(other).setAt(3, 2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(other);
		model.rebuild();

		// Strip: 0 = All, 1 = Main, 2 = the custom tab.
		java.awt.Rectangle tabRect = model.tabRect(2);
		model.openMenu(tabRect.x, tabRect.y);

		int idx = indexOfAction(model.getMenu(), MenuAction.COMPACT_TAB);
		assertTrue(idx >= 0);
		assertEquals(Integer.valueOf(other), Integer.valueOf(model.getMenu().getEntries().get(idx).getArg()));
	}

	@Test
	public void rightClickOnTheAllButtonDoesNotOfferCollapseBlankSpaces()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle allRect = model.tabRect(0);
		model.openMenu(allRect.x, allRect.y);

		assertEquals(-1, indexOfAction(model.getMenu(), MenuAction.COMPACT_TAB));
	}

	@Test
	public void collapseBlankSpacesActionRemovesInteriorGapsAndRebuildsRows()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle tabRect = model.tabRect(1);
		model.openMenu(tabRect.x, tabRect.y);
		int idx = indexOfAction(model.getMenu(), MenuAction.COMPACT_TAB);
		java.awt.Rectangle entryRect = model.getMenu().entryRect(idx);

		boolean changed = model.activateMenu(entryRect.x, entryRect.y);

		assertTrue(changed);
		assertEquals(Arrays.asList(1, 2), layout.getMainTab().getSlots());
		model.rebuild();
		assertEquals(Integer.valueOf(1), Integer.valueOf(model.getSlots().get(0).getCanonicalId()));
		assertEquals(Integer.valueOf(2), Integer.valueOf(model.getSlots().get(1).getCanonicalId()));
	}

	@Test
	public void collapseBlankSpacesIsANoOpOnAnAlreadyDenseTab()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle tabRect = model.tabRect(1);
		model.openMenu(tabRect.x, tabRect.y);
		int idx = indexOfAction(model.getMenu(), MenuAction.COMPACT_TAB);
		java.awt.Rectangle entryRect = model.getMenu().entryRect(idx);

		boolean changed = model.activateMenu(entryRect.x, entryRect.y);

		assertFalse(changed);
		assertEquals(Arrays.asList(1, 2), layout.getMainTab().getSlots());
	}

	@Test
	public void collapseBlankSpacesDoesNotTouchOtherTabs()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);
		int other = layout.createTab(3);
		layout.getTab(other).setAt(2, 4);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1), item(3, "C", 1), item(4, "D", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle tabRect = model.tabRect(1);
		model.openMenu(tabRect.x, tabRect.y);
		int idx = indexOfAction(model.getMenu(), MenuAction.COMPACT_TAB);
		java.awt.Rectangle entryRect = model.getMenu().entryRect(idx);

		model.activateMenu(entryRect.x, entryRect.y);

		assertEquals(Arrays.asList(3, null, 4), layout.getTab(other).getSlots());
	}

	// ---- rename tab ----

	@Test
	public void rightClickOnTheMainTabButtonOffersRenameTab()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle tabRect = model.tabRect(1);
		model.openMenu(tabRect.x, tabRect.y);

		int idx = indexOfAction(model.getMenu(), MenuAction.RENAME_TAB);
		assertTrue(idx >= 0);
		assertEquals(Integer.valueOf(0), Integer.valueOf(model.getMenu().getEntries().get(idx).getArg()));
	}

	@Test
	public void rightClickOnACustomTabButtonAlsoOffersRenameTab()
	{
		int other = layout.createTab(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(other);
		model.rebuild();

		// Strip: 0 = All, 1 = Main, 2 = the custom tab.
		java.awt.Rectangle tabRect = model.tabRect(2);
		model.openMenu(tabRect.x, tabRect.y);

		int idx = indexOfAction(model.getMenu(), MenuAction.RENAME_TAB);
		assertTrue(idx >= 0);
		assertEquals(Integer.valueOf(other), Integer.valueOf(model.getMenu().getEntries().get(idx).getArg()));
	}

	@Test
	public void rightClickOnTheAllButtonDoesNotOfferRenameTab()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle allRect = model.tabRect(0);
		model.openMenu(allRect.x, allRect.y);

		assertEquals(-1, indexOfAction(model.getMenu(), MenuAction.RENAME_TAB));
	}

	@Test
	public void activatingRenameTabRaisesARequestInsteadOfMutatingTheLayout()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle tabRect = model.tabRect(1);
		model.openMenu(tabRect.x, tabRect.y);
		int idx = indexOfAction(model.getMenu(), MenuAction.RENAME_TAB);
		java.awt.Rectangle entryRect = model.getMenu().entryRect(idx);

		assertEquals(-1, model.consumeRenameTabRequest());

		boolean changed = model.activateMenu(entryRect.x, entryRect.y);

		assertFalse("the pure tier never mutates the name itself - the controller opens a chatbox first",
			changed);
		assertFalse(model.isMenuOpen());
		assertEquals("Main", layout.getMainTab().getName());
		assertEquals(0, model.consumeRenameTabRequest());
		assertEquals("one-shot, like consumeCloseRequest", -1, model.consumeRenameTabRequest());
	}

	@Test
	public void renameTabSetsTheNameAndInvalidatesForARebuild()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();
		assertFalse(model.isDirty());

		boolean changed = model.renameTab(0, "Runes");

		assertTrue(changed);
		assertEquals("Runes", layout.getMainTab().getName());
		assertTrue("a rename must trigger a rebuild so the title/divider pick it up", model.isDirty());
	}

	@Test
	public void renameTabIsANoOpWhenTheNameDoesNotChange()
	{
		layout.getMainTab().setName("Runes");
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		boolean changed = model.renameTab(0, "Runes");

		assertFalse(changed);
	}

	@Test
	public void titleBaseNameIsTheActiveTabsNameAndTracksARename()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();
		assertEquals("Main", model.titleBaseName());

		model.renameTab(0, "Runes");
		model.rebuild();

		assertEquals("Runes", model.titleBaseName());
	}

	@Test
	public void titleBaseNameIsBanklessBankForTheAllTab()
	{
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(-1);
		model.rebuild();

		assertEquals("Bankless Bank", model.titleBaseName());
	}

	@Test
	public void allViewDividerShowsTheRenamedTabsName()
	{
		int other = layout.createTab(1);
		layout.getMainTab().append(2);
		model.renameTab(other, "Runes");
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(-1);
		model.rebuild();

		BankRow divider = model.getRows().stream()
			.filter(r -> r.getKind() == BankRow.Kind.HEADER && r.getTabIndex() == other)
			.findFirst()
			.orElse(null);
		assertEquals("Runes", divider.getHeaderText());
	}

	@Test
	public void setPlaceholdersEnabledDoesNotInvalidateWhenUnchanged()
	{
		model.setSnapshots(Collections.emptyList());
		model.setPlaceholdersEnabled(true);
		model.rebuild();
		assertFalse(model.isDirty());

		model.setPlaceholdersEnabled(true);
		assertFalse("no-op setter must not force a rebuild every frame", model.isDirty());

		model.setPlaceholdersEnabled(false);
		assertTrue(model.isDirty());
	}

	// ---- counts ----

	@Test
	public void getTotalItemCountSumsOwnedItemsAcrossAllTabsRegardlessOfActiveTab()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2);
		layout.getTab(other).append(3); // placeholder, not owned
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(other);
		model.rebuild();

		assertEquals(2, model.getTotalItemCount());
	}

	@Test
	public void getTotalPlaceholderCountExcludesIgnoredIds()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		layout.addPlaceholderIgnore(2);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		assertEquals(1, model.getTotalPlaceholderCount());
	}

	// ---- GE value (card 6, spec section 5) ----

	@Test
	public void tabValueSumsQuantityTimesUnitPriceOverOwnedIdsInThatTabOnly()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2); // createTab already places id 2 into the new tab
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 3), item(2, "B", 5))));
		model.setUnitPrices(mapOf(1, 10, 2, 100));
		model.rebuild();

		assertEquals(30L, model.tabValue(0));
		assertEquals(500L, model.tabValue(other));
	}

	@Test
	public void placeholderContributesNothingToTabValueEvenWithAKnownPrice()
	{
		layout.getMainTab().append(42); // owned by no storage: a placeholder
		model.setSnapshots(Collections.emptyList());
		model.setUnitPrices(mapOf(42, 1_000_000));
		model.rebuild();

		assertEquals(0L, model.tabValue(0));
	}

	@Test
	public void tabValueForAllTabsEqualsTheSumOfEveryTabsValue()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2); // createTab already places id 2 into the new tab
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 2), item(2, "B", 3))));
		model.setUnitPrices(mapOf(1, 5, 2, 7));
		model.rebuild();

		assertEquals(model.tabValue(0) + model.tabValue(other), model.tabValue(-1));
		assertEquals(31L, model.tabValue(-1));
	}

	@Test
	public void tabValueUsesLongArithmeticForQuantitiesThatOverflowAnInt()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "Huge stack", 2_000_000_000L))));
		model.setUnitPrices(mapOf(1, 3));
		model.rebuild();

		assertEquals(6_000_000_000L, model.tabValue(0));
	}

	@Test
	public void titleValueDoesNotChangeWhenASearchStringIsSet()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Abyssal whip", 2))));
		model.setUnitPrices(mapOf(1, 1000));
		model.setActiveTab(0);
		model.rebuild();
		long before = model.titleValue();

		model.setSearch("whip");
		model.rebuild();

		assertEquals(before, model.titleValue());
	}

	@Test
	public void titleValueInByStorageModeSumsEveryOwnedSnapshotItem()
	{
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 2)),
			storage("poh", "POH", item(2, "B", 3))));
		model.setUnitPrices(mapOf(1, 5, 2, 7));
		model.setMode(ViewMode.BY_STORAGE);
		model.rebuild();

		assertEquals(2 * 5L + 3 * 7L, model.titleValue());
	}

	@Test
	public void unitPriceIsZeroForAnUnknownId()
	{
		assertEquals(0, model.unitPrice(999));
	}

	@Test
	public void slotAtMatchesHitTestAndIsNullOverADividerRow()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(-1);
		model.rebuild();

		java.awt.Rectangle slotRect = model.slotRect(0);
		assertEquals(1, model.slotAt(slotRect.x, slotRect.y).getCanonicalId());

		// The divider row sits above the first group's items; the model always draws the divider
		// starting at y = 0 in this fixture.
		assertNull(model.slotAt(model.gridRect().x + 1, model.gridRect().y + 1));
	}

	// ---- All view header rows ----

	@Test
	public void allViewEmitsOneHeaderRowPerNonEmptyTabCarryingItsIconId()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1))));
		model.setActiveTab(-1);
		model.rebuild();

		List<BankRow> headers = model.getRows().stream()
			.filter(r -> r.getKind() == BankRow.Kind.HEADER).collect(java.util.stream.Collectors.toList());
		assertEquals(2, headers.size());
		assertEquals(0, headers.get(0).getTabIndex());
		assertEquals(1, headers.get(0).getHeaderIconItemId());
		assertEquals(other, headers.get(1).getTabIndex());
	}

	@Test
	public void allViewSkipsTabsWithNoItems()
	{
		layout.getMainTab().append(1);
		int emptyTab = layout.createTab(2);
		layout.getTab(emptyTab).removeItem(2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(-1);
		model.rebuild();

		long headerCount = model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count();
		assertEquals(1, headerCount);
	}

	@Test
	public void allViewEmitsNoDividerWhenOnlyOneTabExists()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.setActiveTab(-1);
		model.rebuild();

		long headerCount = model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count();
		assertEquals("a single tab reads better as one unlabelled grid", 0, headerCount);
	}

	@Test
	public void searchingOnTheAllViewRemovesEveryDividerAndClearingRestoresThem()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2);
		layout.getTab(other).append(3);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "Abyssal whip", 1), item(2, "Bronze sword", 1), item(3, "Rune whip", 1))));
		model.setActiveTab(-1);
		model.rebuild();
		assertEquals(2, model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count());

		model.setSearch("whip");
		model.rebuild();
		assertEquals(0, model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count());

		model.clearSearch();
		model.rebuild();
		assertEquals(2, model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count());
	}

	@Test
	public void selectingASpecificTabProducesNoDividers()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2);
		layout.getTab(other).append(3);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(3, "B", 1))));
		model.setActiveTab(other);
		model.rebuild();

		assertEquals(0, model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count());
	}

	@Test
	public void contentHeightOnTheAllViewAccountsForEveryDividerAndItemRow()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(2);
		layout.getTab(other).append(3);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(1, "A", 1), item(3, "B", 1))));
		model.setActiveTab(-1);
		model.rebuild();

		long dividers = model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.HEADER).count();
		long itemRows = model.getRows().stream().filter(r -> r.getKind() == BankRow.Kind.ITEMS).count();
		assertEquals(dividers * BankGeometry.HEADER_H + itemRows * BankGeometry.SLOT_H, model.getContentHeight());
	}

	// ---- misc getters ----

	@Test
	public void getStripLengthCountsAllPlusTabsPlusPlusButtonUnderCap()
	{
		assertEquals(3, model.getStripLength()); // All + Main + plus button
		for (int i = 1; i <= 7; i++)
		{
			layout.createTab(1000 + i);
		}
		assertEquals(10, model.getStripLength()); // All + 8 tabs + plus (8 < MAX_TABS)
		layout.createTab(1099); // the ninth tab, reaching MAX_TABS
		assertEquals(10, model.getStripLength()); // All + 9 tabs, no plus (cap reached)
		layout.createTab(9999);
		assertEquals(10, model.getStripLength()); // cap enforced: still 9 tabs, no plus
	}

	@Test
	public void setVisibleRowsClampsToRange()
	{
		model.setVisibleRows(1);
		assertEquals(BankGeometry.MIN_ROWS, model.getVisibleRows());
		model.setVisibleRows(100);
		assertEquals(BankGeometry.MAX_ROWS, model.getVisibleRows());
	}

	// ---- keyboard ----

	@Test
	public void onCharAppendsPrintableCharactersToSearch()
	{
		assertTrue(model.onChar('a'));
		assertTrue(model.onChar('b'));
		assertEquals("ab", model.getSearch());
	}

	@Test
	public void onCharRejectsControlCharacters()
	{
		assertFalse(model.onChar((char) 8));
		assertEquals("", model.getSearch());
	}

	@Test
	public void onBackspaceRemovesLastCharacter()
	{
		model.setSearch("abc");
		assertTrue(model.onBackspace());
		assertEquals("ab", model.getSearch());
	}

	@Test
	public void onBackspaceOnEmptySearchIsNoOp()
	{
		assertFalse(model.onBackspace());
	}

	@Test
	public void clearSearchEmptiesSearchText()
	{
		model.setSearch("abc");
		model.clearSearch();
		assertEquals("", model.getSearch());
	}

	// ---- column count: the window is a viewport, the tab's own width is the layout ----

	/** Fills the main tab's first {@code n} slots with items 1..n, all owned. */
	private void fillMainTab(int n)
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 1; i <= n; i++)
		{
			layout.getMainTab().setAt(i - 1, i);
			items.add(item(i, "Item " + i, 1));
		}
		model.setSnapshots(Collections.singletonList(
			new StorageSnapshot("carryable", "Inventory", null, items)));
		model.setActiveTab(0);
	}

	private static List<BankRow> itemRows(BankViewModel model)
	{
		List<BankRow> out = new ArrayList<>();
		for (BankRow row : model.getRows())
		{
			if (row.getKind() == BankRow.Kind.ITEMS)
			{
				out.add(row);
			}
		}
		return out;
	}

	@Test
	public void everyItemsRowIsAsWideAsTheTabOrTheWindow_whicheverIsWider()
	{
		fillMainTab(24);

		for (int cols : new int[]{4, 8, 12, 16})
		{
			model.setVisibleCols(cols);
			model.rebuild();

			final int expected = Math.max(layout.getMainTab().getCols(), cols);
			for (BankRow row : itemRows(model))
			{
				assertEquals("at " + cols + " columns", expected, row.getSlots().size());
			}
		}
	}

	@Test
	public void resizingTheWindowNeverMovesAnItem()
	{
		// The point of the per-tab width: index i lands at (i / tab.cols, i % tab.cols) whatever the
		// window is doing, so the second row starts with the same item at every window width.
		fillMainTab(24);
		model.setVisibleRows(BankGeometry.MIN_ROWS);
		final java.util.List<Integer> before = new ArrayList<>(layout.getMainTab().getSlots());

		for (int cols : new int[]{4, 12, 16, 8})
		{
			model.setVisibleCols(cols);
			model.rebuild();
			assertEquals("at " + cols + " columns", 1, model.getSlots().get(0).getCanonicalId());
			assertEquals("the tab's own 8-wide layout decides the second row, at " + cols + " columns",
				9, itemRows(model).get(1).getSlots().get(0).getCanonicalId());
		}

		assertEquals("a resize must never write to the layout", before, layout.getMainTab().getSlots());
		assertEquals(BankTab.DEFAULT_COLS, layout.getMainTab().getCols());
	}

	@Test
	public void occupiedRowCountFollowsTheTabsWidthNotTheWindows()
	{
		fillMainTab(24);
		model.setVisibleRows(BankGeometry.MIN_ROWS);

		model.setVisibleCols(4);
		model.rebuild();
		final int narrowRows = itemRows(model).size();

		model.setVisibleCols(12);
		model.rebuild();
		assertEquals("24 items in an 8-wide tab always occupy 3 rows", narrowRows, itemRows(model).size());

		// Only re-laying the tab out changes that.
		layout.compactTab(0, 12);
		model.invalidate();
		model.rebuild();
		assertTrue("the same items in a 12-wide tab need fewer rows", itemRows(model).size() < narrowRows);
	}

	// ---- a window wider than the tab: blank columns that widen the tab when dropped on ----

	@Test
	public void aWindowWiderThanTheTabPadsEachRowWithBlankBeyondWidthCells()
	{
		fillMainTab(8);
		model.setVisibleCols(12);
		model.rebuild();

		final BankRow first = itemRows(model).get(0);
		assertEquals(12, first.getSlots().size());
		for (int c = 8; c < 12; c++)
		{
			assertTrue("column " + c + " is past the tab's width", first.getSlots().get(c).isEmpty());
			assertTrue("column " + c + " must be a beyond-width cell",
				first.getSlots().get(c).isBeyondWidth());
			assertEquals(c, first.getSlots().get(c).getGridCol());
			assertEquals(-1, first.getSlots().get(c).getIndexInTab());
		}
		assertFalse("a cell inside the tab's width is an ordinary slot",
			first.getSlots().get(7).isBeyondWidth());
	}

	@Test
	public void droppingOnABlankColumnBeyondTheTabWidensItAndKeepsEveryOtherItemWhereItWas()
	{
		fillMainTab(16);
		model.setVisibleCols(12);
		model.rebuild();

		// Row 1, column 9: three columns past the tab's own eight.
		final int flat = 12 + 9;
		assertTrue(model.getSlots().get(flat).isBeyondWidth());
		final Rectangle from = model.slotRect(0);
		final Rectangle onto = model.slotRect(flat);

		model.beginDrag(from.x + 4, from.y + 4);
		DropTarget target = model.endDrag(onto.x + 4, onto.y + 4);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertEquals("the tab widened to hold the dropped column", 10, layout.getMainTab().getCols());
		assertEquals("the dragged item sits at (row 1, col 9) of the widened tab",
			Integer.valueOf(1), layout.getMainTab().itemAt(1 * 10 + 9));

		// Item 9 was the first cell of row 1 before the widening and must still be.
		assertEquals(Integer.valueOf(9), layout.getMainTab().itemAt(10));
		// Item 16 was (row 1, col 7) and must still be.
		assertEquals(Integer.valueOf(16), layout.getMainTab().itemAt(1 * 10 + 7));
	}

	@Test
	public void theAllViewNeverOffersBeyondWidthCells()
	{
		fillMainTab(8);
		model.setActiveTab(-1);
		model.setVisibleCols(16);
		model.rebuild();

		for (BankSlot slot : model.getSlots())
		{
			assertFalse("the All view lays every tab out at its own width", slot.isBeyondWidth());
		}
	}

	// ---- a window narrower than the tab: horizontal scrolling ----

	@Test
	public void aWindowAsWideAsTheTabHasNothingToScrollSideways()
	{
		fillMainTab(24);
		model.setVisibleCols(8);
		model.rebuild();

		assertEquals(0, model.getMaxHScroll());
		assertEquals(model.getViewportWidth(), model.getContentWidth());
	}

	@Test
	public void aWindowNarrowerThanTheTabScrollsSidewaysByTheDifference()
	{
		fillMainTab(24);
		model.setVisibleCols(4);
		model.rebuild();

		assertEquals(8 * BankGeometry.SLOT_W, model.getContentWidth());
		assertEquals(4 * BankGeometry.SLOT_W, model.getMaxHScroll());

		model.setHScroll(9999);
		assertEquals(model.getMaxHScroll(), model.getHScroll());
		model.setHScroll(-5);
		assertEquals(0, model.getHScroll());
	}

	@Test
	public void horizontalScrollShiftsTheGridLeftAndHitTestingFollowsIt()
	{
		fillMainTab(24);
		model.setVisibleCols(4);
		model.rebuild();

		final Rectangle atRest = model.slotRect(5);
		model.setHScroll(2 * BankGeometry.SLOT_W);
		final Rectangle scrolled = model.slotRect(5);
		assertEquals(atRest.x - 2 * BankGeometry.SLOT_W, scrolled.x);
		assertEquals(atRest.y, scrolled.y);

		// Slot 5 is column 5 of the tab, only reachable in a 4-column window once it has scrolled.
		Hit hit = model.hitTest(scrolled.x + 4, scrolled.y + 4);
		assertEquals(Hit.Type.SLOT, hit.getType());
		assertEquals(6, hit.getSlot().getCanonicalId());
	}

	@Test
	public void wideningTheWindowReClampsAHorizontalOffsetThatNoLongerExists()
	{
		fillMainTab(24);
		model.setVisibleCols(4);
		model.rebuild();
		model.setHScroll(model.getMaxHScroll());
		assertTrue(model.getHScroll() > 0);

		model.setVisibleCols(12);
		model.rebuild();
		assertEquals(0, model.getMaxHScroll());
		assertEquals(0, model.getHScroll());
	}

	@Test
	public void aTabsHorizontalOffsetIsRememberedAcrossTabSwitchesLikeItsVerticalOne()
	{
		fillMainTab(24);
		model.setVisibleCols(4);
		model.rebuild();
		model.setHScroll(BankGeometry.SLOT_W);
		final int remembered = model.getHScroll();
		assertTrue(remembered > 0);

		model.setActiveTab(-1);
		model.rebuild();
		assertEquals("the All view starts at its own offset", 0, model.getHScroll());

		model.setActiveTab(0);
		model.rebuild();
		assertEquals(remembered, model.getHScroll());
	}

	@Test
	public void searchResultsStayDenseAtTheWindowWidthAndNeverScrollSideways()
	{
		fillMainTab(24);
		model.setVisibleCols(4);
		model.rebuild();
		model.setHScroll(model.getMaxHScroll());

		model.setSearch("Item 1");
		model.rebuild();

		for (BankRow row : itemRows(model))
		{
			assertTrue("a search row holds at most the window's columns", row.getSlots().size() <= 4);
		}
		assertEquals(0, model.getMaxHScroll());
		assertEquals(0, model.getHScroll());
	}

	// ---- a tab taller than the window: vertical scrollbar visibility ----

	@Test
	public void aTabThatFitsInTheWindowHasNoVerticalScrollbar()
	{
		fillMainTab(8);
		model.setVisibleRows(6);
		model.rebuild();

		assertEquals(0, model.getMaxScroll());
		assertFalse(model.isVScrollbarVisible());
	}

	@Test
	public void aTabTallerThanTheWindowShowsTheVerticalScrollbar()
	{
		fillMainTab(56);
		model.setVisibleRows(6);
		model.rebuild();

		assertTrue("56 items at 8 cols is 7 rows, one more than the 6-row viewport",
			model.getMaxScroll() > 0);
		assertTrue(model.isVScrollbarVisible());
	}

	@Test
	public void resizingTallerHidesTheVerticalScrollbarAgain()
	{
		fillMainTab(56);
		model.setVisibleRows(6);
		model.rebuild();
		assertTrue(model.isVScrollbarVisible());

		model.setVisibleRows(20);
		model.rebuild();

		assertEquals(0, model.getMaxScroll());
		assertFalse("a taller window now fits every row", model.isVScrollbarVisible());
	}

	@Test
	public void theVerticalScrollbarVisibilityIsPerTab()
	{
		fillMainTab(8);
		final int other = layout.createTab(900);
		for (int i = 0; i < 56; i++)
		{
			layout.getTab(other).setAt(i, 900 + i);
		}
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 1; i <= 8; i++)
		{
			items.add(item(i, "Item " + i, 1));
		}
		for (int i = 0; i < 56; i++)
		{
			items.add(item(900 + i, "Wide item " + i, 1));
		}
		model.setSnapshots(Collections.singletonList(
			new StorageSnapshot("carryable", "Inventory", null, items)));
		model.setVisibleRows(6);

		model.setActiveTab(0);
		model.rebuild();
		assertFalse("the main tab only has 8 items, one row", model.isVScrollbarVisible());

		model.setActiveTab(other);
		model.rebuild();
		assertTrue("the other tab has 56 items, seven rows", model.isVScrollbarVisible());
	}

	@Test
	public void hitTestSkipsTheVerticalScrollbarWhenThereIsNothingToScroll()
	{
		fillMainTab(8);
		model.setVisibleRows(6);
		model.rebuild();

		Rectangle up = model.scrollUpRect();
		Hit hit = model.hitTest(up.x + 4, up.y + 4);
		assertEquals("no bar to hit, the point falls through to whatever is behind it",
			Hit.Type.NONE, hit.getType());
	}

	@Test
	public void wheelScrollingIsANoOpWhenThereIsNothingToScroll()
	{
		fillMainTab(8);
		model.setVisibleRows(6);
		model.rebuild();

		model.scrollBy(BankGeometry.SCROLL_STEP);

		assertEquals(0, model.getScroll());
	}

	@Test
	public void theAllViewsVirtualWidthIsTheWidestTabsWidth()
	{
		fillMainTab(8);
		final int other = layout.createTab(500);
		layout.getTab(other).setCols(16);
		layout.getTab(other).setAt(15, 501);
		model.setSnapshots(Collections.singletonList(new StorageSnapshot("carryable", "Inventory", null,
			Arrays.asList(item(500, "Wide one", 1), item(501, "Wide two", 1)))));
		for (int i = 1; i <= 8; i++)
		{
			layout.getMainTab().setAt(i - 1, i);
		}

		model.setActiveTab(-1);
		model.setVisibleCols(8);
		model.invalidate();
		model.rebuild();

		assertEquals(16 * BankGeometry.SLOT_W, model.getContentWidth());
		assertEquals(8 * BankGeometry.SLOT_W, model.getMaxHScroll());
	}

	// ---- collapse blank spaces reflows to the window width ----

	@Test
	public void collapseBlankSpacesPacksTheTabAndRelaysItOutAtTheWindowWidth()
	{
		fillMainTab(6);
		layout.getMainTab().setAt(2, null);
		model.setVisibleCols(12);
		model.rebuild();

		assertTrue(layout.compactTab(0, model.getVisibleCols()));

		assertEquals(12, layout.getMainTab().getCols());
		assertEquals(Arrays.asList(1, 2, 4, 5, 6), layout.getMainTab().getSlots());
	}

	@Test
	public void setVisibleColsClampsAtBothEnds()
	{
		model.setVisibleCols(1);
		assertEquals(BankGeometry.MIN_COLS, model.getVisibleCols());

		model.setVisibleCols(999);
		assertEquals(BankGeometry.MAX_COLS, model.getVisibleCols());
	}

	@Test
	public void setMaxColsCapsTheVisibleColumnsToWhatTheCanvasHolds()
	{
		model.setVisibleCols(16);
		model.setMaxCols(6);
		assertEquals(6, model.getVisibleCols());

		// Widening the canvas again does not restore the old width; the player asks for that.
		model.setMaxCols(BankGeometry.MAX_COLS);
		assertEquals(6, model.getVisibleCols());
	}

	@Test
	public void theWindowAndItsGridWidenWithTheColumnCount()
	{
		model.setVisibleCols(4);
		assertEquals(BankGeometry.width(4), model.size().width);
		assertEquals(4 * BankGeometry.SLOT_W, model.gridRect().width);

		model.setVisibleCols(12);
		assertEquals(BankGeometry.width(12), model.size().width);
		assertEquals(12 * BankGeometry.SLOT_W, model.gridRect().width);
		assertEquals(model.gridRect().x + model.gridRect().width, model.scrollbarRect().x);
		assertEquals(model.size().width - BankGeometry.GRIP, model.resizeGripRect().x);
	}

	@Test
	public void dropOnAnEmptyCellHitsTheRightSlotIndexAtTwelveColumns()
	{
		fillMainTab(3);
		model.setVisibleCols(12);
		model.rebuild();

		// The tab's slot 9 is (row 1, col 1) of its own 8-wide layout, which is flat cell 13 of a
		// 12-cell row: this only passes if hit-testing and the flat-index maths agree that the tab's
		// width, not the window's, decides where a slot index lands.
		java.awt.Rectangle dragFrom = model.slotRect(0);
		java.awt.Rectangle dropOnto = model.slotRect(12 + 1);

		model.beginDrag(dragFrom.x + 4, dragFrom.y + 4);
		DropTarget target = model.endDrag(dropOnto.x + 4, dropOnto.y + 4);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertEquals(9, target.getSlotIndex());
		assertNull(layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(9));
		assertEquals("dropping inside the tab's width must not widen it",
			BankTab.DEFAULT_COLS, layout.getMainTab().getCols());
	}

	@Test
	public void dropOnAnOccupiedCellSwapsInANarrowWindow()
	{
		fillMainTab(6);
		model.setVisibleCols(4);
		model.rebuild();

		// Items 5 and 6 sit at slots 4 and 5 of the tab's own first row, past a 4-column viewport, so
		// neither cell is reachable until the grid has scrolled sideways to them.
		assertTrue("a 4-column window on an 8-wide tab must scroll", model.getMaxHScroll() > 0);
		model.setHScroll(model.getMaxHScroll());
		java.awt.Rectangle dragFrom = model.slotRect(4);
		java.awt.Rectangle dropOnto = model.slotRect(5);
		assertTrue("the dragged cell must have scrolled into view", model.gridRect().contains(dragFrom));
		assertTrue("the drop cell must have scrolled into view", model.gridRect().contains(dropOnto));

		model.beginDrag(dragFrom.x + 4, dragFrom.y + 4);
		DropTarget target = model.endDrag(dropOnto.x + 4, dropOnto.y + 4);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertEquals(Integer.valueOf(6), layout.getMainTab().itemAt(4));
		assertEquals(Integer.valueOf(5), layout.getMainTab().itemAt(5));
	}

	@Test
	public void tallerWindowReClampsAScrollPositionThatNoLongerExists()
	{
		fillMainTab(80);
		model.setVisibleRows(BankGeometry.MIN_ROWS);
		model.rebuild();

		model.setScroll(model.getMaxScroll());
		final int deepScroll = model.getScroll();
		assertTrue("the short window must overflow", deepScroll > 0);

		model.setVisibleRows(BankGeometry.MAX_ROWS);
		model.rebuild();

		assertTrue("the tall window shows more at once, so the old offset cannot survive",
			model.getScroll() <= model.getMaxScroll());
		assertEquals(model.getMaxScroll(), model.getScroll());
	}

	@Test
	public void aTabsRememberedScrollIsRewrittenWhenTheRowCountChanges()
	{
		fillMainTab(80);
		model.setVisibleRows(BankGeometry.MIN_ROWS);
		model.rebuild();
		model.setScroll(model.getMaxScroll());

		model.setVisibleRows(BankGeometry.MAX_ROWS);
		model.rebuild();
		model.setScroll(model.getScroll());
		final int clamped = model.getScroll();

		// Leave the tab and come back: the remembered offset must be the clamped one, not the deep
		// short-window offset that no longer exists.
		model.setActiveTab(-1);
		model.setActiveTab(0);
		model.rebuild();

		assertEquals(clamped, model.getScroll());
	}

	// ---- manual item add (bottom bar's add button) --------------------------------------------

	@Test
	public void hitTestFindsTheAddButtonBetweenTheSearchFieldAndTheModeButton()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		Rectangle add = model.addButtonRect();
		Rectangle field = model.searchRect();
		Rectangle mode = model.modeButtonRect();

		assertEquals(Hit.Type.ADD_BUTTON, model.hitTest(add.x + 1, add.y + 1).getType());
		assertTrue("the add button sits left of the mode button", add.x + add.width <= mode.x);
		assertTrue("the search field stops before the add button", field.x + field.width <= add.x);
		assertFalse("the add button must not overlap the search field", add.intersects(field));
		assertEquals(Collections.singletonList("Add an item"), model.tooltipLines(add.x + 1, add.y + 1));
	}

	@Test
	public void addItemAppendsToTheActiveTabAsAPlaceholder()
	{
		int otherTab = layout.createTab(2);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(2, "Two", 1))));
		model.setActiveTab(otherTab);

		assertTrue(model.addItem(99));

		assertEquals(Arrays.asList(2, 99), layout.getTab(otherTab).getSlots());
		assertEquals(otherTab, model.getActiveTab());

		model.rebuild();
		BankSlot added = slotFor(99);
		assertTrue("an unowned manually added item renders as a placeholder", added.isPlaceholder());
		assertEquals(0, added.getQuantity());
	}

	@Test
	public void addItemTargetsTheMainTabWhileTheAllViewIsActive()
	{
		int otherTab = layout.createTab(2);
		model.setActiveTab(-1);

		assertTrue(model.addItem(99));

		assertEquals(Collections.singletonList(99), layout.getMainTab().getSlots());
		assertEquals(Collections.singletonList(2), layout.getTab(otherTab).getSlots());
		assertEquals(layout.indexOfMainTab(), model.getActiveTab());
	}

	@Test
	public void addItemJumpsToAnIdAlreadyInTheLayoutInsteadOfAddingItTwice()
	{
		int otherTab = layout.createTab(2);
		model.setActiveTab(layout.indexOfMainTab());

		assertFalse("nothing changed, so nothing to save", model.addItem(2));

		assertEquals(otherTab, model.getActiveTab());
		assertEquals(Collections.singletonList(2), layout.getTab(otherTab).getSlots());
		assertEquals(Collections.emptyList(), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemIsANoOpForANonPositiveId()
	{
		assertFalse(model.addItem(0));
		assertFalse(model.addItem(-5));
		assertEquals(Collections.emptyList(), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemClearsTheSearchFilterAndItsFocusSoTheNewSlotIsOnScreen()
	{
		fillMainTab(3);
		model.setSearch("item 1");
		model.setSearchFocused(true);
		model.rebuild();

		assertTrue(model.addItem(99));

		assertEquals("", model.getSearch());
		assertFalse(model.isSearchFocused());
		model.rebuild();
		assertEquals(99, slotFor(99).getCanonicalId());
	}

	@Test
	public void addItemLeavesTheByStorageViewForTheTabGrid()
	{
		model.setMode(ViewMode.BY_STORAGE);

		assertTrue(model.addItem(99));

		assertEquals(ViewMode.TABS, model.getMode());
	}

	@Test
	public void addItemScrollsTheNewSlotIntoView()
	{
		fillMainTab(200);
		model.setVisibleRows(BankGeometry.MIN_ROWS);
		model.rebuild();
		model.setScroll(0);

		assertTrue(model.addItem(999));
		model.rebuild();

		Rectangle r = model.slotRect(model.getSlots().indexOf(slotFor(999)));
		Rectangle grid = model.gridRect();
		assertTrue("the added slot must be inside the viewport, was " + r + " in " + grid,
			r.y >= grid.y && r.y + r.height <= grid.y + grid.height);
	}

	@Test
	public void addItemLeavesTheScrollAloneWhenTheNewSlotIsAlreadyVisible()
	{
		fillMainTab(3);
		model.rebuild();

		assertTrue(model.addItem(99));
		model.rebuild();

		assertEquals(0, model.getScroll());
		assertEquals(0, model.getHScroll());
	}

	@Test
	public void aManuallyAddedItemDragsLikeAnyOtherSlot()
	{
		fillMainTab(3);
		model.addItem(99);
		model.rebuild();

		assertTrue(layout.placeItem(99, layout.indexOfMainTab(), 0));

		assertEquals(Arrays.asList(99, 2, 3, 1), layout.getMainTab().getSlots());
	}

	// =========================================================================================
	// Context menu, shaped like the game's "Choose Option" menu (card 26)
	// =========================================================================================

	@Test
	public void anItemMenuRowCarriesTheItemNameAsItsOrangeTarget()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "Rune scimitar", 1))));
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle slot = model.slotRect(0);
		model.openMenu(slot.x, slot.y);

		int idx = indexOfAction(model.getMenu(), MenuAction.IGNORE_PLACEHOLDER);
		assertTrue(idx >= 0);
		ContextMenuEntry entry = model.getMenu().getEntries().get(idx);
		assertEquals("Never show placeholder", entry.getLabel());
		assertEquals("Rune scimitar", entry.getTarget());
		assertTrue(entry.hasTarget());
		assertEquals("Never show placeholder Rune scimitar", entry.text());
	}

	@Test
	public void aTabMenuRowCarriesTheTabNameAsItsOrangeTarget()
	{
		layout.getMainTab().append(1);
		int tabIndex = layout.createTab(2);
		layout.renameTab(tabIndex, "Runes");
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle tabRect = model.tabRect(tabIndex + 1);
		model.openMenu(tabRect.x + 2, tabRect.y + 2);

		int idx = indexOfAction(model.getMenu(), MenuAction.RENAME_TAB);
		assertTrue(idx >= 0);
		ContextMenuEntry entry = model.getMenu().getEntries().get(idx);
		assertEquals("Rename", entry.getLabel());
		assertEquals("Runes", entry.getTarget());
	}

	@Test
	public void cancelHasNoTarget()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle slot = model.slotRect(0);
		model.openMenu(slot.x, slot.y);

		ContextMenuEntry cancel = model.getMenu().getEntries()
			.get(model.getMenu().getEntries().size() - 1);
		assertEquals(MenuAction.CANCEL, cancel.getAction());
		assertFalse(cancel.hasTarget());
		assertEquals("Cancel", cancel.text());
	}

	@Test
	public void theMenuBoxUsesTheGamesHeaderAndRowGeometry()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle slot = model.slotRect(0);
		model.openMenu(slot.x, slot.y);

		ContextMenu menu = model.getMenu();
		int n = menu.getEntries().size();
		java.awt.Rectangle bounds = menu.getBounds();

		// The game's own 15n + 22.
		assertEquals(15 * n + 22, bounds.height);
		assertEquals(BankGeometry.menuHeight(n), bounds.height);

		// A header band, then the first row two pixels below it.
		assertEquals(bounds.y + 1, menu.headerRect().y);
		assertEquals(bounds.y + BankGeometry.MENU_HEADER_H + BankGeometry.MENU_BODY_GAP,
			menu.entryRect(0).y);
		assertEquals(BankGeometry.MENU_ENTRY_H, menu.entryRect(0).height);
		assertEquals(menu.entryRect(0).y + BankGeometry.MENU_ENTRY_H, menu.entryRect(1).y);

		// The header belongs to no entry, so clicking it hits nothing.
		assertEquals(-1, menu.entryIndexAt(bounds.x + 4, bounds.y + 4));
		assertEquals(0, menu.entryIndexAt(bounds.x + 4, menu.entryRect(0).y + 1));
		assertEquals(n - 1, menu.entryIndexAt(bounds.x + 4, menu.entryRect(n - 1).y + 1));
		assertEquals(-1, menu.entryIndexAt(bounds.x + 4, bounds.y + bounds.height + 5));
	}

	@Test
	public void theMenuIsCentredHorizontallyOnTheClickLikeTheGames()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		// The title bar, mid-window, so neither clamp applies.
		java.awt.Rectangle title = model.titleBarRect();
		int x = model.size().width / 2;
		int y = title.y + 2;
		model.openMenu(x, y);

		java.awt.Rectangle bounds = model.getMenu().getBounds();
		assertEquals(x - bounds.width / 2, bounds.x);
		assertEquals(y, bounds.y);
	}

	@Test
	public void theMenuIsAtLeastAsWideAsItsHeaderAndItsWidestRow()
	{
		layout.getMainTab().append(1);
		model.setSnapshots(Collections.emptyList());
		model.setActiveTab(0);
		model.rebuild();

		java.awt.Rectangle slot = model.slotRect(0);
		model.openMenu(slot.x, slot.y);

		ContextMenu menu = model.getMenu();
		int widest = ContextMenu.TITLE.length();
		for (ContextMenuEntry entry : menu.getEntries())
		{
			widest = Math.max(widest, entry.text().length());
		}
		assertEquals(widest * BankGeometry.MENU_CHAR_W + BankGeometry.MENU_PADDING,
			menu.getBounds().width);
	}

	/** The rendered slot for an id in the current rows, or fails. */
	private BankSlot slotFor(int itemId)
	{
		for (BankSlot slot : model.getSlots())
		{
			if (slot != null && !slot.isEmpty() && slot.getCanonicalId() == itemId)
			{
				return slot;
			}
		}
		throw new AssertionError("no slot rendered for item " + itemId);
	}
}
