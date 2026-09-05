package io.robrichardson.banklessbank;

import com.google.inject.Injector;
import com.google.inject.Provides;
import io.robrichardson.banklessbank.bootstrap.DwmsImporter;
import io.robrichardson.banklessbank.tracking.ClientState;
import io.robrichardson.banklessbank.tracking.ItemContainerWatcher;
import io.robrichardson.banklessbank.tracking.StorageManagerManager;
import io.robrichardson.banklessbank.tracking.carryable.CarryableStorageManager;
import io.robrichardson.banklessbank.tracking.death.DeathStorageManager;
import io.robrichardson.banklessbank.tracking.playerownedhouse.PlayerOwnedHouseStorageManager;
import io.robrichardson.banklessbank.tracking.sailing.SailingStorageManager;
import io.robrichardson.banklessbank.tracking.stash.StashStorageManager;
import io.robrichardson.banklessbank.tracking.world.WorldStorageManager;
import io.robrichardson.banklessbank.ui.BankInputListener;
import io.robrichardson.banklessbank.ui.BankOverlay;
import io.robrichardson.banklessbank.ui.BankViewController;
import io.robrichardson.banklessbank.ui.BanklessBankPanel;
import io.robrichardson.banklessbank.ui.HudButtonOverlay;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ConfigSync;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Bankless Bank",
	description = "A bank-style in-game view of every item you own, without visiting a bank. Built for UIMs.",
	tags = {"uim", "bank", "storage", "items", "inventory", "looting bag", "poh", "stash", "overlay"}
)
public class BanklessBankPlugin extends Plugin
{
	private static final String CONFIG_KEY_SHOW_HUD_BUTTON = "showHudButton";
	private static final String CONFIG_KEY_PLACEHOLDERS = "placeholders";
	private static final String CONFIG_KEY_SHOW_EMPTY_STORAGES = "showEmptyStorages";

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

	@Inject
	private Injector injector;

	@Inject
	private DwmsImporter dwmsImporter;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private BankViewController viewController;

	@Inject
	private BankOverlay bankOverlay;

	@Inject
	private HudButtonOverlay hudButtonOverlay;

	@Inject
	private BankInputListener inputListener;

	private BanklessBankPanel panel;
	private NavigationButton navButton;

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

	/**
	 * The profile whose tracked storages are actually loaded; null until the deferred load has
	 * run.
	 */
	@Getter
	private volatile String loadedProfileKey;

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

		final GameState gameState = client.getGameState();
		if (gameState == GameState.LOGGED_IN || gameState == GameState.LOGGING_IN)
		{
			clientState = ClientState.LOGGING_IN;
		}

		if (gameState == GameState.LOGGED_IN)
		{
			// Already in-game, so the login sequence we hook the initial load onto has been and gone;
			// onGameTick replays the item containers instead.
			pluginStartedAlreadyLoggedIn = true;
		}

		panel = injector.getInstance(BanklessBankPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Bankless Bank")
			.icon(icon)
			.panel(panel)
			.priority(5)
			.build();
		clientToolbar.addNavigation(navButton);

		viewController.startUp();
		overlayManager.add(bankOverlay);
		if (config.showHudButton())
		{
			overlayManager.add(hudButtonOverlay);
		}
		mouseManager.registerMouseListener(0, inputListener);
		mouseManager.registerMouseWheelListener(0, inputListener);
		keyManager.registerKeyListener(inputListener);
		keyManager.registerKeyListener(inputListener.getHotkeyListener());

		log.debug("Bankless Bank started");
	}

	@Override
	protected void shutDown()
	{
		mouseManager.unregisterMouseListener(inputListener);
		mouseManager.unregisterMouseWheelListener(inputListener);
		keyManager.unregisterKeyListener(inputListener);
		keyManager.unregisterKeyListener(inputListener.getHotkeyListener());
		overlayManager.remove(bankOverlay);
		overlayManager.remove(hudButtonOverlay);
		inputListener.publish(false, null, false);
		inputListener.publishHud(false, null);
		viewController.stop();

		// startUp/shutDown run on the EDT, but BankOverlay.render() -> refresh() mutates the same
		// BankViewModel/BankLayout state on the client thread; overlayManager.remove above does not
		// cancel a frame already in flight. Route the flush through the client thread so it
		// serialises against that in-flight render instead of racing it. If the client thread never
		// runs again (client exiting), at worst the last second of layout edits is lost; tracked
		// storages themselves are still flushed separately by the existing onClientShutdown/
		// onConfigSync handlers.
		clientThread.invoke(() ->
		{
			viewController.flush();
			save();
		});

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;

		log.debug("Bankless Bank stopped");
	}

	/** Toggles the bank view overlay. Safe from any thread. */
	public void toggleView()
	{
		viewController.toggle();
	}

	/** Opens or closes the bank view overlay. Safe from any thread. */
	public void setViewOpen(boolean viewOpen)
	{
		viewController.setOpen(viewOpen);
	}

	/** Whether the bank view overlay is currently open. */
	public boolean isViewOpen()
	{
		return viewController.isOpen();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!BanklessBankConfig.CONFIG_GROUP.equals(configChanged.getGroup()))
		{
			return;
		}

		if (CONFIG_KEY_SHOW_HUD_BUTTON.equals(configChanged.getKey()))
		{
			if (config.showHudButton())
			{
				overlayManager.add(hudButtonOverlay);
			}
			else
			{
				overlayManager.remove(hudButtonOverlay);
				inputListener.publishHud(false, null);
			}
		}
		else if (CONFIG_KEY_PLACEHOLDERS.equals(configChanged.getKey())
			|| CONFIG_KEY_SHOW_EMPTY_STORAGES.equals(configChanged.getKey()))
		{
			viewController.post(() -> viewController.getViewModel().invalidate());
			storagesChanged();
		}
	}

	private void reset()
	{
		clientState = ClientState.LOGGED_OUT;

		ItemContainerWatcher.reset();
		storageManagerManager.reset();
		storagesChanged();
		loadedProfileKey = null;
	}

	/** No tracked storage is loaded, so events carry nothing we can attribute to a profile. */
	private boolean isLoggedOut()
	{
		return clientState == ClientState.LOGGED_OUT;
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
		loadedProfileKey = null;

		clientThread.invokeLater(() ->
		{
			storageManagerManager.reset();
			storageManagerManager.load(profileKey);
			storagesChanged();
			loadedProfileKey = profileKey;
			maybeAutoImportFromDwms(profileKey);
			refreshPanel();
		});
	}

	/**
	 * On first login for a profile with no tracked data of our own, and that we have never
	 * imported into before, automatically pulls in whatever DWMS has (fill-gaps only, so it never
	 * clobbers anything). No-op otherwise; the player can still trigger a manual import from the
	 * sidebar panel at any time.
	 */
	private void maybeAutoImportFromDwms(String profileKey)
	{
		if (profileKey == null)
		{
			return;
		}

		if (dwmsImporter.hasOwnData(profileKey) || dwmsImporter.hasImportedBefore(profileKey))
		{
			return;
		}

		if (!dwmsImporter.hasDwmsData(profileKey) && !dwmsImporter.isDwmsEnabled())
		{
			return;
		}

		dwmsImporter.importNow(DwmsImporter.Mode.FILL_GAPS, result ->
		{
			log.debug("Auto-import from DWMS on first login: {}", result.getMessage());
			reload();
			refreshPanel();
		});
	}

	private void refreshPanel()
	{
		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::refresh);
		}
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
		loadedProfileKey = profileKey;
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

		final GameState gameState = gameStateChanged.getGameState();
		if (gameState == GameState.LOGGING_IN)
		{
			clientState = ClientState.LOGGING_IN;
		}

		if (gameState == GameState.LOGGED_IN
			|| gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.HOPPING)
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (isLoggedOut())
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
		dwmsImporter.onGameTick();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage pluginMessage)
	{
		dwmsImporter.onPluginMessage(pluginMessage);
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		storageManagerManager.onActorDeath(actorDeath);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onChatMessage(chatMessage);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onGameObjectSpawned(gameObjectSpawned);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOption)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onMenuOptionClicked(menuOption);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onWidgetLoaded(widgetLoaded);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed widgetClosed)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onWidgetClosed(widgetClosed);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onVarbitChanged(varbitChanged);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged itemContainerChanged)
	{
		if (isLoggedOut())
		{
			return;
		}

		storageManagerManager.onItemContainerChanged(itemContainerChanged);
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		if (isLoggedOut())
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
