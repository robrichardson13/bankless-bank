package io.robrichardson.banklessbank.bootstrap;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import io.robrichardson.banklessbank.tracking.Storage;
import io.robrichardson.banklessbank.tracking.StorageManager;
import io.robrichardson.banklessbank.tracking.StorageManagerManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * One-time bootstrap of tracked storages from the "Dude, Where's My Stuff?" (DWMS) plugin.
 *
 * <p>Two sources are combined:
 * <ol>
 *   <li>DWMS's saved RS-profile config (group {@code dudewheresmystuff}). Our tracking layer is a
 *       port of DWMS's, so the save strings are copied key-for-key into our own group. This keeps
 *       per-storage metadata (deathpile expiry, UUIDs, last-updated times) and works even when
 *       DWMS is disabled.</li>
 *   <li>If DWMS is running, its live state via the {@code storages-request} PluginMessage, which
 *       refreshes the item lists of storages that DWMS has updated since it last saved.</li>
 * </ol>
 *
 * <p>In {@link Mode#FILL_GAPS} only storages we had no data for are touched; {@link Mode#OVERWRITE}
 * replaces everything. Imports never run on a timer. After import there is no dependency on DWMS.
 */
@Slf4j
@Singleton
public class DwmsImporter
{
	public static final String DWMS_CONFIG_GROUP = "dudewheresmystuff";
	public static final String DWMS_PLUGIN_CLASS = "dev.thource.runelite.dudewheresmystuff.DudeWheresMyStuffPlugin";
	public static final String DWMS_STORAGES_REQUEST = "storages-request";
	public static final String DWMS_STORAGES_RESPONSE = "storages-response";
	public static final String PLUGIN_MESSAGE_SOURCE = "Bankless Bank";
	public static final String CONFIG_KEY_IMPORTED_AT = "dwmsImportedAt";

	/** Config key prefixes to copy from DWMS. Coins, minigames and UI settings are skipped. */
	static final List<String> IMPORTED_KEY_PREFIXES = List.of(
		"carryable.", "death.", "poh.", "world.", "sailing.", "stash.", "minutesPlayed");

	private static final int RESPONSE_TIMEOUT_TICKS = 10;

	public enum Mode
	{
		FILL_GAPS,
		OVERWRITE
	}

	@Value
	public static class Result
	{
		int configKeysCopied;
		int liveStoragesApplied;
		boolean liveResponseReceived;
		String message;
	}

	private final BanklessBankPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final EventBus eventBus;

	@Getter
	@Nullable
	private Result lastResult;

	@Getter
	private boolean importing;

	@Nullable
	private Pending pending;

	@Inject
	public DwmsImporter(
		BanklessBankPlugin plugin,
		Client client,
		ClientThread clientThread,
		ConfigManager configManager,
		PluginManager pluginManager,
		EventBus eventBus)
	{
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
	}

	@Nullable
	private Plugin findDwmsPlugin()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (DWMS_PLUGIN_CLASS.equals(p.getClass().getName()))
			{
				return p;
			}
		}
		return null;
	}

	public boolean isDwmsInstalled()
	{
		return findDwmsPlugin() != null;
	}

	public boolean isDwmsEnabled()
	{
		Plugin p = findDwmsPlugin();
		return p != null && pluginManager.isPluginEnabled(p);
	}

	/** Whether DWMS has saved anything for the given RS profile. */
	public boolean hasDwmsData(String profileKey)
	{
		return profileKey != null
			&& !configManager.getRSProfileConfigurationKeys(DWMS_CONFIG_GROUP, profileKey, "").isEmpty();
	}

	/** Whether Bankless Bank has any tracked storage saved for the given RS profile. */
	public boolean hasOwnData(String profileKey)
	{
		if (profileKey == null)
		{
			return false;
		}

		for (String prefix : IMPORTED_KEY_PREFIXES)
		{
			if (!configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, profileKey, prefix).isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	public boolean hasImportedBefore(String profileKey)
	{
		return profileKey != null
			&& configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, profileKey, CONFIG_KEY_IMPORTED_AT) != null;
	}

	/**
	 * Runs an import for the current RS profile. Work happens on the client thread; the callback is
	 * invoked (on the client thread) once the config copy and, if DWMS is running, the live response
	 * have been applied or timed out.
	 */
	public void importNow(Mode mode, @Nullable Consumer<Result> callback)
	{
		if (importing)
		{
			return;
		}
		importing = true;

		clientThread.invokeLater(() ->
		{
			String profileKey = configManager.getRSProfileKey();
			if (profileKey == null)
			{
				finish(new Result(0, 0, false, "Log in first"), callback);
				return;
			}

			StorageManagerManager smm = plugin.getStorageManagerManager();
			Set<String> storagesWithDataBefore = storagesWithData(smm, profileKey);

			plugin.save();
			int copied = copyConfig(profileKey, mode, storagesWithDataBefore);
			plugin.reload();

			if (!isDwmsEnabled())
			{
				finish(new Result(copied, 0, false,
					copied + " storages copied from saved DWMS data (DWMS not running)"), callback);
				return;
			}

			pending = new Pending(mode, callback, copied, storagesWithDataBefore, client.getTickCount() + RESPONSE_TIMEOUT_TICKS);
			Map<String, Object> data = new HashMap<>();
			data.put("source", PLUGIN_MESSAGE_SOURCE);
			eventBus.post(new PluginMessage(DWMS_CONFIG_GROUP, DWMS_STORAGES_REQUEST, data));
		});
	}

	/** Config keys of the storages we currently hold data for, from their live state. */
	private Set<String> storagesWithData(StorageManagerManager smm, String profileKey)
	{
		Set<String> keys = new HashSet<>();
		for (StorageManager<?, ?> manager : smm.getStorageManagers())
		{
			for (Storage<?> storage : manager.getStorages())
			{
				if (storage.hasData() || !storage.getItems().isEmpty())
				{
					keys.add(storageKey(manager, storage));
				}
			}
		}
		keys.addAll(configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, profileKey, ""));
		return keys;
	}

	private static String storageKey(StorageManager<?, ?> manager, Storage<?> storage)
	{
		return manager.getConfigKey() + "." + storage.getType().getConfigKey();
	}

	/**
	 * Copies DWMS's saved storage strings into our config group.
	 *
	 * @return number of keys copied
	 */
	int copyConfig(String profileKey, Mode mode, Set<String> existingKeys)
	{
		int copied = 0;
		for (String key : configManager.getRSProfileConfigurationKeys(DWMS_CONFIG_GROUP, profileKey, ""))
		{
			if (!isImportedKey(key))
			{
				continue;
			}

			if (mode == Mode.FILL_GAPS && existingKeys.contains(key))
			{
				continue;
			}

			String value = configManager.getConfiguration(DWMS_CONFIG_GROUP, profileKey, key);
			if (value == null)
			{
				continue;
			}

			configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, profileKey, key, value);
			copied++;
		}

		configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, profileKey, CONFIG_KEY_IMPORTED_AT,
			String.valueOf(System.currentTimeMillis()));
		return copied;
	}

	static boolean isImportedKey(String key)
	{
		for (String prefix : IMPORTED_KEY_PREFIXES)
		{
			if (key.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	/** Must be called every game tick so a missing DWMS response times out. */
	public void onGameTick()
	{
		Pending p = pending;
		if (p != null && client.getTickCount() >= p.deadlineTick)
		{
			pending = null;
			finish(new Result(p.configKeysCopied, 0, false,
				p.configKeysCopied + " storages copied; DWMS did not answer the live request"), p.callback);
		}
	}

	/** Handles the DWMS "storages-response" message. Posted by DWMS on the client thread. */
	public void onPluginMessage(PluginMessage message)
	{
		if (!DWMS_CONFIG_GROUP.equals(message.getNamespace())
			|| !DWMS_STORAGES_RESPONSE.equals(message.getName())
			|| message.getData() == null
			|| !PLUGIN_MESSAGE_SOURCE.equals(message.getData().get("target")))
		{
			return;
		}

		Pending p = pending;
		if (p == null)
		{
			return;
		}
		pending = null;

		int applied = applyLiveStorages(message.getData(), p.mode, p.storagesWithDataBefore);
		plugin.save();
		plugin.storagesChanged();
		finish(new Result(p.configKeysCopied, applied, true,
			p.configKeysCopied + " storages copied, " + applied + " refreshed from DWMS live data"), p.callback);
	}

	@SuppressWarnings("unchecked")
	int applyLiveStorages(Map<String, Object> data, Mode mode, Set<String> storagesWithDataBefore)
	{
		Object storagesObj = data.get("storages");
		if (!(storagesObj instanceof List))
		{
			return 0;
		}

		StorageManagerManager smm = plugin.getStorageManagerManager();
		int applied = 0;
		for (Object entryObj : (List<Object>) storagesObj)
		{
			if (!(entryObj instanceof Map))
			{
				continue;
			}
			Map<String, Object> entry = (Map<String, Object>) entryObj;
			String category = Objects.toString(entry.get("category"), "");
			String name = Objects.toString(entry.get("name"), "");

			// Death storages are per-instance (uuid keyed) and were copied with their metadata already.
			if ("death".equals(category))
			{
				continue;
			}

			Storage<?> storage = smm.findStorage(category, name).orElse(null);
			if (!(storage instanceof ItemStorage))
			{
				continue;
			}

			String key = category + "." + storage.getType().getConfigKey();
			if (mode == Mode.FILL_GAPS && storagesWithDataBefore.contains(key))
			{
				continue;
			}

			List<ItemStack> stacks = new ArrayList<>();
			Object itemsObj = entry.get("items");
			if (itemsObj instanceof List)
			{
				for (Object itemObj : (List<Object>) itemsObj)
				{
					if (!(itemObj instanceof Map))
					{
						continue;
					}
					Map<String, Object> item = (Map<String, Object>) itemObj;
					int id = toLong(item.get("id"), -1) > 0 ? (int) toLong(item.get("id"), -1) : -1;
					long quantity = toLong(item.get("quantity"), 0);
					if (id > 0 && quantity > 0)
					{
						stacks.add(new ItemStack(id, quantity, plugin));
					}
				}
			}

			((ItemStorage<?>) storage).importItems(stacks, toLong(entry.get("lastUpdated"), -1));
			applied++;
		}

		return applied;
	}

	private static long toLong(Object o, long dfault)
	{
		return o instanceof Number ? ((Number) o).longValue() : dfault;
	}

	private void finish(Result result, @Nullable Consumer<Result> callback)
	{
		lastResult = result;
		importing = false;
		log.info("DWMS import: {}", result.getMessage());
		if (callback != null)
		{
			callback.accept(result);
		}
	}

	@Value
	private static class Pending
	{
		Mode mode;
		@Nullable Consumer<Result> callback;
		int configKeysCopied;
		Set<String> storagesWithDataBefore;
		int deadlineTick;
	}
}
