package io.robrichardson.banklessbank;

import java.awt.Color;
import net.runelite.client.config.Alpha;
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

	@ConfigSection(
		name = "HUD button",
		description = "The on-canvas button that opens the bank view",
		position = 2
	)
	String HUD_SECTION = "hud";

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
		section = HUD_SECTION,
		position = 0
	)
	default boolean showHudButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudIconItemId",
		name = "Icon item ID",
		description = "Show this item's sprite on the HUD button instead of the bank booth. 0 uses the bank booth.",
		section = HUD_SECTION,
		position = 1
	)
	default int hudIconItemId()
	{
		return 0;
	}

	@Range(min = 16, max = 64)
	@ConfigItem(
		keyName = "hudSize",
		name = "Size",
		description = "Width and height of the HUD button in pixels",
		section = HUD_SECTION,
		position = 2
	)
	default int hudSize()
	{
		return 30;
	}

	@Alpha
	@ConfigItem(
		keyName = "hudBackgroundColor",
		name = "Background",
		description = "Fill colour of the HUD button. Set alpha to 0 to draw the icon only.",
		section = HUD_SECTION,
		position = 3
	)
	default Color hudBackgroundColor()
	{
		return new Color(0x3E, 0x35, 0x29, 0xD0);
	}

	@Alpha
	@ConfigItem(
		keyName = "hudBorderColor",
		name = "Border",
		description = "Outline colour of the HUD button. Set alpha to 0 to hide it.",
		section = HUD_SECTION,
		position = 4
	)
	default Color hudBorderColor()
	{
		return new Color(0x5F, 0x54, 0x3F);
	}

	@Alpha
	@ConfigItem(
		keyName = "hudGlyphColor",
		name = "Bank booth colour",
		description = "Colour of the built-in bank booth icon. Has no effect when an icon item is set.",
		section = HUD_SECTION,
		position = 5
	)
	default Color hudGlyphColor()
	{
		return new Color(0xFF, 0x98, 0x1F);
	}

	@ConfigItem(
		keyName = "placeholders",
		name = "Placeholders",
		description = "Keep a greyed-out slot for items you no longer own, like real bank placeholders",
		section = VIEW_SECTION,
		position = 1
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
		position = 2
	)
	default boolean showEmptyStorages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showValue",
		name = "Show GE value",
		description = "Show the Grand Exchange value of the visible tab in the title bar",
		section = VIEW_SECTION,
		position = 3
	)
	default boolean showValue()
	{
		return true;
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
