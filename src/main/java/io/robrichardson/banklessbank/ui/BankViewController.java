package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
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
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxTextInput;

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
	static final String KEY_VIEW_COLS = "viewCols";
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
	private final ChatboxItemSearch itemSearch;
	private final ChatboxTextInput tabRenameInput;

	@Getter
	private final BankViewModel viewModel = new BankViewModel();

	private final ConcurrentLinkedQueue<Runnable> actions = new ConcurrentLinkedQueue<>();

	/** Canonical id to display name, for placeholders whose storages are long gone. */
	private final Map<Integer, String> knownNames = new HashMap<>();

	/** Canonical id to GE unit price, refreshed alongside {@link #knownNames}. */
	private final Map<Integer, Integer> unitPrices = new HashMap<>();

	/** Storage instance to owning manager config key. Rebuilt lazily; identity keyed. */
	private final Map<Object, String> categories = new IdentityHashMap<>();

	private volatile boolean open;

	/** Published from the client thread each frame; read by the AWT key/mouse listener. */
	private volatile boolean searchFocused;

	/**
	 * True while either of our two chatbox UIs - the item search or the tab-rename text input - is
	 * up. Read by the AWT key listener, which must leave every key alone while either is open: it
	 * registers ahead of the chatbox panel's own listener and would otherwise swallow the keys they
	 * need (Escape above all).
	 */
	private volatile boolean chatboxInputOpen;

	/** Overlay top-left in canvas coordinates. {@code null} until the first open centres it. */
	private volatile Point position;

	private String loadedProfileKey;
	/** Active tab restored from config, applied to the first profile load then cleared to -1. */
	private int pendingActiveTab = -1;
	private boolean layoutDirty;
	private long lastSaveMs;
	private volatile boolean started;

	@Inject
	BankViewController(BanklessBankPlugin plugin, Client client, ItemManager itemManager,
		ConfigManager configManager, BanklessBankConfig config, LayoutStore layoutStore,
		ChatboxItemSearch itemSearch, ChatboxTextInput tabRenameInput)
	{
		this.plugin = plugin;
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.config = config;
		this.layoutStore = layoutStore;
		this.itemSearch = itemSearch;
		this.tabRenameInput = tabRenameInput;
	}

	// ---- lifecycle -------------------------------------------------------------------------

	/** Restores the window position and view state that were saved globally (not per profile). */
	public void startUp()
	{
		int x = readInt(KEY_VIEW_X, -1);
		int y = readInt(KEY_VIEW_Y, -1);
		position = (x < 0 || y < 0) ? null : new Point(x, y);

		viewModel.setVisibleRows(readInt(KEY_VIEW_ROWS, BankGeometry.DEFAULT_ROWS));
		viewModel.setVisibleCols(readInt(KEY_VIEW_COLS, BankGeometry.DEFAULT_COLS));

		String mode = configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_MODE);
		if (ViewMode.BY_STORAGE.name().equals(mode))
		{
			viewModel.setMode(ViewMode.BY_STORAGE);
		}

		// Held back rather than applied now: the first profile load replaces the layout, and
		// syncProfile() has to reset the active tab for a genuine profile *change*. Applying the
		// saved value there instead is what makes it survive startup.
		pendingActiveTab = readInt(KEY_VIEW_TAB, -1);
		viewModel.setActiveTab(-1);

		open = false;
		started = true;
		actions.clear();
	}

	/** Stops the controller from doing further work. Safe from any thread. */
	public void stop()
	{
		started = false;
		open = false;
		searchFocused = false;
		chatboxInputOpen = false;
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
			searchFocused = false;
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

	/** Client thread, from {@link BankOverlay#render}. Read from the AWT thread by the listener. */
	public void publishSearchFocused(boolean focused)
	{
		this.searchFocused = focused;
	}

	/** Safe from any thread; read by the AWT key/mouse listener to decide whether to consume input. */
	public boolean isSearchFocused()
	{
		return searchFocused;
	}

	// ---- manual item add -------------------------------------------------------------------

	/**
	 * Safe from any thread. True while the game's chatbox item search or the tab-rename text input is
	 * open, which is the AWT key listener's cue to leave every key alone - it registers ahead of the
	 * chatbox panel's own listener and would otherwise swallow the keys either needs.
	 */
	public boolean isChatboxInputOpen()
	{
		return chatboxInputOpen;
	}

	/**
	 * Opens RuneLite's own in-game item search in the chatbox and adds whatever the player picks to
	 * the end of the tab on show. Client thread only ({@code build()} opens a chatbox panel), which
	 * is where the input listener's posted runnable runs it.
	 *
	 * <p>The selection callback can arrive on either thread - the widget listener fires on the client
	 * thread, Enter on a highlighted result fires on AWT - so it is posted through the action queue
	 * like any other mutation rather than touching the view model directly.
	 */
	public void openItemSearch()
	{
		chatboxInputOpen = true;
		itemSearch
			.tooltipText("Add to bank")
			.onItemSelected(id ->
			{
				// Cleared here as well as in onClose: the search is a shared singleton, so another
				// plugin opening it replaces both callbacks, and a click on our button is then the
				// only way back. Re-opening unconditionally is what makes that recovery work.
				chatboxInputOpen = false;
				post(() -> addItem(id));
			})
			.onClose(() -> chatboxInputOpen = false)
			.build();
	}

	/** Client thread. Adds a canonicalised id to the view and persists the layout if it changed. */
	private void addItem(int itemId)
	{
		if (viewModel.addItem(itemManager.canonicalize(itemId)))
		{
			saveLayoutIfChanged();
		}
		saveViewState();
	}

	// ---- tab rename ------------------------------------------------------------------------

	/**
	 * Opens a chatbox text input pre-filled with the tab's current name, following the same shape as
	 * {@link #openItemSearch()}: the input is a shared, constructor-injected instance, so opening it
	 * unconditionally re-registers our callbacks even if another plugin (or a previous, abandoned
	 * rename) still holds them. Client thread only ({@code build()} opens a chatbox panel), which is
	 * where the input listener's posted runnable runs it, from {@link BankViewModel#consumeRenameTabRequest()}.
	 *
	 * <p>{@code onDone} fires on Enter (client thread's widget dispatch or AWT, same ambiguity as the
	 * item search), so the actual rename is posted through the action queue rather than touching the
	 * view model directly. A no-op for a tab index that no longer exists (e.g. deleted by the time the
	 * player submits, which is not currently reachable but is the safe default).
	 */
	public void openTabRename(int tabIndex)
	{
		BankTab tab = viewModel.getLayout().getTab(tabIndex);
		if (tab == null)
		{
			return;
		}

		chatboxInputOpen = true;
		tabRenameInput
			.prompt("Enter tab name")
			.value(tab.getName())
			.onDone((String newName) -> post(() -> renameTab(tabIndex, newName)))
			.onClose(() -> chatboxInputOpen = false)
			.build();
	}

	/** Client thread. Renames a tab and persists the layout if the name actually changed. */
	private void renameTab(int tabIndex, String newName)
	{
		if (viewModel.renameTab(tabIndex, newName))
		{
			saveLayoutIfChanged();
		}
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

	/** Persists the window size (rows and columns), mode and active tab. */
	public void saveViewState()
	{
		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_ROWS, viewModel.getVisibleRows());
		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_COLS, viewModel.getVisibleCols());
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

		viewModel.setMaxRows(BankGeometry.rowsForHeight(client.getCanvasHeight()));
		viewModel.setMaxCols(BankGeometry.colsForWidth(client.getCanvasWidth()));

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

	/**
	 * Re-reads window position, size, mode and active tab from config and forces the layout to be
	 * reloaded on the next {@link #refresh()}. Used after an import replaces the config underneath
	 * us. Client thread only.
	 */
	public void reloadFromConfig()
	{
		layoutDirty = false;          // must precede the null, or syncProfile() saves the stale layout
		loadedProfileKey = null;      // makes syncProfile() reload on the next refresh()

		int x = readInt(KEY_VIEW_X, -1);
		int y = readInt(KEY_VIEW_Y, -1);
		position = (x < 0 || y < 0) ? null : new Point(x, y);

		viewModel.setVisibleRows(readInt(KEY_VIEW_ROWS, BankGeometry.DEFAULT_ROWS));
		viewModel.setVisibleCols(readInt(KEY_VIEW_COLS, BankGeometry.DEFAULT_COLS));

		String mode = configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, KEY_VIEW_MODE);
		viewModel.setMode(ViewMode.BY_STORAGE.name().equals(mode) ? ViewMode.BY_STORAGE : ViewMode.TABS);

		pendingActiveTab = readInt(KEY_VIEW_TAB, -1);
		viewModel.setActiveTab(-1);
		viewModel.setScroll(0);
		viewModel.invalidate();
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
		viewModel.setActiveTab(pendingActiveTab >= 0 && pendingActiveTab < layout.getTabs().size()
			? pendingActiveTab : -1);
		pendingActiveTab = -1;
		viewModel.setScroll(0);
		categories.clear();
		knownNames.clear();
		unitPrices.clear();
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
				unitPrices.put(canonical, itemManager.getItemPrice(canonical));
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
		viewModel.setUnitPrices(unitPrices);
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

	/**
	 * Fills in names and GE prices for layout ids we have never seen a storage for (a placeholder,
	 * or a released-then-returned item), so a placeholder-only tab still gets a tooltip price.
	 */
	private void seedPlaceholderNames()
	{
		viewModel.getLayout().getTabs().forEach(tab -> tab.getSlots().forEach(id ->
		{
			if (id == null)
			{
				return;
			}
			if (!knownNames.containsKey(id))
			{
				knownNames.put(id, nameOf(id));
			}
			if (!unitPrices.containsKey(id))
			{
				unitPrices.put(id, itemManager.getItemPrice(id));
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
