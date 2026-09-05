package io.robrichardson.banklessbank.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * The player's arrangement of items into tabs and slots. Tab 0 is the main tab and always exists;
 * up to nine more can be created. Keys are canonical item ids.
 */
public class BankLayout
{
	public static final int MAX_TABS = 10;

	@Getter
	private List<BankTab> tabs = new ArrayList<>();

	public BankLayout()
	{
		tabs.add(new BankTab("Main"));
	}

	public BankTab getMainTab()
	{
		return tabs.get(0);
	}

	/** Repairs a layout loaded from config (missing main tab, duplicates, empty custom tabs). */
	public void normalise()
	{
		if (tabs == null)
		{
			tabs = new ArrayList<>();
		}
		if (tabs.isEmpty())
		{
			tabs.add(new BankTab("Main"));
		}

		Set<Integer> seen = new HashSet<>();
		for (BankTab tab : tabs)
		{
			if (tab.getSlots() == null)
			{
				tab.setSlots(new ArrayList<>());
			}
			tab.getSlots().removeIf(id -> id == null || !seen.add(id));
		}

		while (tabs.size() > MAX_TABS)
		{
			deleteTab(tabs.size() - 1);
		}
	}

	/**
	 * Brings the layout in line with what the player owns: unseen ids are appended to the main tab
	 * and, when placeholders are off, ids no longer owned are dropped.
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
			Iterator<Integer> it = tab.getSlots().iterator();
			while (it.hasNext())
			{
				int id = it.next();
				if (!keepPlaceholders && !ownedIds.contains(id))
				{
					it.remove();
					changed = true;
					continue;
				}
				present.add(id);
			}

			if (t > 0 && tab.getSlots().isEmpty())
			{
				tabs.remove(t);
				changed = true;
			}
		}

		for (int id : ownedIds)
		{
			if (present.add(id))
			{
				getMainTab().getSlots().add(id);
				changed = true;
			}
		}

		return changed;
	}

	public int indexOfTab(int itemId)
	{
		for (int i = 0; i < tabs.size(); i++)
		{
			if (tabs.get(i).getSlots().contains(itemId))
			{
				return i;
			}
		}
		return -1;
	}

	private void detach(int itemId)
	{
		for (BankTab tab : tabs)
		{
			tab.getSlots().remove(Integer.valueOf(itemId));
		}
	}

	/**
	 * Moves an item to a slot in a tab (insert semantics). A slot index past the end appends.
	 * Custom tabs left empty are deleted.
	 */
	public void moveItem(int itemId, int toTab, int toSlot)
	{
		if (toTab < 0 || toTab >= tabs.size())
		{
			return;
		}

		BankTab target = tabs.get(toTab);
		int fromTab = indexOfTab(itemId);
		int fromSlot = fromTab == -1 ? -1 : tabs.get(fromTab).getSlots().indexOf(itemId);
		detach(itemId);

		List<Integer> slots = target.getSlots();
		if (fromTab == toTab && fromSlot != -1 && fromSlot < toSlot)
		{
			toSlot--;
		}
		toSlot = Math.max(0, Math.min(toSlot, slots.size()));
		slots.add(toSlot, itemId);

		pruneEmptyTabs();
	}

	/** Moves an item to the end of a tab. */
	public void moveItemToTab(int itemId, int toTab)
	{
		moveItem(itemId, toTab, Integer.MAX_VALUE);
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

		detach(itemId);
		BankTab tab = new BankTab("Tab " + tabs.size());
		tab.getSlots().add(itemId);
		tabs.add(tab);
		pruneEmptyTabs();
		return tabs.indexOf(tab);
	}

	/** Deletes a custom tab, moving its items to the end of the main tab. */
	public void deleteTab(int index)
	{
		if (index <= 0 || index >= tabs.size())
		{
			return;
		}

		BankTab tab = tabs.remove(index);
		getMainTab().getSlots().addAll(tab.getSlots());
	}

	/** Releases a placeholder: removes the id if the player does not own it. */
	public boolean releasePlaceholder(int itemId, Set<Integer> ownedIds)
	{
		if (ownedIds.contains(itemId) || indexOfTab(itemId) == -1)
		{
			return false;
		}

		detach(itemId);
		pruneEmptyTabs();
		return true;
	}

	/** Releases every placeholder in a tab (or every tab when index is -1). */
	public int releaseAllPlaceholders(int tabIndex, Set<Integer> ownedIds)
	{
		int released = 0;
		for (int t = 0; t < tabs.size(); t++)
		{
			if (tabIndex != -1 && t != tabIndex)
			{
				continue;
			}

			Iterator<Integer> it = tabs.get(t).getSlots().iterator();
			while (it.hasNext())
			{
				if (!ownedIds.contains(it.next()))
				{
					it.remove();
					released++;
				}
			}
		}

		pruneEmptyTabs();
		return released;
	}

	private void pruneEmptyTabs()
	{
		for (int t = tabs.size() - 1; t > 0; t--)
		{
			if (tabs.get(t).getSlots().isEmpty())
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
