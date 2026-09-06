package io.robrichardson.banklessbank.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * The player's arrangement of items into tabs and free-floating slots. Exactly one tab is always
 * flagged {@link BankTab#isMain()} and always exists (see {@link #getMainTab()}); it can sit
 * anywhere in {@link #getTabs()} - it is reorderable like any other tab, just never deletable. Up
 * to {@link #MAX_TABS} tabs exist in total. Keys are canonical item ids.
 */
public class BankLayout
{
	/**
	 * Most tabs a layout may hold, the main tab included. The real bank stops at nine; we do not,
	 * because the tab strip wraps onto extra rows rather than clipping (see {@code BankGeometry}).
	 *
	 * <p>Not unlimited, for three reasons. The strip is capped at {@code BankGeometry.MAX_STRIP_ROWS}
	 * rows, past which buttons shrink towards illegibility. {@link #normalise()} needs a deterministic
	 * bound to merge a surplus against. And the decisive one: the layout is a single RS-profile config
	 * value, which always cloud-syncs and is truncated server-side past 262144 bytes, so the largest
	 * layout the model can express has to stay inside that. A tab holds up to
	 * {@link BankTab#MAX_SLOTS} ids, which is roughly 9.5KB of JSON, so twenty tabs is about 190KB -
	 * inside the limit with headroom, where thirty would be about 280KB and could be silently cut off.
	 * At twenty, the minimum-width window still lays all 22 strip buttons out six to a row over four
	 * rows at 34px each, close to the natural {@code BankGeometry.TAB_W} and far above
	 * {@code BankGeometry.MIN_TAB_W}.
	 */
	public static final int MAX_TABS = 20;

	@Getter
	private List<BankTab> tabs = new ArrayList<>();

	/** Canonical ids that must never be drawn as placeholders. Insertion-ordered for stable JSON. */
	private Set<Integer> placeholderIgnoreIds = new LinkedHashSet<>();

	public BankLayout()
	{
		BankTab main = new BankTab("Main");
		main.setMain(true);
		tabs.add(main);
	}

	/**
	 * The one tab flagged {@link BankTab#isMain()}, wherever it currently sits in the strip.
	 * {@link #normalise()} guarantees exactly one exists; falls back to index 0 defensively for a
	 * layout used before normalisation (e.g. directly in a test).
	 */
	public BankTab getMainTab()
	{
		int idx = indexOfMainTab();
		return tabs.get(idx >= 0 ? idx : 0);
	}

	/** Index of the tab flagged {@link BankTab#isMain()}, or -1 if none is (pre-normalise only). */
	public int indexOfMainTab()
	{
		for (int i = 0; i < tabs.size(); i++)
		{
			if (tabs.get(i).isMain())
			{
				return i;
			}
		}
		return -1;
	}

	/** Repairs a layout loaded from config (missing main tab, duplicates, empty custom tabs). */
	public void normalise()
	{
		if (tabs == null)
		{
			tabs = new ArrayList<>();
		}
		// Deserialised JSON can carry a null element where a tab object was expected; drop those
		// before anything below dereferences one.
		tabs.removeIf(Objects::isNull);
		if (tabs.isEmpty())
		{
			BankTab main = new BankTab("Main");
			main.setMain(true);
			tabs.add(main);
		}

		// Exactly one tab is ever flagged main. Legacy JSON saved before the flag existed has none,
		// so slot 0 becomes main, matching the old implicit rule; corrupt JSON with more than one
		// keeps only the first and demotes the rest, never leaving zero or several.
		boolean foundMain = false;
		for (BankTab tab : tabs)
		{
			if (tab.isMain())
			{
				if (foundMain)
				{
					tab.setMain(false);
				}
				foundMain = true;
			}
		}
		if (!foundMain)
		{
			tabs.get(0).setMain(true);
		}

		for (int t = 0; t < tabs.size(); t++)
		{
			BankTab tab = tabs.get(t);
			if (tab.getSlots() == null)
			{
				tab.setSlots(new ArrayList<>());
			}
			// Null (JSON saved before the name field existed) or blank (a rename that emptied it out
			// without going through renameTab, or corrupt JSON) both fall back to the same default a
			// rename resets to - "Main"/"Tab N" - so a legacy or corrupted layout never shows a blank
			// title or divider label.
			if (tab.getName() == null || tab.getName().trim().isEmpty())
			{
				tab.setName(defaultTabName(t));
			}

			// A corrupt or absent width would otherwise divide the grid by zero.
			tab.setCols(tab.getCols());

			List<Integer> slots = tab.getSlots();
			while (slots.size() > tab.maxSlots())
			{
				slots.remove(slots.size() - 1);
			}

			// Card 27: uniqueness is per-tab, not per-layout, so the "seen" set is scoped to this tab
			// alone - the same id may legitimately repeat across different tabs.
			Set<Integer> seenInTab = new HashSet<>();
			for (int i = 0; i < slots.size(); i++)
			{
				Integer id = slots.get(i);
				if (id != null && (id <= 0 || !seenInTab.add(id)))
				{
					slots.set(i, null);
				}
			}
			tab.trimTrailingNulls();
		}

		for (BankTab tab : tabs)
		{
			if (tab.getIcon() > 0 && !tab.contains(tab.getIcon()))
			{
				tab.setIcon(-1);
			}
		}

		// Delete from the end, but never the main tab (deleteTab is already a no-op on it) - skip
		// back past it so a main tab saved last still lets surplus tabs merge away.
		while (tabs.size() > MAX_TABS)
		{
			int victim = tabs.size() - 1;
			while (victim >= 0 && tabs.get(victim).isMain())
			{
				victim--;
			}
			deleteTab(victim);
		}

		if (placeholderIgnoreIds == null)
		{
			placeholderIgnoreIds = new LinkedHashSet<>();
		}
		placeholderIgnoreIds.removeIf(id -> id == null || id <= 0);
	}

	/**
	 * Brings the layout in line with what the player owns: unseen ids are appended after the last
	 * occupied slot of the main tab and, when placeholders are off, ids no longer owned have their
	 * slot blanked (never removed from the list, so later items keep their positions).
	 *
	 * @return true if the layout changed
	 */
	public boolean sync(Set<Integer> ownedIds, boolean keepPlaceholders)
	{
		boolean changed = false;
		Set<Integer> present = new HashSet<>();

		for (int t = tabs.size() - 1; t >= 0; t--)
		{
			BankTab tab = tabs.get(t);
			List<Integer> slots = tab.getSlots();
			for (int i = 0; i < slots.size(); i++)
			{
				Integer id = slots.get(i);
				if (id == null)
				{
					continue;
				}
				if (!keepPlaceholders && !ownedIds.contains(id))
				{
					slots.set(i, null);
					changed = true;
					continue;
				}
				present.add(id);
			}

			tab.trimTrailingNulls();
			if (!tab.isMain() && tab.isEmpty())
			{
				tabs.remove(t);
				changed = true;
			}
		}

		for (int id : ownedIds)
		{
			if (present.add(id))
			{
				getMainTab().append(id);
				changed = true;
			}
		}

		return changed;
	}

	/**
	 * The <b>first</b> tab holding this id, or -1. With duplication there may be several - see
	 * {@link #tabsContaining(int)}.
	 */
	public int indexOfTab(int itemId)
	{
		for (int i = 0; i < tabs.size(); i++)
		{
			if (tabs.get(i).contains(itemId))
			{
				return i;
			}
		}
		return -1;
	}

	/** Number of tabs holding this id: 0, or 1 when it is not duplicated. */
	public int copyCount(int itemId)
	{
		return tabsContaining(itemId).size();
	}

	/** Tab indices holding this id, in strip order. Empty when the id is not in the layout. */
	public List<Integer> tabsContaining(int itemId)
	{
		List<Integer> result = new ArrayList<>();
		for (int i = 0; i < tabs.size(); i++)
		{
			if (tabs.get(i).contains(itemId))
			{
				result.add(i);
			}
		}
		return result;
	}

	/** True when {@code toTab} exists, does not already hold the id, and has room for one more. */
	public boolean canCopyTo(int itemId, int toTab)
	{
		BankTab tab = getTab(toTab);
		return tab != null && !tab.contains(itemId) && tab.appendIndex() < tab.maxSlots();
	}

	/**
	 * Moves the id at {@code (fromTab, fromSlot)} to {@code (toTab, toSlot)}. When the target slot is
	 * occupied the two swap, the occupant taking the vacated source slot. A no-op (returns false) for
	 * an out-of-range tab or slot, an empty source slot, a drop onto the source's own slot, or when
	 * the move would put an id into a tab that already holds another copy of it (see below). Prunes
	 * emptied non-main tabs.
	 */
	public boolean moveSlot(int fromTab, int fromSlot, int toTab, int toSlot)
	{
		BankTab from = getTab(fromTab);
		BankTab to = getTab(toTab);
		if (from == null || to == null || fromSlot < 0 || fromSlot >= from.getSlots().size()
			|| toSlot < 0 || toSlot >= to.maxSlots())
		{
			return false;
		}

		Integer itemId = from.itemAt(fromSlot);
		if (itemId == null)
		{
			return false;
		}

		if (from == to && fromSlot == toSlot)
		{
			return false;
		}

		Integer occupant = to.itemAt(toSlot);

		// Card 27: a move can never duplicate an id within one tab. Same-tab moves/swaps can never
		// break per-tab uniqueness (a permutation of one tab's own slots), so only cross-tab moves are
		// checked, in both directions for a swap.
		if (from != to)
		{
			if (occupant == null && to.contains(itemId))
			{
				return false;
			}
			if (occupant != null && (occupant.equals(itemId) || to.contains(itemId) || from.contains(occupant)))
			{
				return false;
			}
		}

		from.removeAt(fromSlot);
		if (occupant != null)
		{
			from.setAt(fromSlot, occupant);
		}
		to.setAt(toSlot, itemId);

		pruneEmptyTabs();
		return true;
	}

	/**
	 * Moves the id at {@code (fromTab, fromSlot)} to the end of {@code toTab}
	 * ({@link BankTab#appendIndex()}). Same duplication refusal as {@link #moveSlot}.
	 */
	public boolean moveSlotToTab(int fromTab, int fromSlot, int toTab)
	{
		BankTab to = getTab(toTab);
		if (to == null)
		{
			return false;
		}
		return moveSlot(fromTab, fromSlot, toTab, to.appendIndex());
	}

	/**
	 * Blanks the slot, pruning the tab when it is a non-main tab left empty. This is "remove this
	 * copy": the id survives in every other tab holding it.
	 */
	public boolean removeSlot(int tabIndex, int slotIndex)
	{
		BankTab tab = getTab(tabIndex);
		if (tab == null || !tab.removeAt(slotIndex))
		{
			return false;
		}
		pruneEmptyTabs();
		return true;
	}

	/**
	 * Adds a second (or third...) slot for an id at the end of {@code toTab}. A no-op returning -1
	 * for a non-positive id, an out-of-range tab, a tab that already holds the id, or a tab with no
	 * room ({@link BankTab#maxSlots()}).
	 *
	 * @return the slot index the copy landed at, or -1
	 */
	public int copyItemToTab(int itemId, int toTab)
	{
		if (itemId <= 0)
		{
			return -1;
		}
		BankTab tab = getTab(toTab);
		if (tab == null || tab.contains(itemId))
		{
			return -1;
		}
		int index = tab.appendIndex();
		if (index >= tab.maxSlots())
		{
			return -1;
		}
		tab.setAt(index, itemId);
		return index;
	}

	/**
	 * A new tab holding {@code itemId}, leaving every existing copy where it is. Used by a drop of a
	 * search result onto the plus button, which copies rather than moves.
	 *
	 * @return the new tab's index, or -1 at the tab limit
	 */
	public int createTabWith(int itemId)
	{
		if (itemId <= 0 || tabs.size() >= MAX_TABS)
		{
			return -1;
		}
		BankTab tab = new BankTab("Tab " + tabs.size());
		tab.append(itemId);
		tabs.add(tab);
		return tabs.indexOf(tab);
	}

	/**
	 * A new tab holding the id at {@code (fromTab, fromSlot)}, blanking that slot. Used by a grid drag
	 * onto the plus button and by the "New tab from <item>" menu row.
	 *
	 * @return the new tab's index, or -1 at the tab limit or for an empty source slot
	 */
	public int createTabFrom(int fromTab, int fromSlot)
	{
		BankTab from = getTab(fromTab);
		if (from == null || tabs.size() >= MAX_TABS)
		{
			return -1;
		}
		Integer itemId = from.itemAt(fromSlot);
		if (itemId == null)
		{
			return -1;
		}
		from.removeAt(fromSlot);
		BankTab tab = new BankTab("Tab " + tabs.size());
		tab.append(itemId);
		tabs.add(tab);
		pruneEmptyTabs();
		return tabs.indexOf(tab);
	}

	/**
	 * Blanks the slot if the player does not own the id it holds. The slot-addressed form of
	 * {@link #releasePlaceholder(int, Set)}; releases one copy only.
	 */
	public boolean releasePlaceholderAt(int tabIndex, int slotIndex, Set<Integer> ownedIds)
	{
		BankTab tab = getTab(tabIndex);
		if (tab == null)
		{
			return false;
		}
		Integer itemId = tab.itemAt(slotIndex);
		if (itemId == null || ownedIds.contains(itemId))
		{
			return false;
		}
		tab.removeAt(slotIndex);
		if (!tab.isMain() && tab.isEmpty())
		{
			tabs.remove(tabIndex);
		}
		return true;
	}

	/**
	 * Moves a tab from one index to another (list move, not a swap). Any tab, including the main
	 * tab, can move to any position - main is identified by {@link BankTab#isMain()}, not by list
	 * position, so dragging it elsewhere in the strip does not change what it is. {@code to} is
	 * clamped into {@code [0, tabs.size() - 1]}.
	 *
	 * @return true when the order changed
	 */
	public boolean moveTab(int from, int to)
	{
		if (from < 0 || from >= tabs.size())
		{
			return false;
		}

		int clampedTo = Math.max(0, Math.min(to, tabs.size() - 1));
		if (clampedTo == from)
		{
			return false;
		}

		BankTab tab = tabs.remove(from);
		int insertAt = Math.max(0, Math.min(clampedTo, tabs.size()));
		tabs.add(insertAt, tab);
		return true;
	}

	/**
	 * Sets a tab's icon. The item must occupy a slot in that tab; otherwise this is a no-op.
	 * {@code itemId <= 0} clears the icon back to "first occupied slot".
	 *
	 * @return true when the icon changed
	 */
	public boolean setTabIcon(int tabIndex, int itemId)
	{
		BankTab tab = getTab(tabIndex);
		if (tab == null)
		{
			return false;
		}

		if (itemId <= 0)
		{
			if (tab.getIcon() == -1)
			{
				return false;
			}
			tab.setIcon(-1);
			return true;
		}

		if (!tab.contains(itemId) || tab.getIcon() == itemId)
		{
			return false;
		}

		tab.setIcon(itemId);
		return true;
	}

	/**
	 * Longest name a rename can set (in UTF-16 chars); anything longer is truncated.
	 */
	public static final int MAX_TAB_NAME_LENGTH = 20;

	/**
	 * Renames a tab: the given name is trimmed and capped at {@link #MAX_TAB_NAME_LENGTH} chars,
	 * and a blank (or all-whitespace) name resets the tab back to its default - "Main" for the main
	 * tab, "Tab {@code N}" (1-based on its current position in the strip) for any other. A no-op for
	 * an out-of-range index.
	 *
	 * @return true when the name changed
	 */
	public boolean renameTab(int tabIndex, String newName)
	{
		BankTab tab = getTab(tabIndex);
		if (tab == null)
		{
			return false;
		}

		String trimmed = newName == null ? "" : newName.trim();
		if (trimmed.length() > MAX_TAB_NAME_LENGTH)
		{
			trimmed = trimmed.substring(0, MAX_TAB_NAME_LENGTH);
		}

		String resolved = trimmed.isEmpty() ? defaultTabName(tabIndex) : trimmed;
		if (resolved.equals(tab.getName()))
		{
			return false;
		}

		tab.setName(resolved);
		return true;
	}

	/** "Main" for the main tab, "Tab {@code N}" (1-based) for any other tab at this index. */
	private String defaultTabName(int tabIndex)
	{
		BankTab tab = getTab(tabIndex);
		return tab != null && tab.isMain() ? "Main" : "Tab " + (tabIndex + 1);
	}

	/**
	 * Collapses a tab's interior blank slots, packing its items down while preserving their order
	 * (a hidden placeholder still holds its slot, so it packs down with everything else rather than
	 * being dropped) and re-laying them out at {@code cols} - the window's current column count when
	 * this comes from "Collapse blank spaces", which is what makes that entry reflow the tab to fit
	 * the window. A no-op for an out-of-range index, or a tab that already has no gaps and is already
	 * that wide.
	 *
	 * @return true when the tab changed
	 */
	public boolean compactTab(int tabIndex, int cols)
	{
		BankTab tab = getTab(tabIndex);
		return tab != null && tab.compact(cols);
	}

	/**
	 * Widens a tab's layout to at least {@code cols} columns, re-indexing its ids so each keeps the
	 * {@code (row, col)} it renders at. What a drop onto a blank column past the tab's right edge
	 * does. A no-op for an out-of-range index or a tab that is already that wide.
	 *
	 * @return true when the tab changed
	 */
	public boolean widenTab(int tabIndex, int cols)
	{
		BankTab tab = getTab(tabIndex);
		return tab != null && tab.widenTo(cols);
	}

	public Set<Integer> getPlaceholderIgnoreIds()
	{
		return Collections.unmodifiableSet(placeholderIgnoreIds);
	}

	public boolean isPlaceholderIgnored(int itemId)
	{
		return placeholderIgnoreIds.contains(itemId);
	}

	/** @return true when newly added */
	public boolean addPlaceholderIgnore(int itemId)
	{
		return itemId > 0 && placeholderIgnoreIds.add(itemId);
	}

	/** @return true when removed */
	public boolean removePlaceholderIgnore(int itemId)
	{
		return placeholderIgnoreIds.remove(itemId);
	}

	/** Removes every id from the ignore list. @return true when anything was removed */
	public boolean clearPlaceholderIgnores()
	{
		if (placeholderIgnoreIds.isEmpty())
		{
			return false;
		}
		placeholderIgnoreIds.clear();
		return true;
	}

	/**
	 * Adds an item the player need not own to the end of a tab, at {@link BankTab#appendIndex()}.
	 * This is the manual-add path (the bottom bar's item search): until the item is owned it renders
	 * as an ordinary placeholder, and from then on it drags, moves and releases like anything else.
	 *
	 * <p>Card 27: the guard is per-tab, not layout-wide - an id already elsewhere in the layout can
	 * still be added here as a second copy. This is {@link #copyItemToTab(int, int)} in boolean
	 * clothing; the manual-add entry point is kept as its own method for callers.
	 *
	 * @return true when the layout changed
	 */
	public boolean addItem(int itemId, int tabIndex)
	{
		return copyItemToTab(itemId, tabIndex) >= 0;
	}

	/**
	 * Deletes a custom tab, appending its occupied ids (gaps dropped) to the end of the main tab.
	 * A no-op for an out-of-range index or the main tab itself, wherever it currently sits.
	 *
	 * <p>Card 27: an id main already holds is skipped rather than merged again, which would otherwise
	 * duplicate it within main - the copy is dropped because main already has one; that is a merge,
	 * not a loss.
	 */
	public void deleteTab(int index)
	{
		if (index < 0 || index >= tabs.size() || tabs.get(index).isMain())
		{
			return;
		}

		BankTab tab = tabs.remove(index);
		BankTab main = getMainTab();
		for (int id : tab.itemIds())
		{
			if (!main.contains(id))
			{
				main.append(id);
			}
		}
	}

	/**
	 * Releases a placeholder: blanks its slot if the player does not own it. Delegates to
	 * {@link #releasePlaceholderAt(int, int, Set)} on the <b>first</b> tab holding it; only meaningful
	 * where {@link #copyCount(int)} is 1, since with duplication the other copies are untouched.
	 */
	public boolean releasePlaceholder(int itemId, Set<Integer> ownedIds)
	{
		int tabIdx = indexOfTab(itemId);
		if (tabIdx == -1)
		{
			return false;
		}
		return releasePlaceholderAt(tabIdx, tabs.get(tabIdx).indexOf(itemId), ownedIds);
	}

	/** Releases every placeholder in a tab (or every tab when index is -1). */
	public int releaseAllPlaceholders(int tabIndex, Set<Integer> ownedIds)
	{
		int released = 0;
		for (int t = tabs.size() - 1; t >= 0; t--)
		{
			if (tabIndex != -1 && t != tabIndex)
			{
				continue;
			}

			BankTab tab = tabs.get(t);
			for (int id : new ArrayList<>(tab.itemIds()))
			{
				if (!ownedIds.contains(id))
				{
					tab.removeItem(id);
					released++;
				}
			}

			if (!tab.isMain() && tab.isEmpty())
			{
				tabs.remove(t);
			}
		}

		return released;
	}

	private void pruneEmptyTabs()
	{
		for (int t = tabs.size() - 1; t >= 0; t--)
		{
			if (!tabs.get(t).isMain() && tabs.get(t).isEmpty())
			{
				tabs.remove(t);
			}
		}
	}

	@Nullable
	public BankTab getTab(int index)
	{
		return index >= 0 && index < tabs.size() ? tabs.get(index) : null;
	}
}
