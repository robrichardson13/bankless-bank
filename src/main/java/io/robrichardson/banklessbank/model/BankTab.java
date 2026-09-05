package io.robrichardson.banklessbank.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One bank tab: an ordered list of canonical item ids. Ids that the player no longer owns stay in
 * the list as placeholders until released. The tab icon is its first item, like the real bank.
 */
@Data
@NoArgsConstructor
public class BankTab
{
	private String name = "";
	private List<Integer> slots = new ArrayList<>();

	public BankTab(String name)
	{
		this.name = name;
	}

	public int getIconItemId()
	{
		return slots.isEmpty() ? -1 : slots.get(0);
	}
}
