package io.robrichardson.banklessbank.ui;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One {@code {canonicalId, name, quantity, stackable}} reading of an item inside one storage. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class ItemSnapshot
{
	private final int canonicalId;
	private final String name;
	private final long quantity;
	private final boolean stackable;
}
