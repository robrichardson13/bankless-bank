package io.robrichardson.banklessbank.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One row of content: {@code HEADER} (storage name) or {@code ITEMS} (up to 8 {@link BankSlot}s),
 * with its content-space {@code y} and {@code height}. */
@Getter
@EqualsAndHashCode
@ToString
public final class BankRow
{
	public enum Kind
	{
		HEADER, ITEMS
	}

	private final Kind kind;
	private final int y;
	private final int height;
	private final String headerText;
	private final String headerSubtitle;
	private final List<BankSlot> slots;
	/** The tab this row belongs to in TABS mode, or -1 for BY_STORAGE and search rows. */
	private final int tabIndex;
	/** The tab's icon item id for a header row in the All view, or -1. */
	private final int headerIconItemId;

	public BankRow(Kind kind, int y, int height, String headerText, String headerSubtitle, List<BankSlot> slots)
	{
		this(kind, y, height, headerText, headerSubtitle, slots, -1, -1);
	}

	public BankRow(Kind kind, int y, int height, String headerText, String headerSubtitle, List<BankSlot> slots,
		int tabIndex, int headerIconItemId)
	{
		this.kind = kind;
		this.y = y;
		this.height = height;
		this.headerText = headerText;
		this.headerSubtitle = headerSubtitle;
		this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
		this.tabIndex = tabIndex;
		this.headerIconItemId = headerIconItemId;
	}
}
