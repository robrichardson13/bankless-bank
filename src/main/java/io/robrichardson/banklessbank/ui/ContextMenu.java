package io.robrichardson.banklessbank.ui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Entries + local-coordinate bounds + per-entry rects. */
@Getter
@EqualsAndHashCode
@ToString
public final class ContextMenu
{
	private final List<ContextMenuEntry> entries;
	private final Rectangle bounds;

	public ContextMenu(List<ContextMenuEntry> entries, Rectangle bounds)
	{
		this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
		this.bounds = new Rectangle(bounds);
	}

	/** Local rect of entry {@code i}, one {@link BankGeometry#MENU_ENTRY_H}-tall row within {@link #getBounds()}. */
	public Rectangle entryRect(int i)
	{
		return new Rectangle(bounds.x, bounds.y + i * BankGeometry.MENU_ENTRY_H, bounds.width, BankGeometry.MENU_ENTRY_H);
	}

	/** Index of the entry under the local point, or -1. */
	public int entryIndexAt(int x, int y)
	{
		if (!bounds.contains(x, y))
		{
			return -1;
		}
		int i = (y - bounds.y) / BankGeometry.MENU_ENTRY_H;
		return i >= 0 && i < entries.size() ? i : -1;
	}
}
