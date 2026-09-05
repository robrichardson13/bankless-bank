package io.robrichardson.banklessbank.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/** Behaviour tests for {@link BankLayout} and {@link BankTab} against the real implementation. */
public class BankLayoutTest
{
	private BankLayout layout;

	@Before
	public void setUp()
	{
		layout = new BankLayout();
	}

	private static Set<Integer> setOf(Integer... ids)
	{
		return new LinkedHashSet<>(Arrays.asList(ids));
	}

	// ---- construction / main tab ----

	@Test
	public void newLayoutHasOnlyMainTab()
	{
		assertEquals(1, layout.getTabs().size());
		assertEquals("Main", layout.getMainTab().getName());
		assertTrue(layout.getMainTab().getSlots().isEmpty());
	}

	// ---- custom tab cap ----

	@Test
	public void createTabAddsUpToNineCustomTabsPlusMain()
	{
		for (int i = 1; i <= 9; i++)
		{
			int idx = layout.createTab(1000 + i);
			assertEquals(i, idx);
		}
		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());
	}

	@Test
	public void createTabReturnsMinusOneWhenTabLimitReached()
	{
		for (int i = 1; i <= 9; i++)
		{
			layout.createTab(1000 + i);
		}
		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());

		int result = layout.createTab(9999);
		assertEquals(-1, result);
		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());
	}

	@Test
	public void createTabDetachesItemFromItsPreviousTab()
	{
		layout.getMainTab().getSlots().add(5);
		int idx = layout.createTab(5);
		assertEquals(1, idx);
		assertFalse(layout.getMainTab().getSlots().contains(5));
		assertEquals(Arrays.asList(5), layout.getTab(idx).getSlots());
	}

	// ---- add/remove tab ----

	@Test
	public void deleteTabMovesItemsToEndOfMainTab()
	{
		layout.getMainTab().getSlots().add(1);
		int idx = layout.createTab(2);
		layout.getTab(idx).getSlots().add(3);

		layout.deleteTab(idx);

		assertEquals(1, layout.getTabs().size());
		assertEquals(Arrays.asList(1, 2, 3), layout.getMainTab().getSlots());
	}

	@Test
	public void deleteTabIgnoresMainTabIndex()
	{
		layout.getMainTab().getSlots().add(1);
		layout.deleteTab(0);
		assertEquals(1, layout.getTabs().size());
		assertEquals(Arrays.asList(1), layout.getMainTab().getSlots());
	}

	@Test
	public void deleteTabIgnoresOutOfRangeIndex()
	{
		layout.deleteTab(5);
		assertEquals(1, layout.getTabs().size());
	}

	// ---- moveItem: insert semantics within and across tabs ----

	@Test
	public void moveItemInsertsAtGivenSlotWithinSameTab()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2, 3));

		layout.moveItem(3, 0, 0);

		assertEquals(Arrays.asList(3, 1, 2), layout.getMainTab().getSlots());
	}

	@Test
	public void moveItemWithinSameTabAdjustsForRemovalShift()
	{
		// Moving an item to a later index in the same tab should land just before that index,
		// accounting for the item's own removal shifting everything after it left by one.
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2, 3, 4));

		layout.moveItem(1, 0, 3);

		assertEquals(Arrays.asList(2, 3, 1, 4), layout.getMainTab().getSlots());
	}

	@Test
	public void moveItemAcrossTabsInsertsAtTargetSlot()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));
		int other = layout.createTab(10);
		layout.getTab(other).getSlots().add(11);

		layout.moveItem(1, other, 0);

		assertEquals(Arrays.asList(2), layout.getMainTab().getSlots());
		assertEquals(Arrays.asList(1, 10, 11), layout.getTab(other).getSlots());
	}

	@Test
	public void moveItemPastEndOfTabAppends()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));

		layout.moveItem(1, 0, 999);

		assertEquals(Arrays.asList(2, 1), layout.getMainTab().getSlots());
	}

	@Test
	public void moveItemToOutOfRangeTabIsNoOp()
	{
		layout.getMainTab().getSlots().add(1);

		layout.moveItem(1, 5, 0);

		assertEquals(Arrays.asList(1), layout.getMainTab().getSlots());
	}

	@Test
	public void moveItemLeavingCustomTabEmptyDeletesIt()
	{
		int other = layout.createTab(10);
		layout.moveItem(10, 0, 0);

		assertEquals(1, layout.getTabs().size());
		assertTrue(layout.getMainTab().getSlots().contains(10));
	}

	@Test
	public void moveItemToTabAppendsAtEnd()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));
		int other = layout.createTab(10);

		layout.moveItemToTab(2, other);

		assertEquals(Arrays.asList(1), layout.getMainTab().getSlots());
		assertEquals(Arrays.asList(10, 2), layout.getTab(other).getSlots());
	}

	// ---- no duplicate ids across tabs (ordering invariant) ----

	@Test
	public void moveItemNeverLeavesItemInTwoTabs()
	{
		layout.getMainTab().getSlots().add(1);
		int other = layout.createTab(10);

		layout.moveItem(1, other, 0);

		int occurrences = 0;
		for (BankTab tab : layout.getTabs())
		{
			occurrences += (int) tab.getSlots().stream().filter(id -> id == 1).count();
		}
		assertEquals(1, occurrences);
	}

	// ---- placeholder semantics ----

	@Test
	public void syncKeepsIdInLayoutAsPlaceholderWhenNoLongerOwned()
	{
		layout.getMainTab().getSlots().add(1);

		boolean changed = layout.sync(setOf(), true);

		assertFalse(changed);
		assertTrue(layout.getMainTab().getSlots().contains(1));
	}

	@Test
	public void syncRemovesUnownedIdWhenPlaceholdersDisabled()
	{
		layout.getMainTab().getSlots().add(1);

		boolean changed = layout.sync(setOf(), false);

		assertTrue(changed);
		assertFalse(layout.getMainTab().getSlots().contains(1));
	}

	@Test
	public void syncAppendsNewlyOwnedIdsToMainTab()
	{
		boolean changed = layout.sync(setOf(1, 2), true);

		assertTrue(changed);
		assertEquals(Arrays.asList(1, 2), layout.getMainTab().getSlots());
	}

	@Test
	public void syncDoesNotDuplicateAlreadyPlacedIds()
	{
		int other = layout.createTab(5);

		boolean changed = layout.sync(setOf(5), true);

		assertFalse(changed);
		assertEquals(Arrays.asList(5), layout.getTab(other).getSlots());
		assertFalse(layout.getMainTab().getSlots().contains(5));
	}

	@Test
	public void syncWithPlaceholdersDisabledRemovesEmptiedCustomTab()
	{
		int other = layout.createTab(5);

		boolean changed = layout.sync(setOf(), false);

		assertTrue(changed);
		assertEquals(1, layout.getTabs().size());
	}

	@Test
	public void syncReturnsFalseWhenNothingChanges()
	{
		layout.getMainTab().getSlots().add(1);

		boolean changed = layout.sync(setOf(1), true);

		assertFalse(changed);
	}

	// ---- release placeholder per item / per tab ----

	@Test
	public void releasePlaceholderRemovesUnownedItem()
	{
		layout.getMainTab().getSlots().add(1);

		boolean released = layout.releasePlaceholder(1, setOf());

		assertTrue(released);
		assertFalse(layout.getMainTab().getSlots().contains(1));
	}

	@Test
	public void releasePlaceholderDoesNotRemoveOwnedItem()
	{
		layout.getMainTab().getSlots().add(1);

		boolean released = layout.releasePlaceholder(1, setOf(1));

		assertFalse(released);
		assertTrue(layout.getMainTab().getSlots().contains(1));
	}

	@Test
	public void releasePlaceholderReturnsFalseWhenItemNotInAnyTab()
	{
		boolean released = layout.releasePlaceholder(42, setOf());
		assertFalse(released);
	}

	@Test
	public void releasePlaceholderDeletesTabLeftEmpty()
	{
		int other = layout.createTab(5);

		layout.releasePlaceholder(5, setOf());

		assertEquals(1, layout.getTabs().size());
	}

	@Test
	public void releaseAllPlaceholdersInSingleTabOnlyAffectsThatTab()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));
		int other = layout.createTab(3);
		layout.getTab(other).getSlots().add(4);

		int released = layout.releaseAllPlaceholders(0, setOf());

		assertEquals(2, released);
		assertTrue(layout.getMainTab().getSlots().isEmpty());
		// custom tab untouched
		assertEquals(Arrays.asList(3, 4), layout.getTab(1).getSlots());
	}

	@Test
	public void releaseAllPlaceholdersWithIndexMinusOneAffectsEveryTab()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));
		int other = layout.createTab(3);
		layout.getTab(other).getSlots().add(4);

		int released = layout.releaseAllPlaceholders(-1, setOf(2));

		assertEquals(3, released);
		assertEquals(Arrays.asList(2), layout.getMainTab().getSlots());
	}

	@Test
	public void releaseAllPlaceholdersKeepsOwnedItems()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2, 3));

		int released = layout.releaseAllPlaceholders(-1, setOf(2));

		assertEquals(2, released);
		assertEquals(Arrays.asList(2), layout.getMainTab().getSlots());
	}

	// ---- tab icon item ----

	@Test
	public void tabIconIsFirstSlotItem()
	{
		BankTab tab = new BankTab("Custom");
		tab.getSlots().addAll(Arrays.asList(7, 8, 9));

		assertEquals(7, tab.getIconItemId());
	}

	@Test
	public void tabIconIsMinusOneWhenEmpty()
	{
		BankTab tab = new BankTab("Empty");
		assertEquals(-1, tab.getIconItemId());
	}

	// ---- indexOfTab ----

	@Test
	public void indexOfTabFindsContainingTab()
	{
		int other = layout.createTab(5);
		assertEquals(other, layout.indexOfTab(5));
		assertEquals(-1, layout.indexOfTab(999));
	}

	// ---- normalise ----

	@Test
	public void normaliseRestoresMissingMainTab()
	{
		layout.getTabs().clear();

		layout.normalise();

		assertEquals(1, layout.getTabs().size());
		assertEquals("Main", layout.getMainTab().getName());
	}

	@Test
	public void normaliseRemovesDuplicateIdsAcrossTabs()
	{
		layout.getMainTab().getSlots().addAll(Arrays.asList(1, 2));
		BankTab dupeTab = new BankTab("Dupe");
		dupeTab.getSlots().addAll(Arrays.asList(2, 3));
		layout.getTabs().add(dupeTab);

		layout.normalise();

		int occurrencesOfTwo = 0;
		for (BankTab tab : layout.getTabs())
		{
			occurrencesOfTwo += (int) tab.getSlots().stream().filter(id -> id == 2).count();
		}
		assertEquals(1, occurrencesOfTwo);
	}

	@Test
	public void normaliseRemovesNullSlotEntries()
	{
		layout.getMainTab().getSlots().add(null);
		layout.getMainTab().getSlots().add(1);

		layout.normalise();

		assertEquals(Arrays.asList(1), layout.getMainTab().getSlots());
	}

	@Test
	public void normaliseTruncatesTabsBeyondMax()
	{
		for (int i = 1; i <= 9; i++)
		{
			layout.createTab(1000 + i);
		}
		BankTab extra = new BankTab("Extra");
		extra.getSlots().add(9999);
		layout.getTabs().add(extra);
		assertEquals(11, layout.getTabs().size());

		layout.normalise();

		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());
		// the pruned tab's item is preserved by folding into main, per deleteTab's contract
		assertTrue(layout.getMainTab().getSlots().contains(9999));
	}

	@Test
	public void normaliseHandlesNullSlotsList()
	{
		layout.getMainTab().setSlots(null);

		layout.normalise();

		List<Integer> slots = layout.getMainTab().getSlots();
		assertTrue(slots != null && slots.isEmpty());
	}
}
