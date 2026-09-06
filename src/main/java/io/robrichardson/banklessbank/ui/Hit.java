package io.robrichardson.banklessbank.ui;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Hit-test result: {@code {type, index, slot}}. */
@Getter
@EqualsAndHashCode
@ToString
public final class Hit
{
	public enum Type
	{
		NONE, TITLE_BAR, CLOSE, TAB, TAB_PLUS, SLOT, SLOT_EMPTY, GRID_EMPTY, SEARCH, SEARCH_BUTTON,
		MODE_BUTTON, ADD_BUTTON, SCROLL_UP, SCROLL_DOWN, SCROLL_THUMB, SCROLL_TRACK, HSCROLL_LEFT, HSCROLL_RIGHT,
		HSCROLL_THUMB, HSCROLL_TRACK, MENU_ENTRY, RESIZE_GRIP
	}

	private final Type type;
	private final int index;
	private final BankSlot slot;

	private Hit(Type type, int index, BankSlot slot)
	{
		this.type = type;
		this.index = index;
		this.slot = slot;
	}

	public static Hit none()
	{
		return new Hit(Type.NONE, -1, null);
	}

	public static Hit of(Type type, int index)
	{
		return new Hit(type, index, null);
	}

	public static Hit slot(int index, BankSlot slot)
	{
		return new Hit(Type.SLOT, index, slot);
	}

	public static Hit emptySlot(int index, BankSlot slot)
	{
		return new Hit(Type.SLOT_EMPTY, index, slot);
	}
}
