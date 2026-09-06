package io.robrichardson.banklessbank.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/** Behaviour tests for {@link BankLayout} against the real implementation. */
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
	public void createTabAddsUpToEightCustomTabsPlusMain()
	{
		for (int i = 1; i < BankLayout.MAX_TABS; i++)
		{
			int idx = layout.createTab(1000 + i);
			assertEquals(i, idx);
		}
		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());
	}

	@Test
	public void createTabReturnsMinusOneWhenTabLimitReached()
	{
		for (int i = 1; i < BankLayout.MAX_TABS; i++)
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
		layout.getMainTab().append(5);
		int idx = layout.createTab(5);
		assertEquals(1, idx);
		assertFalse(layout.getMainTab().contains(5));
		assertEquals(Arrays.asList(5), layout.getTab(idx).itemIds());
	}

	// ---- add/remove tab ----

	@Test
	public void deleteTabAppendsTheDeletedTabsItemsWithoutCarryingItsGaps()
	{
		layout.getMainTab().append(1);
		int idx = layout.createTab(2);
		layout.getTab(idx).setAt(5, 3);

		layout.deleteTab(idx);

		assertEquals(1, layout.getTabs().size());
		assertEquals(Arrays.asList(1, 2, 3), layout.getMainTab().itemIds());
	}

	@Test
	public void deleteTabIgnoresMainTabIndex()
	{
		layout.getMainTab().append(1);
		layout.deleteTab(0);
		assertEquals(1, layout.getTabs().size());
		assertEquals(Arrays.asList(1), layout.getMainTab().itemIds());
	}

	@Test
	public void deleteTabIgnoresTheMainTabRegardlessOfPosition()
	{
		layout.createTab(1);
		layout.moveTab(0, layout.getTabs().size() - 1);
		int mainIndex = layout.indexOfMainTab();
		layout.getMainTab().append(2);

		layout.deleteTab(mainIndex);

		assertEquals(2, layout.getTabs().size());
		assertTrue(layout.getMainTab().contains(2));
	}

	@Test
	public void deleteTabIgnoresOutOfRangeIndex()
	{
		layout.deleteTab(5);
		assertEquals(1, layout.getTabs().size());
	}

	// ---- placeItem: move / swap semantics ----

	@Test
	public void placeItemOnAnEmptySlotMovesTheItemThere()
	{
		layout.getMainTab().setAt(0, 1);

		boolean changed = layout.placeItem(1, 0, 5);

		assertTrue(changed);
		assertNull(layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(5));
	}

	@Test
	public void placeItemOnAnOccupiedSlotSwapsTheTwoItems()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 3);

		boolean changed = layout.placeItem(1, 0, 2);

		assertTrue(changed);
		assertEquals(Integer.valueOf(3), layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(2));
	}

	@Test
	public void placeItemSwapsAcrossTabs()
	{
		layout.getMainTab().setAt(0, 1);
		int other = layout.createTab(10);
		layout.getTab(other).setAt(2, 10);

		boolean changed = layout.placeItem(1, other, 2);

		assertTrue(changed);
		assertEquals(Integer.valueOf(10), layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(1), layout.getTab(other).itemAt(2));
	}

	@Test
	public void placeItemOnItsOwnSlotIsANoOp()
	{
		layout.getMainTab().setAt(2, 1);

		boolean changed = layout.placeItem(1, 0, 2);

		assertFalse(changed);
		assertEquals(Integer.valueOf(1), layout.getMainTab().itemAt(2));
	}

	@Test
	public void placeItemFromOutsideAnyTabSendsTheOccupantToTheAppendIndex()
	{
		layout.getMainTab().setAt(0, 5);

		boolean changed = layout.placeItem(999, 0, 0);

		assertTrue(changed);
		assertEquals(Integer.valueOf(999), layout.getMainTab().itemAt(0));
		assertTrue(layout.getMainTab().contains(5));
		assertFalse(layout.getMainTab().indexOf(5) == 0);
	}

	@Test
	public void placeItemLeavesInteriorGapsUntouched()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		layout.getMainTab().setAt(4, 3);

		layout.placeItem(1, 0, 6);

		assertNull(layout.getMainTab().itemAt(1));
		assertNull(layout.getMainTab().itemAt(3));
		assertEquals(Integer.valueOf(2), layout.getMainTab().itemAt(2));
		assertEquals(Integer.valueOf(3), layout.getMainTab().itemAt(4));
	}

	@Test
	public void placeItemIsANoOpForAnOutOfRangeTabOrNegativeSlot()
	{
		layout.getMainTab().append(1);

		assertFalse(layout.placeItem(1, 5, 0));
		assertFalse(layout.placeItem(1, 0, -1));
	}

	@Test
	public void placeItemIsANoOpForASlotAtOrAboveMaxSlots()
	{
		layout.getMainTab().append(1);

		assertFalse(layout.placeItem(1, 0, BankTab.MAX_SLOTS));
	}

	@Test
	public void placeItemLeavingACustomTabEmptyDeletesIt()
	{
		int other = layout.createTab(10);

		layout.placeItem(10, 0, 0);

		assertEquals(1, layout.getTabs().size());
		assertTrue(layout.getMainTab().contains(10));
	}

	@Test
	public void placeItemNeverLeavesAnItemInTwoTabs()
	{
		layout.getMainTab().append(1);
		int other = layout.createTab(10);

		layout.placeItem(1, other, 0);

		int occurrences = 0;
		for (BankTab tab : layout.getTabs())
		{
			occurrences += (int) tab.itemIds().stream().filter(id -> id == 1).count();
		}
		assertEquals(1, occurrences);
	}

	@Test
	public void moveItemToTabAppendsAfterTheLastOccupiedSlot()
	{
		int other = layout.createTab(10);
		layout.getTab(other).setAt(3, 20);
		layout.getMainTab().append(1);

		layout.moveItemToTab(1, other);

		assertEquals(Integer.valueOf(1), layout.getTab(other).itemAt(4));
	}

	// ---- moveTab ----

	@Test
	public void moveTabReordersCustomTabs()
	{
		int a = layout.createTab(1);
		int b = layout.createTab(2);
		int c = layout.createTab(3);

		boolean changed = layout.moveTab(a, c);

		assertTrue(changed);
		List<BankTab> tabs = layout.getTabs();
		assertEquals("Main", tabs.get(0).getName());
		assertTrue(tabs.get(1).contains(2));
		assertTrue(tabs.get(2).contains(3));
		assertTrue(tabs.get(3).contains(1));
	}

	@Test
	public void moveTabCanMoveTheMainTabToTheEnd()
	{
		layout.createTab(1);
		layout.createTab(2);

		boolean changed = layout.moveTab(0, layout.getTabs().size() - 1);

		assertTrue(changed);
		List<BankTab> tabs = layout.getTabs();
		assertEquals("Main", tabs.get(tabs.size() - 1).getName());
		assertTrue(tabs.get(tabs.size() - 1).isMain());
		assertEquals(tabs.size() - 1, layout.indexOfMainTab());
		// Still the one and only main tab, just relocated.
		assertFalse(tabs.get(0).isMain());
	}

	@Test
	public void moveTabMainToEndAndBackRestoresTheOriginalOrder()
	{
		int a = layout.createTab(1);
		int b = layout.createTab(2);

		layout.moveTab(0, layout.getTabs().size() - 1);
		int mainIndex = layout.indexOfMainTab();
		boolean changed = layout.moveTab(mainIndex, 0);

		assertTrue(changed);
		assertEquals(0, layout.indexOfMainTab());
		assertTrue(layout.getTab(0).isMain());
		assertTrue(layout.getTab(1).contains(1));
		assertTrue(layout.getTab(2).contains(2));
	}

	@Test
	public void moveTabCanDisplaceMainFromIndexZero()
	{
		layout.createTab(1);
		layout.createTab(2);

		boolean changed = layout.moveTab(2, 0);

		assertTrue(changed);
		assertEquals("Main", layout.getMainTab().getName());
		assertTrue(layout.getMainTab().isMain());
		assertFalse(layout.getTab(0).isMain());
		assertTrue(layout.getTab(0).contains(2));
	}

	@Test
	public void appendGoesToTheMainTabWhereverItSitsInTheStrip()
	{
		layout.createTab(1);
		layout.moveTab(0, layout.getTabs().size() - 1);
		int mainIndex = layout.indexOfMainTab();

		layout.sync(setOf(1, 2), true);

		// 1 already lives in the non-main tab created above; 2 is new and must land in main,
		// wherever main currently sits, never at list position 0.
		assertTrue(layout.getTab(mainIndex).contains(2));
		assertFalse(layout.getTab(0).contains(2));
	}

	@Test
	public void moveTabClampsAnOutOfRangeTarget()
	{
		int a = layout.createTab(1);
		layout.createTab(2);

		boolean changed = layout.moveTab(a, 999);

		assertTrue(changed);
		assertTrue(layout.getTab(layout.getTabs().size() - 1).contains(1));
	}

	@Test
	public void moveTabReturnsFalseWhenTheOrderIsUnchanged()
	{
		int a = layout.createTab(1);
		layout.createTab(2);

		boolean changed = layout.moveTab(a, a);

		assertFalse(changed);
	}

	// ---- tab icon ----

	@Test
	public void setTabIconAcceptsAnyItemInThatTab()
	{
		int tab = layout.createTab(1);
		layout.getTab(tab).append(2);

		boolean changed = layout.setTabIcon(tab, 2);

		assertTrue(changed);
		assertEquals(2, layout.getTab(tab).getIcon());
	}

	@Test
	public void setTabIconRejectsAnItemFromAnotherTab()
	{
		int tab = layout.createTab(1);
		layout.getMainTab().append(2);

		boolean changed = layout.setTabIcon(tab, 2);

		assertFalse(changed);
		assertEquals(-1, layout.getTab(tab).getIcon());
	}

	@Test
	public void setTabIconWithMinusOneClearsBackToTheFirstItem()
	{
		int tab = layout.createTab(1);
		layout.getTab(tab).append(2);
		layout.setTabIcon(tab, 2);

		boolean changed = layout.setTabIcon(tab, -1);

		assertTrue(changed);
		assertEquals(-1, layout.getTab(tab).getIcon());
		assertEquals(1, layout.getTab(tab).getIconItemId());
	}

	// ---- renameTab ----

	@Test
	public void renameTabSetsTheTrimmedName()
	{
		int tab = layout.createTab(1);

		boolean changed = layout.renameTab(tab, "  Runes  ");

		assertTrue(changed);
		assertEquals("Runes", layout.getTab(tab).getName());
	}

	@Test
	public void renameTabPersistsOnTheTabItself()
	{
		int tab = layout.createTab(1);
		layout.renameTab(tab, "Runes");

		// A fresh read of the same tab object sees the rename - it is a plain field, no separate store.
		assertEquals("Runes", layout.getTab(tab).getName());
		assertEquals("Runes", layout.getTabs().get(tab).getName());
	}

	@Test
	public void renameTabCapsAtTwentyCharacters()
	{
		int tab = layout.createTab(1);

		layout.renameTab(tab, "This name is definitely longer than twenty characters");

		assertEquals(20, layout.getTab(tab).getName().length());
		assertEquals("This name is definit", layout.getTab(tab).getName());
	}

	@Test
	public void renameMainTabWithBlankResetsToMain()
	{
		layout.getMainTab().setName("Old name");

		boolean changed = layout.renameTab(layout.indexOfMainTab(), "   ");

		assertTrue(changed);
		assertEquals("Main", layout.getMainTab().getName());
	}

	@Test
	public void renameCustomTabWithEmptyStringResetsToItsDefaultTabName()
	{
		int tab = layout.createTab(1);
		layout.renameTab(tab, "Runes");

		boolean changed = layout.renameTab(tab, "");

		assertTrue(changed);
		assertEquals("Tab " + (tab + 1), layout.getTab(tab).getName());
	}

	@Test
	public void renameTabIsANoOpWhenTheNameDoesNotActuallyChange()
	{
		int tab = layout.createTab(1);
		layout.renameTab(tab, "Runes");

		boolean changed = layout.renameTab(tab, "Runes");

		assertFalse(changed);
	}

	@Test
	public void renameTabIsANoOpForAnOutOfRangeIndex()
	{
		boolean changed = layout.renameTab(99, "Runes");

		assertFalse(changed);
	}

	// ---- placeholder ignore list ----

	@Test
	public void placeholderIgnoreAddRemoveAndQuery()
	{
		assertFalse(layout.isPlaceholderIgnored(5));

		assertTrue(layout.addPlaceholderIgnore(5));
		assertFalse(layout.addPlaceholderIgnore(5));
		assertTrue(layout.isPlaceholderIgnored(5));

		assertTrue(layout.removePlaceholderIgnore(5));
		assertFalse(layout.removePlaceholderIgnore(5));
		assertFalse(layout.isPlaceholderIgnored(5));
	}

	@Test
	public void clearPlaceholderIgnoresRemovesEverythingAndReportsWhetherAnythingChanged()
	{
		assertFalse(layout.clearPlaceholderIgnores());

		layout.addPlaceholderIgnore(5);
		layout.addPlaceholderIgnore(6);

		assertTrue(layout.clearPlaceholderIgnores());
		assertTrue(layout.getPlaceholderIgnoreIds().isEmpty());
		assertFalse(layout.clearPlaceholderIgnores());
	}

	@Test
	public void placeholderIgnoreIdsSurviveNormalise()
	{
		layout.addPlaceholderIgnore(5);
		layout.addPlaceholderIgnore(6);

		layout.normalise();

		assertTrue(layout.isPlaceholderIgnored(5));
		assertTrue(layout.isPlaceholderIgnored(6));
	}

	@Test
	public void normaliseRepairsANullPlaceholderIgnoreSet()
	{
		// Gson's reflective adapter can still write a raw null into the field (e.g. an explicit
		// "placeholderIgnoreIds": null in the saved JSON), bypassing the constructor's default.
		// normalise() must be defensive about that even though our own writer never produces it.
		try
		{
			java.lang.reflect.Field field = BankLayout.class.getDeclaredField("placeholderIgnoreIds");
			field.setAccessible(true);
			field.set(layout, null);
		}
		catch (ReflectiveOperationException e)
		{
			throw new RuntimeException(e);
		}

		layout.normalise();

		assertTrue(layout.getPlaceholderIgnoreIds().isEmpty());
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
	public void normaliseKeepsInteriorGapsAndTrimsTrailingOnes()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		layout.getMainTab().getSlots().add(null);
		layout.getMainTab().getSlots().add(null);

		layout.normalise();

		assertEquals(Arrays.asList(1, null, 2), layout.getMainTab().getSlots());
	}

	@Test
	public void normaliseBlanksDuplicateIdsInPlaceRatherThanShiftingLaterSlots()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		BankTab dupeTab = new BankTab("Dupe");
		dupeTab.setAt(0, 2);
		dupeTab.setAt(1, 3);
		layout.getTabs().add(dupeTab);

		layout.normalise();

		assertEquals(Arrays.asList(1, null, 2), layout.getMainTab().getSlots());
		assertEquals(Arrays.asList(null, 3), dupeTab.getSlots());
	}

	@Test
	public void normaliseTruncatesATabBeyondMaxSlots()
	{
		// setAt rejects an index at/above MAX_SLOTS, so a corrupt over-long tab can only arrive via
		// deserialisation; simulate that directly on the backing list.
		List<Integer> oversized = new java.util.ArrayList<>();
		for (int i = 0; i < BankTab.MAX_SLOTS + 5; i++)
		{
			oversized.add(null);
		}
		oversized.set(BankTab.MAX_SLOTS + 2, 1);
		layout.getMainTab().setSlots(oversized);
		assertTrue(layout.getMainTab().getSlots().size() > BankTab.MAX_SLOTS);

		layout.normalise();

		assertTrue(layout.getMainTab().getSlots().size() <= BankTab.MAX_SLOTS);
	}

	@Test
	public void normaliseMergesSurplusTabsPastMaxTabsIntoTheMainTabRatherThanDroppingThem()
	{
		// A layout saved before MAX_TABS dropped from 10 to 9: nine custom tabs plus Main (ten
		// tabs total). createTab itself now enforces the new cap, so a saved 10-tab layout can only
		// arrive via deserialisation; build one directly on the backing list, as Gson would.
		for (int i = 1; i <= BankLayout.MAX_TABS; i++)
		{
			BankTab tab = new BankTab("Tab " + i);
			tab.append(1000 + i);
			layout.getTabs().add(tab);
		}
		assertEquals(BankLayout.MAX_TABS + 1, layout.getTabs().size());

		layout.normalise();

		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());
		assertTrue("the surplus (tenth) tab's item must survive, not vanish",
			layout.getMainTab().contains(1000 + BankLayout.MAX_TABS));
	}

	@Test(timeout = 5000)
	public void normaliseMergesSurplusTabsWhenTheMainTabIsSavedLast()
	{
		// The truncation loop deletes from the end, and deleteTab refuses the main tab. With main
		// dragged to the end of the strip before saving, the loop has to skip back past it or it
		// would spin forever on a delete that never happens.
		BankTab main = layout.getMainTab();
		main.append(1);
		layout.getTabs().remove(main);
		for (int i = 1; i <= BankLayout.MAX_TABS + 2; i++)
		{
			BankTab tab = new BankTab("Tab " + i);
			tab.append(1000 + i);
			layout.getTabs().add(tab);
		}
		layout.getTabs().add(main);
		assertEquals(BankLayout.MAX_TABS + 3, layout.getTabs().size());

		layout.normalise();

		assertEquals(BankLayout.MAX_TABS, layout.getTabs().size());
		assertEquals("main must still be exactly one tab, and still the one it was",
			main, layout.getMainTab());
		assertEquals(BankLayout.MAX_TABS - 1, layout.indexOfMainTab());
		for (int i = BankLayout.MAX_TABS; i <= BankLayout.MAX_TABS + 2; i++)
		{
			assertTrue("surplus tab " + i + "'s item must merge into main, not vanish",
				layout.getMainTab().contains(1000 + i));
		}
		assertTrue("main's own item must survive the merge", layout.getMainTab().contains(1));
	}

	@Test
	public void normaliseClearsAnIconTheTabNoLongerHolds()
	{
		int tab = layout.createTab(1);
		layout.getTab(tab).append(2);
		layout.setTabIcon(tab, 2);
		layout.getTab(tab).removeItem(2);

		layout.normalise();

		assertEquals(-1, layout.getTab(tab).getIcon());
	}

	@Test
	public void normaliseHandlesNullSlotsList()
	{
		layout.getMainTab().setSlots(null);

		layout.normalise();

		List<Integer> slots = layout.getMainTab().getSlots();
		assertTrue(slots != null && slots.isEmpty());
	}

	@Test
	public void normaliseDropsANullTabEntryInsteadOfThrowing()
	{
		layout.getTabs().add(null);
		layout.getMainTab().setAt(0, 1);

		layout.normalise();

		assertEquals(1, layout.getTabs().size());
		assertEquals(Collections.singletonList(1), layout.getMainTab().getSlots());
	}

	@Test
	public void normaliseDropsANullMainTabAndRestoresARealOne()
	{
		layout.getTabs().set(0, null);

		layout.normalise();

		assertEquals(1, layout.getTabs().size());
		assertNotNull(layout.getMainTab());
		assertTrue(layout.getMainTab().getSlots().isEmpty());
	}

	@Test
	public void normaliseFlagsSlotZeroMainWhenNoTabCarriesTheFlag()
	{
		// Simulates JSON saved before the main flag existed: no tab has it set.
		layout.getTabs().get(0).setMain(false);
		layout.createTab(1);

		layout.normalise();

		assertEquals(0, layout.indexOfMainTab());
		assertTrue(layout.getTab(0).isMain());
	}

	@Test
	public void normaliseKeepsExactlyOneMainTabWhenCorruptStateFlagsTwo()
	{
		layout.createTab(1);
		layout.getTab(1).setMain(true);

		layout.normalise();

		int mainCount = 0;
		for (BankTab tab : layout.getTabs())
		{
			if (tab.isMain())
			{
				mainCount++;
			}
		}
		assertEquals(1, mainCount);
		assertEquals(0, layout.indexOfMainTab());
	}

	@Test
	public void normalisePreservesMainFlagWhenMainIsNotFirst()
	{
		layout.createTab(1);
		layout.moveTab(0, 1);
		int mainIndex = layout.indexOfMainTab();

		layout.normalise();

		assertEquals(mainIndex, layout.indexOfMainTab());
		assertTrue(layout.getTab(mainIndex).isMain());
	}

	@Test
	public void normaliseReplacesANullTabNameWithItsDefault()
	{
		layout.getMainTab().setName(null);

		layout.normalise();

		assertEquals("Main", layout.getMainTab().getName());
	}

	@Test
	public void normaliseReplacesABlankTabNameWithItsDefault()
	{
		layout.createTab(1);
		layout.getTab(1).setName("   ");

		layout.normalise();

		assertEquals("Tab 2", layout.getTab(1).getName());
	}

	// ---- sync ----

	@Test
	public void syncKeepsIdInLayoutAsPlaceholderWhenNoLongerOwned()
	{
		layout.getMainTab().append(1);

		boolean changed = layout.sync(setOf(), true);

		assertFalse(changed);
		assertTrue(layout.getMainTab().contains(1));
	}

	@Test
	public void syncAppendsNewItemsAfterTheLastOccupiedSlotNotIntoAGap()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);

		boolean changed = layout.sync(setOf(1, 2, 9), true);

		assertTrue(changed);
		assertEquals(Integer.valueOf(9), layout.getMainTab().itemAt(4));
	}

	@Test
	public void syncWithPlaceholdersOffBlanksTheSlotAndKeepsLaterItemsInPlace()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(1, 2);

		boolean changed = layout.sync(setOf(2), false);

		assertTrue(changed);
		assertNull(layout.getMainTab().itemAt(0));
		assertEquals(Integer.valueOf(2), layout.getMainTab().itemAt(1));
	}

	@Test
	public void syncWithPlaceholdersDisabledRemovesEmptiedCustomTab()
	{
		layout.createTab(5);

		boolean changed = layout.sync(setOf(), false);

		assertTrue(changed);
		assertEquals(1, layout.getTabs().size());
	}

	@Test
	public void syncReturnsFalseWhenNothingChanges()
	{
		layout.getMainTab().append(1);

		boolean changed = layout.sync(setOf(1), true);

		assertFalse(changed);
	}

	@Test
	public void syncDoesNotDuplicateAlreadyPlacedIds()
	{
		int other = layout.createTab(5);

		boolean changed = layout.sync(setOf(5), true);

		assertFalse(changed);
		assertEquals(Arrays.asList(5), layout.getTab(other).itemIds());
		assertFalse(layout.getMainTab().contains(5));
	}

	@Test
	public void syncDoesNotRemoveAnIgnoredPlaceholderFromTheLayout()
	{
		layout.getMainTab().append(1);
		layout.addPlaceholderIgnore(1);

		boolean changed = layout.sync(setOf(), false);

		assertTrue(changed);
		assertFalse(layout.getMainTab().contains(1));
	}

	// ---- release placeholder per item / per tab ----

	@Test
	public void releasePlaceholderRemovesUnownedItem()
	{
		layout.getMainTab().append(1);

		boolean released = layout.releasePlaceholder(1, setOf());

		assertTrue(released);
		assertFalse(layout.getMainTab().contains(1));
	}

	@Test
	public void releasePlaceholderDoesNotRemoveOwnedItem()
	{
		layout.getMainTab().append(1);

		boolean released = layout.releasePlaceholder(1, setOf(1));

		assertFalse(released);
		assertTrue(layout.getMainTab().contains(1));
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
		layout.createTab(5);

		layout.releasePlaceholder(5, setOf());

		assertEquals(1, layout.getTabs().size());
	}

	@Test
	public void releaseAllPlaceholdersInSingleTabOnlyAffectsThatTab()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		int other = layout.createTab(3);
		layout.getTab(other).append(4);

		int released = layout.releaseAllPlaceholders(0, setOf());

		assertEquals(2, released);
		assertTrue(layout.getMainTab().isEmpty());
		assertEquals(Arrays.asList(3, 4), layout.getTab(1).itemIds());
	}

	@Test
	public void releaseAllPlaceholdersWithIndexMinusOneAffectsEveryTab()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		int other = layout.createTab(3);
		layout.getTab(other).append(4);

		int released = layout.releaseAllPlaceholders(-1, setOf(2));

		assertEquals(3, released);
		assertEquals(Arrays.asList(2), layout.getMainTab().itemIds());
	}

	@Test
	public void releaseAllPlaceholdersKeepsOwnedItems()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		layout.getMainTab().append(3);

		int released = layout.releaseAllPlaceholders(-1, setOf(2));

		assertEquals(2, released);
		assertEquals(Arrays.asList(2), layout.getMainTab().itemIds());
	}

	// ---- indexOfTab ----

	@Test
	public void indexOfTabFindsContainingTab()
	{
		int other = layout.createTab(5);
		assertEquals(other, layout.indexOfTab(5));
		assertEquals(-1, layout.indexOfTab(999));
	}

	// ---- compactTab ----

	@Test
	public void compactTabRemovesInteriorGapsPreservingOrder()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);
		layout.getMainTab().setAt(5, 3);

		boolean changed = layout.compactTab(layout.indexOfMainTab(), BankTab.DEFAULT_COLS);

		assertTrue(changed);
		assertEquals(Arrays.asList(1, 2, 3), layout.getMainTab().getSlots());
	}

	@Test
	public void compactTabKeepsAnIgnoredPlaceholderIdInItsRelativePosition()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 999);
		layout.getMainTab().setAt(4, 2);
		layout.addPlaceholderIgnore(999);

		layout.compactTab(layout.indexOfMainTab(), BankTab.DEFAULT_COLS);

		assertEquals(Arrays.asList(1, 999, 2), layout.getMainTab().getSlots());
		assertTrue(layout.isPlaceholderIgnored(999));
	}

	@Test
	public void compactTabTrimsTrailingBlanks()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(4, 2);
		layout.getMainTab().getSlots().add(null);

		layout.compactTab(layout.indexOfMainTab(), BankTab.DEFAULT_COLS);

		assertEquals(Arrays.asList(1, 2), layout.getMainTab().getSlots());
	}

	@Test
	public void compactTabIsANoOpOnAnAlreadyDenseTab()
	{
		layout.getMainTab().append(1);
		layout.getMainTab().append(2);
		layout.getMainTab().append(3);

		boolean changed = layout.compactTab(layout.indexOfMainTab(), BankTab.DEFAULT_COLS);

		assertFalse(changed);
		assertEquals(Arrays.asList(1, 2, 3), layout.getMainTab().getSlots());
	}

	@Test
	public void compactTabDoesNotTouchOtherTabs()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);
		int other = layout.createTab(5);
		layout.getTab(other).setAt(2, 6);

		layout.compactTab(layout.indexOfMainTab(), BankTab.DEFAULT_COLS);

		assertEquals(Arrays.asList(5, null, 6), layout.getTab(other).getSlots());
	}

	@Test
	public void compactTabIsANoOpForAnOutOfRangeIndex()
	{
		assertFalse(layout.compactTab(-1, BankTab.DEFAULT_COLS));
		assertFalse(layout.compactTab(99, BankTab.DEFAULT_COLS));
	}

	// ---- addItem (manual add via the bottom bar's item search) --------------------------------

	@Test
	public void addItemAppendsAfterTheLastOccupiedSlotOfTheTargetTab()
	{
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(3, 2);

		assertTrue(layout.addItem(99, layout.indexOfMainTab()));

		assertEquals(Arrays.asList(1, null, null, 2, 99), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemAppendsToAnEmptyTabAtSlotZero()
	{
		assertTrue(layout.addItem(99, layout.indexOfMainTab()));

		assertEquals(Collections.singletonList(99), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemTargetsTheGivenTabNotTheMainOne()
	{
		int other = layout.createTab(5);

		assertTrue(layout.addItem(99, other));

		assertEquals(Arrays.asList(5, 99), layout.getTab(other).getSlots());
		assertEquals(Collections.emptyList(), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemIsANoOpForAnIdAlreadyInTheLayout()
	{
		int other = layout.createTab(5);

		assertFalse(layout.addItem(5, layout.indexOfMainTab()));

		assertEquals(Collections.emptyList(), layout.getMainTab().getSlots());
		assertEquals(Collections.singletonList(5), layout.getTab(other).getSlots());
		assertEquals(other, layout.indexOfTab(5));
	}

	@Test
	public void addItemIsANoOpForAnIdAlreadyInTheSameTab()
	{
		layout.getMainTab().append(7);

		assertFalse(layout.addItem(7, layout.indexOfMainTab()));

		assertEquals(Collections.singletonList(7), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemIsANoOpForANonPositiveIdOrAnOutOfRangeTab()
	{
		assertFalse(layout.addItem(0, layout.indexOfMainTab()));
		assertFalse(layout.addItem(-3, layout.indexOfMainTab()));
		assertFalse(layout.addItem(99, -1));
		assertFalse(layout.addItem(99, 99));
		assertEquals(Collections.emptyList(), layout.getMainTab().getSlots());
	}

	@Test
	public void addItemIsANoOpWhenTheTabIsFull()
	{
		BankTab main = layout.getMainTab();
		main.setAt(main.maxSlots() - 1, 1);

		assertFalse(layout.addItem(99, layout.indexOfMainTab()));
		assertEquals(-1, layout.indexOfTab(99));
	}

	@Test
	public void addedItemBehavesLikeAnyOtherSlotAfterwards()
	{
		layout.getMainTab().append(1);
		layout.addItem(99, layout.indexOfMainTab());

		assertTrue(layout.placeItem(99, layout.indexOfMainTab(), 0));

		assertEquals(Arrays.asList(99, 1), layout.getMainTab().getSlots());
	}
}
