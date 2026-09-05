package io.robrichardson.banklessbank.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.robrichardson.banklessbank.BanklessBankConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/** Persists a {@link BankLayout} per RS profile as JSON under the plugin's config group. */
@Slf4j
@Singleton
public class LayoutStore
{
	static final String CONFIG_KEY_LAYOUT = "layout";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public LayoutStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public BankLayout load(String profileKey)
	{
		String json = profileKey == null ? null
			: configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, profileKey, CONFIG_KEY_LAYOUT);
		if (json == null || json.isEmpty())
		{
			return new BankLayout();
		}

		try
		{
			BankLayout layout = gson.fromJson(json, BankLayout.class);
			if (layout == null)
			{
				return new BankLayout();
			}
			layout.normalise();
			return layout;
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Discarding unreadable bank layout for profile {}", profileKey, e);
			return new BankLayout();
		}
	}

	public void save(String profileKey, BankLayout layout)
	{
		if (profileKey == null)
		{
			return;
		}

		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, profileKey, CONFIG_KEY_LAYOUT, gson.toJson(layout));
	}
}
