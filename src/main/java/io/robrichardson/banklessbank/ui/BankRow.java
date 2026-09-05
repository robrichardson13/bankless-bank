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

	public BankRow(Kind kind, int y, int height, String headerText, String headerSubtitle, List<BankSlot> slots)
	{
		this.kind = kind;
		this.y = y;
		this.height = height;
		this.headerText = headerText;
		this.headerSubtitle = headerSubtitle;
		this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
	}
}
