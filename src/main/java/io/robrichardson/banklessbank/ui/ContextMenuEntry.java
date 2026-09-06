package io.robrichardson.banklessbank.ui;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * One context menu row, shaped like the game's own: {@code {option, target, action, arg}}.
 *
 * <p>The game splits every menu row into an <i>option</i> ("Wield", "Examine") drawn in white and a
 * <i>target</i> (the item or NPC name) drawn in {@code <col=ff9040>} orange, and we do the same so
 * our menu reads identically - see {@link BankOverlay}'s menu drawing. {@code target} is null or
 * empty for a row that names nothing in particular ("Release all placeholders", "Cancel").
 *
 * <p>{@code arg} is the item id or tab index the action applies to, or -1 when the action needs no
 * argument (e.g. {@link MenuAction#CANCEL}).
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class ContextMenuEntry
{
	private final String label;
	private final MenuAction action;
	private final int arg;
	/** The orange target half of the row, or null when the row names nothing. */
	private final String target;

	/** A row with no target, i.e. an option on its own. */
	public ContextMenuEntry(String label, MenuAction action, int arg)
	{
		this(label, action, arg, null);
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
