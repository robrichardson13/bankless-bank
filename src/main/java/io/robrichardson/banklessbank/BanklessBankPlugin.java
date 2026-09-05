package io.robrichardson.banklessbank;

import com.google.inject.Provides;
import io.robrichardson.banklessbank.tracking.ClientState;
import io.robrichardson.banklessbank.tracking.ItemContainerWatcher;
import io.robrichardson.banklessbank.tracking.StorageManagerManager;
import io.robrichardson.banklessbank.tracking.carryable.CarryableStorageManager;
import io.robrichardson.banklessbank.tracking.death.DeathStorageManager;
import io.robrichardson.banklessbank.tracking.playerownedhouse.PlayerOwnedHouseStorageManager;
import io.robrichardson.banklessbank.tracking.sailing.SailingStorageManager;
import io.robrichardson.banklessbank.tracking.stash.StashStorageManager;
import io.robrichardson.banklessbank.tracking.world.WorldStorageManager;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigSync;
import net.runelite.client.events.RuneScapeProfileChanged;
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
	static final String CONFIG_KEY_IS_MEMBER = "isMember";
	static final String CONFIG_KEY_ACCOUNT_TYPE = "accountType";

	@Getter
	@Inject
	private Client client;

	@Getter
	@Inject
	private ClientThread clientThread;

	@Getter
	@Inject
	private ConfigManager configManager;

	@Getter
	@Inject
	private ItemManager itemManager;

	@Getter
	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Getter
	@Inject
	private BanklessBankConfig config;

	@Inject
	private CarryableStorageManager carryableStorageManager;

	@Inject
	private DeathStorageManager deathStorageManager;

	@Getter
	@Inject
	private SailingStorageManager sailingStorageManager;

	@Inject
	private StashStorageManager stashStorageManager;

	@Inject
	private PlayerOwnedHouseStorageManager playerOwnedHouseStorageManager;

	@Getter
	@Inject
	private WorldStorageManager worldStorageManager;

	@Getter
	private StorageManagerManager storageManagerManager;

	private ClientState clientState = ClientState.LOGGED_OUT;
	private boolean pluginStartedAlreadyLoggedIn;
	private String profileKey;

	/** Set when any tracked storage changes; the view clears it when it redraws. */
	@Getter
	private volatile boolean storagesDirty;

	@Override
	protected void startUp()
	{
		if (storageManagerManager == null)
		{
			deathStorageManager.setCarryableStorageManager(carryableStorageManager);
			worldStorageManager
				.getLeprechaun()
				.setBottomlessBucketStorage(carryableStorageManager.getBottomlessBucket());
			storageManagerManager = new StorageManagerManager(
				itemManager,
				sailingStorageManager,
				carryableStorageManager,
				deathStorageManager,
				stashStorageManager,
				playerOwnedHouseStorageManager,
				worldStorageManager);

			ItemContainerWatcher.init(client);
		}

		reset();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientState = ClientState.LOGGING_IN;
			pluginStartedAlreadyLoggedIn = true;
		}
		else if (client.getGameState() == GameState.LOGGING_IN)
		{
			clientState = ClientState.LOGGING_IN;
		}

		log.debug("Bankless Bank started");
	}

	@Override
	protected void shutDown()
	{
		save();
		log.debug("Bankless Bank stopped");
	}

	private void reset()
	{
		clientState = ClientState.LOGGED_OUT;

		ItemContainerWatcher.reset();
		storageManagerManager.reset();
		storagesChanged();
	}

	/** Called by the tracking layer whenever tracked items change. */
	public void storagesChanged()
	{
		storagesDirty = true;
	}

	/** The view calls this after it has rebuilt from the tracked data. */
	public void clearStoragesDirty()
	{
		storagesDirty = false;
	}

	private void load(String profileKey)
	{
		this.profileKey = profileKey;

		clientThread.invokeLater(() ->
		{
			storageManagerManager.reset();
			storageManagerManager.load(profileKey);
			storagesChanged();
		});
	}

	/** Saves all tracked storages for the current profile. */
	public void save()
	{
		if (profileKey == null)
		{
			return;
		}

		storageManagerManager.save(profileKey);
	}

	/** Re-reads all tracked storages from config (used after a bootstrap import). Client thread only. */
	public void reload()
	{
		if (profileKey == null)
		{
			profileKey = configManager.getRSProfileKey();
		}

		storageManagerManager.reset();
		storageManagerManager.load(profileKey);
		storagesChanged();
	}

	@Subscribe
	public void onConfigSync(ConfigSync configSync)
	{
		save();
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown clientShutdown)
	{
		save();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		save();
		load(configManager.getRSProfileKey());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		storageManagerManager.onGameStateChanged(gameStateChanged);

		if (gameStateChanged.getGameState() == GameState.LOGGING_IN)
		{
			clientState = ClientState.LOGGING_IN;
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		if (clientState == ClientState.LOGGING_IN)
		{
			final boolean isMember = client.getVarcIntValue(VarClientInt.MEMBERSHIP_STATUS) == 1;
			final int accountType = client.getVarbitValue(VarbitID.IRONMAN);

			configManager.setRSProfileConfiguration(BanklessBankConfig.CONFIG_GROUP, CONFIG_KEY_IS_MEMBER, isMember);
			configManager.setRSProfileConfiguration(BanklessBankConfig.CONFIG_GROUP, CONFIG_KEY_ACCOUNT_TYPE, accountType);

			storageManagerManager.logIn(isMember, accountType);
			clientState = ClientState.LOGGED_IN;

			if (pluginStartedAlreadyLoggedIn)
			{
				load(configManager.getRSProfileKey());

				clientThread.invokeLater(() ->
				{
					for (ItemContainer itemContainer : client.getItemContainers())
					{
						onItemContainerChanged(new ItemContainerChanged(itemContainer.getId(), itemContainer));
					}

					VarbitChanged varbitChanged = new VarbitChanged();
					varbitChanged.setVarbitId(-999);
					onVarbitChanged(varbitChanged);
				});

				pluginStartedAlreadyLoggedIn = false;
			}

			return;
		}

		ItemContainerWatcher.onGameTick(this);
		storageManagerManager.onGameTick();
		storageManagerManager.softUpdate();
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		storageManagerManager.onActorDeath(actorDeath);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onChatMessage(chatMessage);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onGameObjectSpawned(gameObjectSpawned);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOption)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onMenuOptionClicked(menuOption);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onWidgetLoaded(widgetLoaded);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed widgetClosed)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onWidgetClosed(widgetClosed);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onVarbitChanged(varbitChanged);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged itemContainerChanged)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onItemContainerChanged(itemContainerChanged);
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		if (clientState == ClientState.LOGGED_OUT)
		{
			return;
		}

		storageManagerManager.onItemDespawned(itemDespawned);
	}

	@Provides
	BanklessBankConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BanklessBankConfig.class);
	}
}
