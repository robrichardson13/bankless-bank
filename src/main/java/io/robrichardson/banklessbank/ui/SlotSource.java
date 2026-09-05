package io.robrichardson.banklessbank.ui;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One line of a slot's per-storage breakdown: {@code {storageName, quantity}}. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class SlotSource
{
	private final String storageName;
	private final long quantity;
}
