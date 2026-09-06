package io.robrichardson.banklessbank.ui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Entries + local-coordinate bounds + per-entry rects, laid out exactly like the game's own
 * "Choose Option" menu: a 1px frame, a {@link BankGeometry#MENU_HEADER_H}-tall header band holding
 * the title, then {@link BankGeometry#MENU_ENTRY_H}-tall rows. See {@link BankGeometry} for the
 * numbers and {@link BankOverlay} for the colours.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class ContextMenu
{
	/** The header the game draws above every menu, and we draw above ours. */
	public static final String TITLE = "Choose Option";

	private final List<ContextMenuEntry> entries;
	private final Rectangle bounds;

	public ContextMenu(List<ContextMenuEntry> entries, Rectangle bounds)
	{
		this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
		this.bounds = new Rectangle(bounds);
	}

	/** The header band's local rect, inside the frame: where {@link #TITLE} is drawn. */
	public Rectangle headerRect()
	{
		return new Rectangle(bounds.x + 1, bounds.y + 1, bounds.width - 2, BankGeometry.MENU_HEADER_H - 2);
	}

	/** Local rect of entry {@code i}, one {@link BankGeometry#MENU_ENTRY_H}-tall row below the header. */
	public Rectangle entryRect(int i)
	{
		return new Rectangle(bounds.x + 1,
			bounds.y + BankGeometry.MENU_HEADER_H + BankGeometry.MENU_BODY_GAP + i * BankGeometry.MENU_ENTRY_H,
			bounds.width - 2, BankGeometry.MENU_ENTRY_H);
	}

	/** Index of the entry under the local point, or -1 (the header and the frame belong to no entry). */
	public int entryIndexAt(int x, int y)
	{
		if (!bounds.contains(x, y))
		{
			return -1;
		}
		int top = bounds.y + BankGeometry.MENU_HEADER_H + BankGeometry.MENU_BODY_GAP;
		if (y < top)
		{
			return -1;
		}
		int i = (y - top) / BankGeometry.MENU_ENTRY_H;
		return i >= 0 && i < entries.size() ? i : -1;
	}
}
