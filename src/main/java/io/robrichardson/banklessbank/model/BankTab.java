package io.robrichardson.banklessbank.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One bank tab: a sparse, positional list of canonical item ids. Index i in {@link #getSlots()} is
 * grid slot i; a {@code null} entry is a deliberate empty slot. Ids the player no longer owns stay
 * in place as placeholders until released. Trailing nulls are never stored.
 *
 * <p>A tab also carries its own layout width, {@link #getCols()}, which alone decides where a slot
 * index lands: index {@code i} is at {@code (i / cols, i % cols)}. The window's column count is a
 * viewport over that grid, never an input to it, so resizing the window never moves an item.
 * Widening the tab ({@link #widenTo(int)}) re-indexes every id so each keeps its {@code (row, col)};
 * {@link #compact(int)} is the deliberate reflow, packing the ids down at a new width.
 *
 * <p>An id occurs at most once in a tab, but may occur in several tabs - see {@link BankLayout} for
 * the duplication rules.
 */
@Data
@NoArgsConstructor
public class BankTab
{
	/** Narrowest layout width, matching the window's narrowest column count. */
	public static final int MIN_COLS = 4;
	/** Widest layout width, matching the window's widest column count. */
	public static final int MAX_COLS = 16;
	/** Layout width of a tab saved before tabs had one, and of every new tab. */
	public static final int DEFAULT_COLS = 8;
	/** Hard cap on a tab's rows, to bound a corrupt saved layout. */
	public static final int MAX_ROWS = 100;
	/**
	 * Absolute cap on a tab's addressable slots: {@link #MAX_ROWS} rows at the widest layout. The
	 * per-tab cap is {@link #maxSlots()}, which is this only at {@link #MAX_COLS}; the constant
	 * exists so widening a full narrow tab can never push an id past an addressable index.
	 */
	public static final int MAX_SLOTS = MAX_ROWS * MAX_COLS;

	private String name = "";
	/** Sparse: entry i is the id at slot i, or null for an empty slot. Never has trailing nulls. */
	private List<Integer> slots = new ArrayList<>();
	/** Explicit icon item id, or -1 to fall back to the first occupied slot. */
	private int icon = -1;
	/**
	 * Whether this is the one tab that always exists and cannot be deleted. Identifies "Main" by
	 * flag, not by list position, so it stays main wherever the player drags it in the strip.
	 * Absent (false) on JSON saved before this flag existed; {@link BankLayout#normalise()} then
	 * treats slot 0 as main, matching the old implicit rule.
	 */
	private boolean main;
	/**
	 * This tab's layout width in columns: index {@code i} sits at {@code (i / cols, i % cols)}.
	 * Absent (so {@value #DEFAULT_COLS}) on JSON saved before tabs had a width, which is exactly the
	 * fixed column count those layouts were arranged at. Always in
	 * {@link #MIN_COLS}..{@link #MAX_COLS}; {@link BankLayout#normalise()} repairs a corrupt value.
	 */
	private int cols = DEFAULT_COLS;

	public BankTab(String name)
	{
		this.name = name;
	}

	// ---- reads -------------------------------------------------------------------------------

	/** {@code c} brought into {@link #MIN_COLS}..{@link #MAX_COLS}. */
	public static int clampCols(int c)
	{
		return Math.max(MIN_COLS, Math.min(MAX_COLS, c));
	}

	/** Highest addressable slot count at this tab's width: {@link #MAX_ROWS} rows of it. */
	public int maxSlots()
	{
		return MAX_ROWS * cols;
	}

	/** Grid row of a flat slot index, at this tab's layout width. */
	public int rowOf(int index)
	{
		return index / cols;
	}

	/** Grid column of a flat slot index, at this tab's layout width. */
	public int colOf(int index)
	{
		return index % cols;
	}

	/** Flat slot index of a grid position, at this tab's layout width. */
	public int indexAt(int row, int col)
	{
		return row * cols + col;
	}

	/** Id at the slot, or null when the slot is empty or out of range. */
	public Integer itemAt(int index)
	{
		if (index < 0 || index >= slots.size())
		{
			return null;
		}
		return slots.get(index);
	}

	/** Slot index of the id, or -1. */
	public int indexOf(int itemId)
	{
		return slots.indexOf(Integer.valueOf(itemId));
	}

	public boolean contains(int itemId)
	{
		return indexOf(itemId) != -1;
	}

	/** Non-null ids in slot order. A fresh list; mutating it does not affect the tab. */
	public List<Integer> itemIds()
	{
		List<Integer> ids = new ArrayList<>();
		for (Integer id : slots)
		{
			if (id != null)
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/** Number of occupied slots. */
	public int itemCount()
	{
		int count = 0;
		for (Integer id : slots)
		{
			if (id != null)
			{
				count++;
			}
		}
		return count;
	}

	/** True when no slot is occupied. */
	public boolean isEmpty()
	{
		return itemCount() == 0;
	}

	/** Highest occupied slot index, or -1 when empty. */
	public int maxOccupiedIndex()
	{
		for (int i = slots.size() - 1; i >= 0; i--)
		{
			if (slots.get(i) != null)
			{
				return i;
			}
		}
		return -1;
	}

	/** Where a new item goes: {@code maxOccupiedIndex() + 1}. */
	public int appendIndex()
	{
		return maxOccupiedIndex() + 1;
	}

	/**
	 * The icon to draw: {@link #getIcon()} when it is set and still occupies a slot in this tab,
	 * otherwise the first occupied slot's id, otherwise -1.
	 */
	public int getIconItemId()
	{
		if (icon > 0 && contains(icon))
		{
			return icon;
		}
		for (Integer id : slots)
		{
			if (id != null)
			{
				return id;
			}
		}
		return -1;
	}

	// ---- writes --------------------------------------------------------------------------------

	/** Sets the layout width, clamped to {@link #MIN_COLS}..{@link #MAX_COLS}. Does not re-index. */
	public void setCols(int cols)
	{
		this.cols = clampCols(cols);
	}

	/**
	 * Puts an id (or null) at a slot, growing the backing list with nulls as needed. Ignores an
	 * index below 0 or at/above {@link #maxSlots()}. Trims trailing nulls afterwards.
	 */
	public void setAt(int index, Integer itemId)
	{
		if (index < 0 || index >= maxSlots())
		{
			return;
		}
		while (slots.size() <= index)
		{
			slots.add(null);
		}
		slots.set(index, itemId);
		trimTrailingNulls();
	}

	/** Appends at {@link #appendIndex()}. Convenience for callers and tests. */
	public void append(int itemId)
	{
		setAt(appendIndex(), itemId);
	}

	/** Blanks the slot holding the id. Returns true when something was removed. */
	public boolean removeItem(int itemId)
	{
		int idx = indexOf(itemId);
		if (idx == -1)
		{
			return false;
		}
		setAt(idx, null);
		return true;
	}

	/**
	 * Blanks slot {@code index} whatever it holds, trimming trailing nulls. Unlike
	 * {@link #removeItem(int)} this addresses a slot rather than an id, which is what removing one
	 * copy of a duplicated item needs.
	 *
	 * @return true when the slot held an id
	 */
	public boolean removeAt(int index)
	{
		if (index < 0 || index >= slots.size() || slots.get(index) == null)
		{
			return false;
		}
		setAt(index, null);
		return true;
	}

	/**
	 * Widens the tab, re-indexing every id so each keeps the {@code (row, col)} it renders at. This
	 * is what a drop onto a blank column past the tab's right edge does; the tab grows to hold that
	 * column and nothing already placed appears to move. A no-op when {@code newCols} is not wider
	 * than the current width (narrowing would collide two ids on one cell - {@link #compact(int)} is
	 * the way to a narrower width).
	 *
	 * @return true when the width changed
	 */
	public boolean widenTo(int newCols)
	{
		final int target = clampCols(newCols);
		if (target <= cols)
		{
			return false;
		}

		final List<Integer> widened = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++)
		{
			final Integer id = slots.get(i);
			if (id == null)
			{
				continue;
			}
			final int index = rowOf(i) * target + colOf(i);
			while (widened.size() <= index)
			{
				widened.add(null);
			}
			widened.set(index, id);
		}

		slots = widened;
		cols = target;
		trimTrailingNulls();
		return true;
	}

	/**
	 * Removes interior empty slots, packing the occupied ones down to the front in their existing
	 * order (trailing nulls are already absent, so this alone makes the tab dense). A hidden,
	 * unowned placeholder is still an id in {@link #slots}, so it packs down with everything else
	 * rather than being dropped. A no-op when the tab already has no gaps (including when empty).
	 *
	 * <p>This is also the one place a tab is re-laid-out at a different width: the packed ids are
	 * re-indexed at {@code newCols} (the window's current column count when this comes from
	 * "Collapse blank spaces"), which is what makes the entry reflow the tab to fit the window.
	 *
	 * @return true when the slots or the width changed
	 */
	public boolean compact(int newCols)
	{
		final int target = clampCols(newCols);
		final List<Integer> ids = itemIds();
		if (ids.size() == slots.size() && target == cols)
		{
			return false;
		}

		slots.clear();
		slots.addAll(ids);
		cols = target;
		while (slots.size() > maxSlots())
		{
			slots.remove(slots.size() - 1);
		}
		return true;
	}

	/** Drops trailing nulls. Called by every write above; exposed for {@code normalise()}. */
	public void trimTrailingNulls()
	{
		int last = slots.size() - 1;
		while (last >= 0 && slots.get(last) == null)
		{
			slots.remove(last);
			last--;
		}
	}
}
