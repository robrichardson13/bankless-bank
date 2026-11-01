package io.robrichardson.banklessbank.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClueItemsTest
{
	@Test
	public void everyStepOfATierFoldsToOneId()
	{
		assertEquals(2677, ClueItems.representativeFor("Clue scroll (easy)"));
		assertEquals(2801, ClueItems.representativeFor("Clue scroll (medium)"));
		assertEquals(2722, ClueItems.representativeFor("Clue scroll (hard)"));
		assertEquals(12073, ClueItems.representativeFor("Clue scroll (elite)"));
	}

	@Test
	public void stepItemsThatComeInManyIdsFoldToo()
	{
		assertEquals(2724, ClueItems.representativeFor("Casket (hard)"));
		assertEquals(2842, ClueItems.representativeFor("Challenge scroll (medium)"));
		assertEquals(2832, ClueItems.representativeFor("Key (medium)"));
		assertEquals(2795, ClueItems.representativeFor("Puzzle box (hard)"));
	}

	@Test
	public void anythingElseIsLeftAlone()
	{
		assertEquals(-1, ClueItems.representativeFor("Reward casket (hard)"));
		assertEquals(-1, ClueItems.representativeFor("Casket"));
		assertEquals(-1, ClueItems.representativeFor("Clue scroll (beginner)"));
		assertEquals(-1, ClueItems.representativeFor("Coins"));
		assertEquals(-1, ClueItems.representativeFor(null));
	}
}
