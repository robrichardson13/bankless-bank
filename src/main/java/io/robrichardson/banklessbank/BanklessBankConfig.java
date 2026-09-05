package io.robrichardson.banklessbank;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(BanklessBankConfig.CONFIG_GROUP)
public interface BanklessBankConfig extends Config
{
	String CONFIG_GROUP = "banklessbank";

	@ConfigItem(
		keyName = "toggleKeybind",
		name = "Toggle bank view",
		description = "Hotkey that opens and closes the Bankless Bank view",
		position = 0
	)
	default Keybind toggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "syncFromDudeWheresMyStuff",
		name = "Sync from Dude, Where's My Stuff?",
		description = "Pull tracked items from the Dude, Where's My Stuff? plugin when it is installed",
		position = 1
	)
	default boolean syncFromDudeWheresMyStuff()
	{
		return true;
	}
}
