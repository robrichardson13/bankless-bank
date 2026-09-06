package io.robrichardson.banklessbank.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/** Behaviour tests for the sparse {@link BankTab} slot model. */
public class BankTabTest
{
	private BankTab tab;

	@Before
	public void setUp()
	{
		tab = new BankTab("Test");
	}

	@Test
	public void itemAtReturnsNullForEmptyAndOutOfRangeSlots()
	{
		tab.append(1);
		assertNull(tab.itemAt(5));
		assertNull(tab.itemAt(-1));
		assertNull(tab.itemAt(100));
	}

	@Test
	public void setAtGrowsTheListWithNullsUpToTheIndex()
	{
		tab.setAt(3, 7);
		assertEquals(Arrays.asList(null, null, null, 7), tab.getSlots());
	}

	@Test
	public void setAtIgnoresNegativeIndexAndIndexAtOrAboveMaxSlots()
	{
		tab.setAt(-1, 7);
		assertTrue(tab.getSlots().isEmpty());

		tab.setAt(BankTab.MAX_SLOTS, 7);
		assertTrue(tab.getSlots().isEmpty());
	}

	@Test
	public void setAtNullBlanksTheSlotAndKeepsEarlierGaps()
	{
		tab.setAt(0, 1);
		tab.setAt(1, 2);
		tab.setAt(2, 3);

		tab.setAt(1, null);

		assertEquals(Arrays.asList(1, null, 3), tab.getSlots());
	}

	@Test
	public void setAtTrimsTrailingNullsButKeepsInteriorGaps()
	{
		tab.setAt(0, 1);
		tab.setAt(2, 3);

		tab.setAt(2, null);

		assertEquals(Arrays.asList(1), tab.getSlots());
	}

	@Test
	public void appendPutsTheItemAfterTheLastOccupiedSlotNotInTheFirstGap()
	{
		tab.setAt(0, 1);
		tab.setAt(3, 2);

		tab.append(9);

		assertEquals(Integer.valueOf(9), tab.itemAt(4));
		assertEquals(5, tab.getSlots().size());
	}

	@Test
	public void appendIndexIsZeroForAnEmptyTab()
	{
		assertEquals(0, tab.appendIndex());
	}

	@Test
	public void maxOccupiedIndexIgnoresTrailingGapsAndIsMinusOneWhenEmpty()
	{
		assertEquals(-1, tab.maxOccupiedIndex());

		tab.setAt(0, 1);
		tab.setAt(4, 2);
		assertEquals(4, tab.maxOccupiedIndex());
	}

	@Test
	public void indexOfReturnsTheSlotIndexAcrossGaps()
	{
		tab.setAt(0, 1);
		tab.setAt(3, 2);

		assertEquals(3, tab.indexOf(2));
		assertEquals(-1, tab.indexOf(999));
	}

	@Test
	public void itemIdsSkipsGapsAndPreservesSlotOrder()
	{
		tab.setAt(0, 1);
		tab.setAt(3, 2);
		tab.setAt(5, 3);

		assertEquals(Arrays.asList(1, 2, 3), tab.itemIds());
	}

	@Test
	public void itemCountCountsOccupiedSlotsOnly()
	{
		tab.setAt(0, 1);
		tab.setAt(3, 2);

		assertEquals(2, tab.itemCount());
	}

	@Test
	public void isEmptyIsTrueForATabOfOnlyGaps()
	{
		assertTrue(tab.isEmpty());

		tab.setAt(3, 1);
		assertFalse(tab.isEmpty());

		tab.setAt(3, null);
		assertTrue(tab.isEmpty());
	}

	@Test
	public void removeItemBlanksTheSlotWithoutShiftingLaterItems()
	{
		tab.setAt(0, 1);
		tab.setAt(1, 2);
		tab.setAt(2, 3);

		boolean removed = tab.removeItem(2);

		assertTrue(removed);
		assertEquals(Arrays.asList(1, null, 3), tab.getSlots());
	}

	@Test
	public void removeItemReturnsFalseForAnItemNotInTheTab()
	{
		tab.append(1);
		assertFalse(tab.removeItem(999));
	}

	@Test
	public void getIconItemIdFallsBackToTheFirstOccupiedSlotWhenIconIsUnset()
	{
		tab.setAt(2, 7);
		tab.setAt(5, 8);

		assertEquals(7, tab.getIconItemId());
	}

	@Test
	public void getIconItemIdReturnsTheExplicitIconWhenTheTabStillHoldsIt()
	{
		tab.setAt(0, 7);
		tab.setAt(1, 8);
		tab.setIcon(8);

		assertEquals(8, tab.getIconItemId());
	}

	@Test
	public void getIconItemIdFallsBackWhenTheExplicitIconItemHasLeftTheTab()
	{
		tab.setAt(0, 7);
		tab.setIcon(8);

		assertEquals(7, tab.getIconItemId());
	}

	@Test
	public void getIconItemIdIsMinusOneForAnEmptyTab()
	{
		assertEquals(-1, tab.getIconItemId());
	}

	// ---- compact ----

	@Test
	public void compactPacksItemsDownPreservingOrder()
	{
		tab.setAt(0, 1);
		tab.setAt(3, 2);
		tab.setAt(5, 3);

		boolean changed = tab.compact(BankTab.DEFAULT_COLS);

		assertTrue(changed);
		assertEquals(Arrays.asList(1, 2, 3), tab.getSlots());
	}

	@Test
	public void compactKeepsAnIgnoredPlaceholderIdInItsRelativeOrder()
	{
		// The layout's placeholder-ignore list is orthogonal to BankTab, which only ever stores
		// ids - a hidden, unowned placeholder id is still a plain id here, so it packs down with
		// everything else exactly like an owned one.
		tab.setAt(0, 1);
		tab.setAt(2, 999);
		tab.setAt(4, 2);

		tab.compact(BankTab.DEFAULT_COLS);

		assertEquals(Arrays.asList(1, 999, 2), tab.getSlots());
	}

	@Test
	public void compactTrimsTrailingNullsToo()
	{
		tab.setAt(0, 1);
		tab.setAt(4, 2);
		tab.getSlots().add(null);
		tab.getSlots().add(null);

		tab.compact(BankTab.DEFAULT_COLS);

		assertEquals(Arrays.asList(1, 2), tab.getSlots());
	}

	@Test
	public void compactIsANoOpOnAnAlreadyDenseTab()
	{
		tab.setAt(0, 1);
		tab.setAt(1, 2);
		tab.setAt(2, 3);

		boolean changed = tab.compact(BankTab.DEFAULT_COLS);

		assertFalse(changed);
		assertEquals(Arrays.asList(1, 2, 3), tab.getSlots());
	}

	@Test
	public void compactIsANoOpOnAnEmptyTab()
	{
		boolean changed = tab.compact(BankTab.DEFAULT_COLS);

		assertFalse(changed);
		assertTrue(tab.getSlots().isEmpty());
	}

	// ---- layout width ----

	@Test
	public void aNewTabIsEightColumnsWide()
	{
		assertEquals(BankTab.DEFAULT_COLS, tab.getCols());
		assertEquals(BankTab.MAX_ROWS * BankTab.DEFAULT_COLS, tab.maxSlots());
	}

	@Test
	public void setColsClampsToTheLegalRange()
	{
		tab.setCols(0);
		assertEquals(BankTab.MIN_COLS, tab.getCols());

		tab.setCols(999);
		assertEquals(BankTab.MAX_COLS, tab.getCols());
	}

	@Test
	public void rowAndColumnOfASlotIndexFollowTheTabsOwnWidth()
	{
		tab.setCols(8);
		assertEquals(1, tab.rowOf(9));
		assertEquals(1, tab.colOf(9));
		assertEquals(9, tab.indexAt(1, 1));

		tab.setCols(12);
		assertEquals(0, tab.rowOf(9));
		assertEquals(9, tab.colOf(9));
		assertEquals(21, tab.indexAt(1, 9));
	}

	@Test
	public void wideningReIndexesEveryItemSoItKeepsItsRowAndColumn()
	{
		// Two full 8-wide rows plus a gap, so the re-index has to move every id on row 1 and beyond.
		for (int i = 0; i < 8; i++)
		{
			tab.setAt(i, 100 + i);
		}
		tab.setAt(8, 200);
		tab.setAt(11, 203);

		assertTrue(tab.widenTo(10));

		assertEquals(10, tab.getCols());
		for (int i = 0; i < 8; i++)
		{
			assertEquals("row 0 column " + i, Integer.valueOf(100 + i), tab.itemAt(i));
		}
		assertNull("the two new columns on row 0 are blank", tab.itemAt(8));
		assertNull(tab.itemAt(9));
		assertEquals("(1,0) stays (1,0)", Integer.valueOf(200), tab.itemAt(10));
		assertEquals("(1,3) stays (1,3)", Integer.valueOf(203), tab.itemAt(13));
	}

	@Test
	public void wideningKeepsEveryItemAtTheSameRowAndColumnForEveryTargetWidth()
	{
		for (int i = 0; i < 20; i++)
		{
			tab.setAt(i, 1 + i);
		}

		final java.util.Map<Integer, int[]> before = new java.util.HashMap<>();
		for (int i = 0; i < tab.getSlots().size(); i++)
		{
			Integer id = tab.itemAt(i);
			if (id != null)
			{
				before.put(id, new int[]{tab.rowOf(i), tab.colOf(i)});
			}
		}

		assertTrue(tab.widenTo(BankTab.MAX_COLS));

		for (java.util.Map.Entry<Integer, int[]> e : before.entrySet())
		{
			int index = tab.indexOf(e.getKey());
			assertEquals("row of " + e.getKey(), e.getValue()[0], tab.rowOf(index));
			assertEquals("column of " + e.getKey(), e.getValue()[1], tab.colOf(index));
		}
	}

	@Test
	public void wideningIsANoOpAtOrBelowTheCurrentWidth()
	{
		tab.setAt(0, 1);
		tab.setAt(9, 2);

		assertFalse(tab.widenTo(BankTab.DEFAULT_COLS));
		assertFalse("narrowing is compact's job, never widenTo's", tab.widenTo(4));
		assertEquals(BankTab.DEFAULT_COLS, tab.getCols());
		assertEquals(Integer.valueOf(2), tab.itemAt(9));
	}

	@Test
	public void wideningAFullNarrowTabNeverPushesAnIdPastTheAbsoluteCap()
	{
		tab.setCols(BankTab.MIN_COLS);
		final int last = tab.maxSlots() - 1;
		tab.setAt(last, 42);

		assertTrue(tab.widenTo(BankTab.MAX_COLS));

		assertEquals(BankTab.MAX_COLS, tab.getCols());
		assertTrue("the widened index must stay addressable", tab.indexOf(42) < BankTab.MAX_SLOTS);
		assertEquals(last / BankTab.MIN_COLS, tab.rowOf(tab.indexOf(42)));
		assertEquals(last % BankTab.MIN_COLS, tab.colOf(tab.indexOf(42)));
	}

	@Test
	public void compactAtANewWidthPacksAndRelaysOut()
	{
		tab.setAt(0, 1);
		tab.setAt(3, 2);
		tab.setAt(9, 3);

		assertTrue(tab.compact(12));

		assertEquals(12, tab.getCols());
		assertEquals(Arrays.asList(1, 2, 3), tab.getSlots());
	}

	@Test
	public void compactIsNotANoOpWhenOnlyTheWidthChanges()
	{
		tab.setAt(0, 1);
		tab.setAt(1, 2);

		assertTrue("a dense tab still has to record the new width", tab.compact(4));
		assertEquals(4, tab.getCols());
		assertEquals(Arrays.asList(1, 2), tab.getSlots());
	}

	@Test
	public void setAtIgnoresAnIndexPastTheTabsOwnRowLimit()
	{
		tab.setCols(BankTab.MIN_COLS);
		tab.setAt(tab.maxSlots(), 7);
		assertTrue(tab.getSlots().isEmpty());
	}

	// ---- removeAt (card 27) ----

	@Test
	public void removeAtBlanksThatSlotOnly()
	{
		tab.setAt(0, 1);
		tab.setAt(1, 2);
		tab.setAt(2, 3);

		boolean removed = tab.removeAt(1);

		assertTrue(removed);
		assertEquals(Arrays.asList(1, null, 3), tab.getSlots());
	}

	@Test
	public void removeAtReturnsFalseForAnEmptyOrOutOfRangeSlot()
	{
		tab.setAt(0, 1);

		assertFalse(tab.removeAt(1));
		assertFalse(tab.removeAt(-1));
		assertFalse(tab.removeAt(100));
	}

	@Test
	public void removeAtTrimsTrailingNulls()
	{
		tab.setAt(0, 1);
		tab.setAt(1, 2);

		boolean removed = tab.removeAt(1);

		assertTrue(removed);
		assertEquals(Arrays.asList(1), tab.getSlots());
	}
}
