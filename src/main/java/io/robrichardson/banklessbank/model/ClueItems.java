package io.robrichardson.banklessbank.model;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Folds treasure trail items that the game splits into one id per clue step back to one id per
 * tier. Every "Clue scroll (hard)" step is its own item (there are nearly two hundred), so without
 * this each new clue would claim a fresh slot and leave a placeholder behind once solved. A player
 * holds at most one per tier anyway, so one slot is what the bank view should show.
 *
 * <p>Keyed by item name rather than an id table so a step Jagex adds later folds too. Only names
 * the game reuses across several step ids are listed; single-id items (beginner/master scrolls,
 * reward caskets, scroll boxes) need no folding. Each representative is the tier's lowest id.
 */
public final class ClueItems
{
	private static final Map<String, Integer> REPRESENTATIVES = new HashMap<>();

	static
	{
		REPRESENTATIVES.put("Clue scroll (easy)", 2677); // TRAIL_CLUE_EASY_SIMPLE001
		REPRESENTATIVES.put("Clue scroll (medium)", 2801); // TRAIL_CLUE_MEDIUM_SEXTANT001
		REPRESENTATIVES.put("Clue scroll (hard)", 2722); // TRAIL_CLUE_HARD_MAP001
		REPRESENTATIVES.put("Clue scroll (elite)", 12073); // TRAIL_ELITE_EMOTE_EXP1
		REPRESENTATIVES.put("Casket (easy)", 2714); // TRAIL_CLUE_EASY_MAP001_CASKET
		REPRESENTATIVES.put("Casket (medium)", 2802); // TRAIL_CLUE_MEDIUM_SEXTANT001_CASKET
		REPRESENTATIVES.put("Casket (hard)", 2724); // TRAIL_CLUE_HARD_SEXTANT001_CASKET
		REPRESENTATIVES.put("Casket (elite)", 12084); // TRAIL_ELITE_EMOTE_CASKET
		REPRESENTATIVES.put("Challenge scroll (medium)", 2842); // TRAIL_CLUE_MEDIUM_ANAGRAM001_CHALLENGE
		REPRESENTATIVES.put("Challenge scroll (hard)", 7269); // TRAIL_CLUE_HARD_ANAGRAM001_CHALLENGE
		REPRESENTATIVES.put("Challenge scroll (elite)", 12128); // TRAIL_ELITE_SKILL_CHALLENGE
		REPRESENTATIVES.put("Key (medium)", 2832); // TRAIL_CLUE_MEDIUM_RIDDLE001_KEY
		REPRESENTATIVES.put("Puzzle box (hard)", 2795); // TRAIL_CLUE_HARD_RIDDLE014_PUZZLEBOX
		REPRESENTATIVES.put("Puzzle box (master)", 20280); // TRAIL_MASTER_ZULRAH_PUZZLEBOX
	}

	private ClueItems()
	{
	}

	/** The one id every item with this name folds to, or -1 when the name is not folded. */
	public static int representativeFor(@Nullable String name)
	{
		if (name == null)
		{
			return -1;
		}
		Integer id = REPRESENTATIVES.get(name);
		return id == null ? -1 : id;
	}
}
