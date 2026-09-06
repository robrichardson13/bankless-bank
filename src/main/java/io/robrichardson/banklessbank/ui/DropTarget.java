package io.robrichardson.banklessbank.ui;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Result of a completed drag: {@code {type, tabIndex, slotIndex}}. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class DropTarget
{
	public enum Type
	{
		SLOT, TAB, NEW_TAB, TAB_REORDER, CANCEL
	}

	private final Type type;
	private final int tabIndex;
	private final int slotIndex;

	public static DropTarget cancel()
	{
		return new DropTarget(Type.CANCEL, -1, -1);
	}
}
