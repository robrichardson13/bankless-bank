package io.robrichardson.banklessbank;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Bankless Bank",
	description = "A bank-style in-game view of every item you own, without visiting a bank. Built for UIMs.",
	tags = {"uim", "bank", "storage", "items", "inventory", "looting bag", "poh", "stash", "overlay"}
)
public class BanklessBankPlugin extends Plugin
{
	/** Namespace and message names of the Dude, Where's My Stuff? PluginMessage API (v1). */
	static final String DWMS_NAMESPACE = "dudewheresmystuff";
	static final String DWMS_STORAGES_REQUEST = "storages-request";
	static final String DWMS_STORAGES_RESPONSE = "storages-response";
	static final String PLUGIN_MESSAGE_SOURCE = "Bankless Bank";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private BanklessBankConfig config;

	@Override
	protected void startUp()
	{
		log.debug("Bankless Bank started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Bankless Bank stopped");
	}

	/**
	 * Receives "storages-response" messages from Dude, Where's My Stuff?. See docs/RESEARCH.md for the
	 * payload shape. Responses addressed to other plugins are ignored via the "target" field.
	 */
	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!DWMS_NAMESPACE.equals(message.getNamespace())
			|| !DWMS_STORAGES_RESPONSE.equals(message.getName())
			|| !PLUGIN_MESSAGE_SOURCE.equals(message.getData().get("target")))
		{
			return;
		}

		log.debug("Received DWMS storages response: {}", message.getData().get("storages"));
	}

	@Provides
	BanklessBankConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BanklessBankConfig.class);
	}
}
