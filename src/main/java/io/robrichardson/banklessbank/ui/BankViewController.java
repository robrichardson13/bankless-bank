package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.LayoutStore;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.StorageManager;
import io.robrichardson.banklessbank.tracking.death.ExpiringDeathStorage;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * The seam between the pure {@link BankViewModel} and the client. Owns the view model, which is
 * only ever touched on the client thread: everything the AWT input listener wants to do arrives
 * here as a {@link Runnable} and is run at the top of the next frame by {@link BankOverlay}.
 */
@Slf4j
@Singleton
public class BankViewController
{
	static final String KEY_VIEW_X = "viewX";
	static final String KEY_VIEW_Y = "viewY";
	static final String KEY_VIEW_ROWS = "viewRows";
	static final String KEY_VIEW_MODE = "viewMode";
	static final String KEY_VIEW_TAB = "viewTab";

	/** Layout writes are coalesced to at most one every this many milliseconds. */
	private static final long SAVE_INTERVAL_MS = 1000L;

	private final BanklessBankPlugin plugin;
	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final BanklessBankConfig config;
	private final LayoutStore layoutStore;

	@Getter
	private final BankViewModel viewModel = new BankViewModel();

	private final ConcurrentLinkedQueue<Runnable> actions = new ConcurrentLinkedQueue<>();

	/** Canonical id to display name, for placeholders whose storages are long gone. */
	private final Map<Integer, String> knownNames = new HashMap<>();

	/** Storage instance to owning manager config key. Rebuilt lazily; identity keyed. */
	private final Map<Object, String> categories = new IdentityHashMap<>();

	private volatile boolean open;

	/** Overlay top-left in canvas coordinates. {@code null} until the first open centres it. */
	private volatile Point position;

	private String loadedProfileKey;
	private boolean layoutDirty;
	private long lastSaveMs;
	private volatile boolean started;

	@Inject
	BankViewController(BanklessBankPlugin plugin, Client client, ItemManager itemManager,
		ConfigManager configManager, BanklessBankConfig config, LayoutStore layoutStore)
	{
		this.plugin = plugin;
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.config = config;
		this.layoutStore = layoutStore;
	}

	// ---- lifecycle -------------------------------------------------------------------------

	/** Restores the window position and view state that were saved globally (not per profile). */
	public void startUp()
	{
		int x = readInt(KEY_VIEW_X, -1);
		int y = readInt(KEY_VIEW_Y, -1);
		position = (x < 0 || y < 0) ? null : new Point(x, y);

		viewModel.setVisibleRows(readInt(KEY_VIEW_ROWS, BankGeometry.DEFAULT_ROWS));

		String mode = configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_MODE);
		if (ViewMode.BY_STORAGE.name().equals(mode))
		{
			viewModel.setMode(ViewMode.BY_STORAGE);
		}

		viewModel.setActiveTab(readInt(KEY_VIEW_TAB, -1));

		open = false;
		started = true;
		actions.clear();
	}

	/** Stops the controller from doing further work. Safe from any thread. */
	public void stop()
	{
		started = false;
		open = false;
		actions.clear();
	}

	/** Flushes the layout and the window position. Client thread only: touches shared view state. */
	public void flush()
	{
		if (loadedProfileKey != null)
		{
			layoutStore.save(loadedProfileKey, viewModel.getLayout());
			layoutDirty = false;
		}

		savePosition();
		saveViewState();
	}

	// ---- open / closed ---------------------------------------------------------------------

	public boolean isOpen()
	{
		return open;
	}

	/** Safe from any thread. */
	public void setOpen(boolean open)
	{
		if (this.open == open)
		{
			return;
		}

		this.open = open;

		if (!open)
		{
			post(() ->
			{
				viewModel.cancelDrag();
				viewModel.closeMenu();
				viewModel.setSearchFocused(false);
			});
		}
	}

	public void toggle()
	{
		setOpen(!open);
	}

	// ---- position --------------------------------------------------------------------------

	/**
	 * The overlay's top-left in canvas coordinates. Centres on the canvas the first time, which is
	 * why this must be called on the client thread (it reads the canvas size).
	 */
	public Point getPosition()
	{
		final Dimension size = viewModel.size();
		final int canvasW = Math.max(client.getCanvasWidth(), size.width);
		final int canvasH = Math.max(client.getCanvasHeight(), size.height);

		Point p = position;
		if (p == null)
		{
			p = new Point((canvasW - size.width) / 2, (canvasH - size.height) / 2);
			position = p;
			return p;
		}

		// A restored position can be off-canvas after a resize, or after the row count changed.
		final int x = clamp(p.x, 0, canvasW - size.width);
		final int y = clamp(p.y, 0, canvasH - size.height);
		if (x != p.x || y != p.y)
		{
			p = new Point(x, y);
			position = p;
		}
		return p;
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}

	public void setPosition(int x, int y)
	{
		position = new Point(Math.max(0, x), Math.max(0, y));
	}

	/** Persists the current position. Called when a title-bar drag ends, and on shutdown. */
	public void savePosition()
	{
		Point p = position;
		if (p == null)
		{
			return;
		}

		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_X, p.x);
		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_Y, p.y);
	}

	/** Persists rows, mode and active tab. */
	public void saveViewState()
	{
		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_ROWS, viewModel.getVisibleRows());
		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_MODE, viewModel.getMode().name());
		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_TAB, viewModel.getActiveTab());
	}

	// ---- action queue ----------------------------------------------------------------------

	/** Queues work to run on the client thread at the top of the next frame. Any thread. */
	public void post(Runnable action)
	{
		if (action != null)
		{
			actions.add(action);
		}
	}

	/** Client thread. Runs everything the input listener queued since the last frame. */
	public void drainActions()
	{
		Runnable action;
		while ((action = actions.poll()) != null)
		{
			try
			{
				action.run();
			}
			catch (RuntimeException e)
			{
				log.warn("Bank view action failed", e);
			}
		}
	}

	// ---- refresh ---------------------------------------------------------------------------

	/**
	 * Client thread. Reloads the layout on a profile change, rebuilds the snapshots when the
	 * tracking layer says they are stale, syncs the layout to what is owned and saves when needed.
	 */
	public void refresh()
	{
		if (!started || plugin.getLoadedProfileKey() == null)
		{
			return;
		}

		boolean profileChanged = syncProfile();
		boolean rebuilt = false;

		if (profileChanged || plugin.isStoragesDirty())
		{
			rebuildSnapshots();
			rebuilt = true;
		}

		if (rebuilt && viewModel.syncLayout())
		{
			viewModel.closeMenu();
			layoutDirty = true;
		}

		if (layoutDirty && loadedProfileKey != null)
		{
			long now = System.currentTimeMillis();
			if (now - lastSaveMs >= SAVE_INTERVAL_MS)
			{
				layoutStore.save(loadedProfileKey, viewModel.getLayout());
				lastSaveMs = now;
				layoutDirty = false;
			}
		}
	}

	/** Marks the layout as needing a write. Called after any menu or drag mutation. */
	public void saveLayoutIfChanged()
	{
		layoutDirty = true;
	}

	private boolean syncProfile()
	{
		String profileKey = plugin.getLoadedProfileKey();
		if (profileKey == null)
		{
			return false;
		}

		if (Objects.equals(profileKey, loadedProfileKey))
		{
			return false;
		}

		if (loadedProfileKey != null && layoutDirty)
		{
			layoutStore.save(loadedProfileKey, viewModel.getLayout());
		}

		loadedProfileKey = profileKey;
		layoutDirty = false;
		lastSaveMs = System.currentTimeMillis();

		BankLayout layout = layoutStore.load(profileKey);
		viewModel.setLayout(layout);
		viewModel.setActiveTab(-1);
		viewModel.setScroll(0);
		categories.clear();
		knownNames.clear();
		return true;
	}

	private void rebuildSnapshots()
	{
		if (categories.isEmpty())
		{
			cacheCategories();
		}

		List<StorageSnapshot> out = new ArrayList<>();

		plugin.getStorageManagerManager().getViewableStorages().forEach(storage ->
		{
			List<ItemSnapshot> items = new ArrayList<>();

			for (ItemStack stack : storage.getItems())
			{
				if (stack.getId() <= 0 || stack.getQuantity() <= 0)
				{
					continue;
				}

				int canonical = itemManager.canonicalize(stack.getId());
				String name = stack.getName();
				if (name == null || name.isEmpty() || "Loading".equals(name))
				{
					name = nameOf(canonical);
				}
				items.add(new ItemSnapshot(canonical, name, stack.getQuantity(), stack.isStackable()));
				knownNames.put(canonical, name);
			}

			if (items.isEmpty() && !config.showEmptyStorages())
			{
				return;
			}

			String subtitle = storage instanceof ExpiringDeathStorage
				? ((ExpiringDeathStorage) storage).getExpireText()
				: null;

			String category = categories.get(storage);
			out.add(new StorageSnapshot(category == null ? "" : category, storage.getName(), subtitle, items));
		});

		seedPlaceholderNames();

		viewModel.setKnownNames(knownNames);
		viewModel.setSnapshots(out);
		plugin.clearStoragesDirty();
	}

	private void cacheCategories()
	{
		for (StorageManager<?, ?> manager : plugin.getStorageManagerManager().getStorageManagers())
		{
			for (Object storage : manager.getStorages())
			{
				categories.put(storage, manager.getConfigKey());
			}
		}
	}

	/** Fills in names for layout ids we have never seen a storage for (released-then-returned). */
	private void seedPlaceholderNames()
	{
		viewModel.getLayout().getTabs().forEach(tab -> tab.getSlots().forEach(id ->
		{
			if (id != null && !knownNames.containsKey(id))
			{
				knownNames.put(id, nameOf(id));
			}
		}));
	}

	/** Client thread only. */
	private String nameOf(int canonicalId)
	{
		try
		{
			String name = itemManager.getItemComposition(canonicalId).getName();
			return name == null ? "Item " + canonicalId : name;
		}
		catch (RuntimeException e)
		{
			return "Item " + canonicalId;
		}
	}

	private int readInt(String key, int fallback)
	{
		Integer value = configManager.getConfiguration(
			BanklessBankConfig.CONFIG_GROUP, key, int.class);
		return value == null ? fallback : value;
	}
}
