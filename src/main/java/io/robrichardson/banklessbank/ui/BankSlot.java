package io.robrichardson.banklessbank.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One drawable cell: canonical id, display name, summed quantity, {@code placeholder} flag,
 * {@code sources}, and its {@code (tabIndex, indexInTab)} origin. */
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

	public BankSlot(int canonicalId, String name, long quantity, boolean stackable,
		boolean placeholder, List<SlotSource> sources, int tabIndex, int indexInTab)
	{
		this.canonicalId = canonicalId;
		this.name = name;
		this.quantity = quantity;
		this.stackable = stackable;
		this.placeholder = placeholder;
		this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
		this.tabIndex = tabIndex;
		this.indexInTab = indexInTab;
	}
}
