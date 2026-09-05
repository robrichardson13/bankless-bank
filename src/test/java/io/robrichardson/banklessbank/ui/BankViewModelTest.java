package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
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

	// ---- TABS grouping and ordering ----

	@Test
	public void rebuildTabsModeGroupsIntoRowsOfEightAndOrdersByTabSlotList()
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 1; i <= 10; i++)
		{
			items.add(item(i, "Item " + i, 1));
			layout.getMainTab().getSlots().add(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", items.toArray(new ItemSnapshot[0]))));

		model.rebuild();

		List<BankRow> rows = model.getRows();
		assertEquals(2, rows.size());
		assertEquals(8, rows.get(0).getSlots().size());
		assertEquals(2, rows.get(1).getSlots().size());
		for (int i = 0; i < 10; i++)
		{
			assertEquals(i + 1, model.getSlots().get(i).getCanonicalId());
		}
	}

	@Test
	public void exactlyEightItemsProducesExactlyOneFullRow()
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 1; i <= 8; i++)
		{
			items.add(item(i, "Item " + i, 1));
			layout.getMainTab().getSlots().add(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", items.toArray(new ItemSnapshot[0]))));

		model.rebuild();

		assertEquals(1, model.getRows().size());
		assertEquals(8, model.getRows().get(0).getSlots().size());
	}

	@Test
	public void singleItemProducesOneShortRow()
	{
		layout.getMainTab().getSlots().add(5);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(5, "Whip", 1))));

		model.rebuild();

		assertEquals(1, model.getRows().size());
		assertEquals(1, model.getRows().get(0).getSlots().size());
	}

	@Test
	public void emptyLayoutProducesNoRows()
	{
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		assertTrue(model.getRows().isEmpty());
		assertTrue(model.getSlots().isEmpty());
		assertEquals(0, model.getContentHeight());
	}

	// ---- placeholders ----

	@Test
	public void idInLayoutWithNoOwningStorageBecomesPlaceholder()
	{
		layout.getMainTab().getSlots().add(42);
		model.setSnapshots(Collections.emptyList());

		model.rebuild();

		assertEquals(1, model.getSlots().size());
		BankSlot slot = model.getSlots().get(0);
		assertTrue(slot.isPlaceholder());
		assertEquals(0, slot.getQuantity());
		assertTrue(slot.getSources().isEmpty());
	}

	@Test
	public void placeholderHiddenAndDroppedByLayoutWhenPlaceholdersDisabled()
	{
		layout.getMainTab().getSlots().add(42);
		model.setSnapshots(Collections.emptyList());
		model.setPlaceholdersEnabled(false);

		model.rebuild();
		assertTrue(model.getSlots().isEmpty());

		boolean changed = model.syncLayout();
		assertTrue(changed);
		assertFalse(layout.getMainTab().getSlots().contains(42));
	}

	// ---- quantities and sources ----

	@Test
	public void quantitiesSumAcrossStoragesAndSourcesListEveryContributingStorage()
	{
		layout.getMainTab().getSlots().add(7);
		model.setSnapshots(Arrays.asList(
			storage("carryable", "Inventory", item(7, "Coins", 100)),
			storage("death", "Deathpile (Edgeville)", item(7, "Coins", 50))));

		model.rebuild();

		BankSlot slot = model.getSlots().get(0);
		assertEquals(150, slot.getQuantity());
		assertEquals(2, slot.getSources().size());
		assertEquals("Inventory", slot.getSources().get(0).getStorageName());
		assertEquals(100, slot.getSources().get(0).getQuantity());
		assertEquals("Deathpile (Edgeville)", slot.getSources().get(1).getStorageName());
		assertEquals(50, slot.getSources().get(1).getQuantity());
	}

	// ---- search ----

	@Test
	public void searchFiltersCaseInsensitivelyAndSpansAllTabsRegardlessOfActiveTab()
	{
		layout.getMainTab().getSlots().add(1);
		int otherTab = layout.createTab(2);
		layout.getTab(otherTab).getSlots().add(3);

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
		assertEquals(Arrays.asList(1, 3), ids);
	}

	@Test
	public void searchWithNoMatchesProducesNoRows()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Abyssal whip", 1))));
		model.setSearch("nonexistentitem");

		model.rebuild();

		assertTrue(model.getRows().isEmpty());
	}

	// ---- BY_STORAGE ----

	@Test
	public void byStorageEmitsHeaderPerNonEmptyStorageAndNoPlaceholders()
	{
		layout.getMainTab().getSlots().add(99); // placeholder-only tab entry, irrelevant to BY_STORAGE
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

	// ---- scrolling ----

	@Test
	public void scrollClampsToZeroAndMaxScrollAndMaxScrollIsZeroWhenContentFits()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
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
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 1; i <= 40; i++)
		{
			items.add(item(i, "Item " + i, 1));
			layout.getMainTab().getSlots().add(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", items.toArray(new ItemSnapshot[0]))));
		model.setVisibleRows(3);
		model.rebuild();
		model.setScroll(model.getMaxScroll());
		int maxBefore = model.getMaxScroll();
		assertTrue(maxBefore > 0);

		// Shrink content drastically.
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Item 1", 1))));
		layout.getMainTab().getSlots().clear();
		layout.getMainTab().getSlots().add(1);
		model.rebuild();

		assertEquals(0, model.getMaxScroll());
		assertEquals(0, model.getScroll());
	}

	// ---- hit testing ----

	@Test
	public void hitTestReturnsSlotWithCorrectIndexAtRowColumnBoundaries()
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 1; i <= 9; i++)
		{
			items.add(item(i, "Item " + i, 1));
			layout.getMainTab().getSlots().add(i);
		}
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", items.toArray(new ItemSnapshot[0]))));
		model.rebuild();

		java.awt.Rectangle firstSlot = model.slotRect(0);
		Hit hit = model.hitTest(firstSlot.x, firstSlot.y);
		assertEquals(Hit.Type.SLOT, hit.getType());
		assertEquals(0, hit.getIndex());
		assertEquals(1, hit.getSlot().getCanonicalId());

		java.awt.Rectangle secondRowFirstSlot = model.slotRect(8);
		Hit hit2 = model.hitTest(secondRowFirstSlot.x, secondRowFirstSlot.y);
		assertEquals(Hit.Type.SLOT, hit2.getType());
		assertEquals(8, hit2.getIndex());
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
	public void hitTestReturnsGridEmptyWithinGridButNoSlot()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Whip", 1))));
		model.rebuild();

		java.awt.Rectangle grid = model.gridRect();
		Hit hit = model.hitTest(grid.x + grid.width - 1, grid.y + grid.height - 1);
		assertEquals(Hit.Type.GRID_EMPTY, hit.getType());
	}

	// ---- drag and drop ----

	@Test
	public void endDragOntoSlotReordersWithinTab()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2, 3));
		model.setSnapshots(Collections.singletonList(
			storage("carryable", "Inventory", item(1, "A", 1), item(2, "B", 1), item(3, "C", 1))));
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0); // item 1
		java.awt.Rectangle dropOnto = model.slotRect(2); // item 3

		model.beginDrag(dragFrom.x, dragFrom.y);
		assertTrue(model.isDragging());
		assertEquals(1, model.getDragSlot().getCanonicalId());

		DropTarget target = model.endDrag(dropOnto.x, dropOnto.y);

		assertEquals(DropTarget.Type.SLOT, target.getType());
		assertFalse(model.isDragging());
		// Insert semantics: dropping onto item 3's slot lands item 1 immediately before it,
		// adjusted for item 1's own removal shifting the list left by one (see BankLayout#moveItem).
		assertEquals(Arrays.asList(2, 1, 3), layout.getMainTab().getSlots());
	}

	@Test
	public void endDragOntoTabStripEntryMovesItemToThatTab()
	{
		layout.getMainTab().getSlots().add(1);
		int otherTab = layout.createTab(99);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip index: 0 = All, 1 = Main, 2 = otherTab (custom tab index 1).
		java.awt.Rectangle otherTabRect = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(otherTabRect.x + 1, otherTabRect.y + 1);

		assertEquals(DropTarget.Type.TAB, target.getType());
		assertEquals(otherTab, target.getTabIndex());
		assertTrue(layout.getTab(otherTab).getSlots().contains(1));
		assertFalse(layout.getMainTab().getSlots().contains(1));
	}

	@Test
	public void endDragOntoPlusCreatesNewTab()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		// Strip: 0 = All, 1 = Main, 2 = plus (only main tab exists so far).
		java.awt.Rectangle plusRect = model.tabRect(2);

		model.beginDrag(dragFrom.x, dragFrom.y);
		DropTarget target = model.endDrag(plusRect.x + 1, plusRect.y + 1);

		assertEquals(DropTarget.Type.NEW_TAB, target.getType());
		assertEquals(2, layout.getTabs().size());
		assertTrue(layout.getTab(1).getSlots().contains(1));
	}

	@Test
	public void dragOntoEmptySpaceCancels()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);

		DropTarget target = model.endDrag(-1000, -1000);

		assertEquals(DropTarget.Type.CANCEL, target.getType());
		assertFalse(model.isDragging());
		assertEquals(Arrays.asList(1), layout.getMainTab().getSlots());
	}

	@Test
	public void cancelDragClearsStateWithoutTouchingLayout()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "A", 1))));
		model.rebuild();

		java.awt.Rectangle dragFrom = model.slotRect(0);
		model.beginDrag(dragFrom.x, dragFrom.y);
		model.cancelDrag();

		assertFalse(model.isDragging());
		assertNull(model.getDragSlot());
		assertEquals(-1, model.getDropCaretIndex());
	}

	// ---- context menu ----

	@Test
	public void activateMenuReleasePlaceholderRemovesOnlyUnownedId()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));
		model.setSnapshots(Collections.singletonList(storage("carryable", "Inventory", item(1, "Owned", 1))));
		model.rebuild();

		// Slot 0 = owned item 1, slot 1 = placeholder item 2.
		java.awt.Rectangle placeholderRect = model.slotRect(1);
		model.openMenu(placeholderRect.x, placeholderRect.y);
		assertTrue(model.isMenuOpen());

		ContextMenu menu = model.getMenu();
		java.awt.Rectangle releaseEntry = menu.entryRect(0); // "Release placeholder"

		boolean changed = model.activateMenu(releaseEntry.x, releaseEntry.y);

		assertTrue(changed);
		assertFalse(model.isMenuOpen());
		assertTrue(layout.getMainTab().getSlots().contains(1));
		assertFalse(layout.getMainTab().getSlots().contains(2));
	}

	@Test
	public void activateMenuCancelDoesNotChangeLayout()
	{
		layout.getMainTab().getSlots().add(1);
		model.setSnapshots(Collections.emptyList());
		model.rebuild();

		java.awt.Rectangle placeholderRect = model.slotRect(0);
		model.openMenu(placeholderRect.x, placeholderRect.y);

		ContextMenu menu = model.getMenu();
		java.awt.Rectangle cancelEntry = menu.entryRect(menu.getEntries().size() - 1);

		boolean changed = model.activateMenu(cancelEntry.x, cancelEntry.y);

		assertFalse(changed);
		assertFalse(model.isMenuOpen());
		assertTrue(layout.getMainTab().getSlots().contains(1));
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

	// ---- misc getters ----

	@Test
	public void getStripLengthCountsAllPlusTabsPlusPlusButtonUnderCap()
	{
		assertEquals(3, model.getStripLength()); // All + Main + plus button
		for (int i = 1; i <= 8; i++)
		{
			layout.createTab(1000 + i);
		}
		assertEquals(11, model.getStripLength()); // All + 9 tabs + plus (9 < MAX_TABS)
		layout.createTab(9999);
		assertEquals(11, model.getStripLength()); // All + 10 tabs, no plus (cap reached)
	}

	@Test
	public void setVisibleRowsClampsToRange()
	{
		model.setVisibleRows(1);
		assertEquals(BankGeometry.MIN_ROWS, model.getVisibleRows());
		model.setVisibleRows(100);
		assertEquals(BankGeometry.MAX_ROWS, model.getVisibleRows());
	}
}
