package io.robrichardson.banklessbank.ui;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One context menu row: {@code {label, action, arg}}. {@code arg} is the item id or tab index
 * the action applies to, or -1 when the action needs no argument (e.g. {@link MenuAction#CANCEL}). */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class ContextMenuEntry
{
	private final String label;
	private final MenuAction action;
	private final int arg;
}
