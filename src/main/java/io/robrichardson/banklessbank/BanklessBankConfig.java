package io.robrichardson.banklessbank;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(BanklessBankConfig.CONFIG_GROUP)
public interface BanklessBankConfig extends Config
{
	String CONFIG_GROUP = "banklessbank";

	@ConfigSection(
		name = "Bank view",
		description = "How the bank view is opened and drawn",
		position = 0
	)
	String VIEW_SECTION = "view";

	@ConfigSection(
		name = "Death storage",
		description = "Deathpile and grave tracking",
		position = 1
	)
	String DEATH_SECTION = "death";

	@ConfigItem(
		keyName = "toggleKeybind",
		name = "Toggle bank view",
		description = "Hotkey that opens and closes the Bankless Bank view",
		section = VIEW_SECTION,
		position = 0
	)
	default Keybind toggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "showHudButton",
		name = "Show HUD button",
		description = "Draw an always-visible button on the game canvas that opens the bank view",
		section = VIEW_SECTION,
		position = 1
	)
	default boolean showHudButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "placeholders",
		name = "Placeholders",
		description = "Keep a greyed-out slot for items you no longer own, like real bank placeholders",
		section = VIEW_SECTION,
		position = 2
	)
	default boolean placeholders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEmptyStorages",
		name = "Show empty storages",
		description = "List storages with no tracked items in the storage tab",
		section = VIEW_SECTION,
		position = 3
	)
	default boolean showEmptyStorages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "deathpilesUseAccountPlayTime",
		name = "Cross-client tracking",
		description = "When enabled, deathpile/grave expiry is based on account play time, so time played on "
			+ "another client (like mobile) counts. Requires visiting the Character Summary tab so the plugin "
			+ "can read your play time.",
		section = DEATH_SECTION,
		position = 0
	)
	default boolean deathpilesUseAccountPlayTime()
	{
		return false;
	}

	@Range(min = 1, max = 59)
	@ConfigItem(
		keyName = "deathpileContingencyMinutes",
		name = "Contingency (minutes)",
		description = "This many minutes are removed from the deathpile/grave timer. If set to 15, new "
			+ "deathpiles start with 45 minutes until expiry.",
		section = DEATH_SECTION,
		position = 1
	)
	default int deathpileContingencyMinutes()
	{
		return 1;
	}
}
