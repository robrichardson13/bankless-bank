package io.robrichardson.banklessbank.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** One storage: {@code {category, name, subtitle, items}}. The only shape the view model knows
 * about tracked data. */
@Getter
@EqualsAndHashCode
@ToString
public final class StorageSnapshot
{
	private final String category;
	private final String name;
	private final String subtitle;
	private final List<ItemSnapshot> items;

	public StorageSnapshot(String category, String name, String subtitle, List<ItemSnapshot> items)
	{
		this.category = category;
		this.name = name;
		this.subtitle = subtitle;
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
	}
}
