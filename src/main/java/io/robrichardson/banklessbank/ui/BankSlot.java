package io.robrichardson.banklessbank.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One drawable cell: canonical id, display name, summed quantity, {@code placeholder} flag,
 * {@code sources}, and its {@code (tabIndex, indexInTab)} origin.
 *
 * <p>A cell drawn past a tab's own layout width has no slot in that tab yet, so it carries
 * {@code indexInTab == -1} and instead records the {@code (gridRow, gridCol)} it is drawn at - see
 * {@link #beyondWidth}. Every other cell leaves those at -1. */
@Getter
@EqualsAndHashCode
@ToString
public final class BankSlot
{
	private final int canonicalId;
	private final String name;
	private final long quantity;
	private final boolean stackable;
	private final boolean placeholder;
	private final List<SlotSource> sources;
	private final int tabIndex;
	private final int indexInTab;
	/** Grid row this cell is drawn at, for a beyond-width cell only; -1 otherwise. */
	private final int gridRow;
	/** Grid column this cell is drawn at, for a beyond-width cell only; -1 otherwise. */
	private final int gridCol;

	public BankSlot(int canonicalId, String name, long quantity, boolean stackable,
		boolean placeholder, List<SlotSource> sources, int tabIndex, int indexInTab)
	{
		this(canonicalId, name, quantity, stackable, placeholder, sources, tabIndex, indexInTab, -1, -1);
	}

	private BankSlot(int canonicalId, String name, long quantity, boolean stackable,
		boolean placeholder, List<SlotSource> sources, int tabIndex, int indexInTab,
		int gridRow, int gridCol)
	{
		this.canonicalId = canonicalId;
		this.name = name;
		this.quantity = quantity;
		this.stackable = stackable;
		this.placeholder = placeholder;
		this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
		this.tabIndex = tabIndex;
		this.indexInTab = indexInTab;
		this.gridRow = gridRow;
		this.gridCol = gridCol;
	}

	/** An unoccupied grid cell: canonicalId -1, no name, no quantity, not a placeholder. */
	public static BankSlot empty(int tabIndex, int indexInTab)
	{
		return new BankSlot(-1, "", 0, false, false, Collections.emptyList(), tabIndex, indexInTab);
	}

	/**
	 * A blank cell drawn to the right of a tab's own layout width, because the window is wider than
	 * the tab is. It holds no slot index - the tab has none for that column - only where it is drawn,
	 * so that a drop onto it can widen the tab to {@code gridCol + 1} and place the item at
	 * {@code (gridRow, gridCol)} in the widened grid.
	 */
	public static BankSlot beyondWidth(int tabIndex, int gridRow, int gridCol)
	{
		return new BankSlot(-1, "", 0, false, false, Collections.emptyList(), tabIndex, -1, gridRow, gridCol);
	}

	/** True for a cell produced by {@link #empty} or {@link #beyondWidth}. */
	public boolean isEmpty()
	{
		return canonicalId <= 0;
	}

	/** True for a cell produced by {@link #beyondWidth}: a column the tab does not yet reach. */
	public boolean isBeyondWidth()
	{
		return gridCol >= 0;
	}
}
