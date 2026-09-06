package io.robrichardson.banklessbank.ui;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * One context menu row, shaped like the game's own: {@code {option, target, action, arg, arg2}}.
 *
 * <p>The game splits every menu row into an <i>option</i> ("Wield", "Examine") drawn in white and a
 * <i>target</i> (the item or NPC name) drawn in {@code <col=ff9040>} orange, and we do the same so
 * our menu reads identically - see {@link BankOverlay}'s menu drawing. {@code target} is null or
 * empty for a row that names nothing in particular ("Release all placeholders", "Cancel").
 *
 * <p>{@code arg} is the item id or tab index the action applies to, or -1 when the action needs no
 * argument (e.g. {@link MenuAction#CANCEL}). {@code arg2} is a second argument, or -1: slot-addressed
 * rows carry {@code (arg = tabIndex, arg2 = slotIndex)}; {@link MenuAction#COPY_TO_TAB} carries
 * {@code (arg = target tab, arg2 = item id)}.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class ContextMenuEntry
{
	private final String label;
	private final MenuAction action;
	private final int arg;
	private final int arg2;
	/** The orange target half of the row, or null when the row names nothing. */
	private final String target;

	public ContextMenuEntry(String label, MenuAction action, int arg, int arg2, String target)
	{
		this.label = label;
		this.action = action;
		this.arg = arg;
		this.arg2 = arg2;
		this.target = target;
	}

	/** A row with no target, i.e. an option on its own. */
	public ContextMenuEntry(String label, MenuAction action, int arg)
	{
		this(label, action, arg, -1, null);
	}

	/** A row with a target and one argument; {@code arg2} defaults to -1. */
	public ContextMenuEntry(String label, MenuAction action, int arg, String target)
	{
		this(label, action, arg, -1, target);
	}

	/** True when this row has an orange target half to draw after the option. */
	public boolean hasTarget()
	{
		return target != null && !target.isEmpty();
	}

	/** Option and target as one string, as drawn; the width of this is the row's width. */
	public String text()
	{
		return hasTarget() ? label + " " + target : label;
	}
}
