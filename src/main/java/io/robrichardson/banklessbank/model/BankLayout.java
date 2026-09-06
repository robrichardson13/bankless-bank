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
 * to eight more tabs can be created. Keys are canonical item ids.
 */
public class BankLayout
{
	public static final int MAX_TABS = 9;

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

		Set<Integer> seen = new HashSet<>();
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

			for (int i = 0; i < slots.size(); i++)
			{
				Integer id = slots.get(i);
				if (id != null && (id <= 0 || !seen.add(id)))
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

	/**
	 * Places an item at an exact slot. If another item already occupies that slot the two swap: the
	 * occupant moves to the slot the dragged item vacated (in the tab it came from). When the dragged
	 * item had no previous slot, the occupant is moved to {@code toTab}'s {@link BankTab#appendIndex()}.
	 * A no-op for an out-of-range tab, a negative slot, a slot at/above the target tab's
	 * {@link BankTab#maxSlots()}, or a drop onto the item's own slot. Empty custom tabs are pruned afterwards.
	 *
	 * @return true when the layout changed
	 */
	public boolean placeItem(int itemId, int toTab, int toSlot)
	{
		if (toTab < 0 || toTab >= tabs.size() || toSlot < 0 || toSlot >= tabs.get(toTab).maxSlots())
		{
			return false;
		}

		BankTab target = tabs.get(toTab);
		int fromTabIdx = indexOfTab(itemId);
		BankTab fromTab = fromTabIdx == -1 ? null : tabs.get(fromTabIdx);
		int fromSlot = fromTab == null ? -1 : fromTab.indexOf(itemId);

		if (fromTab == target && fromSlot == toSlot)
		{
			return false;
		}

		Integer occupant = target.itemAt(toSlot);

		if (fromTab != null)
		{
			fromTab.setAt(fromSlot, null);
		}

		if (occupant != null)
		{
			if (fromTab != null)
			{
				fromTab.setAt(fromSlot, occupant);
			}
			else
			{
				target.setAt(target.appendIndex(), occupant);
			}
		}

		target.setAt(toSlot, itemId);

		pruneEmptyTabs();
		return true;
	}

	/** Places an item at the end of a tab ({@link BankTab#appendIndex()}). */
	public boolean moveItemToTab(int itemId, int toTab)
	{
		if (toTab < 0 || toTab >= tabs.size())
		{
			return false;
		}
		return placeItem(itemId, toTab, tabs.get(toTab).appendIndex());
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
	 * <p>An id already somewhere in the layout is never added twice - the caller is expected to jump
	 * to {@link #indexOfTab(int)} instead - and neither is a non-positive id or one that would land
	 * past the tab's {@link BankTab#maxSlots()}.
	 *
	 * @return true when the layout changed
	 */
	public boolean addItem(int itemId, int tabIndex)
	{
		if (itemId <= 0 || tabIndex < 0 || tabIndex >= tabs.size() || indexOfTab(itemId) != -1)
		{
			return false;
		}

		BankTab tab = tabs.get(tabIndex);
		int index = tab.appendIndex();
		if (index >= tab.maxSlots())
		{
			return false;
		}

		tab.setAt(index, itemId);
		return true;
	}

	/**
	 * Creates a new tab holding the given item.
	 *
	 * @return the new tab's index, or -1 if the tab limit is reached
	 */
	public int createTab(int itemId)
	{
		if (tabs.size() >= MAX_TABS)
		{
			return -1;
		}

		int fromTabIdx = indexOfTab(itemId);
		if (fromTabIdx != -1)
		{
			tabs.get(fromTabIdx).removeItem(itemId);
		}

		BankTab tab = new BankTab("Tab " + tabs.size());
		tab.append(itemId);
		tabs.add(tab);
		pruneEmptyTabs();
		return tabs.indexOf(tab);
	}

	/**
	 * Deletes a custom tab, appending its occupied ids (gaps dropped) to the end of the main tab.
	 * A no-op for an out-of-range index or the main tab itself, wherever it currently sits.
	 */
	public void deleteTab(int index)
	{
		if (index < 0 || index >= tabs.size() || tabs.get(index).isMain())
		{
			return;
		}

		BankTab tab = tabs.remove(index);
		for (int id : tab.itemIds())
		{
			getMainTab().append(id);
		}
	}

	/** Releases a placeholder: blanks its slot if the player does not own it. */
	public boolean releasePlaceholder(int itemId, Set<Integer> ownedIds)
	{
		if (ownedIds.contains(itemId))
		{
			return false;
		}

		int tabIdx = indexOfTab(itemId);
		if (tabIdx == -1)
		{
			return false;
		}

		BankTab tab = tabs.get(tabIdx);
		tab.removeItem(itemId);
		if (!tab.isMain() && tab.isEmpty())
		{
			tabs.remove(tabIdx);
		}
		return true;
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
