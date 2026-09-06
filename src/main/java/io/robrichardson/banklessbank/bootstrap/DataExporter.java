package io.robrichardson.banklessbank.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.robrichardson.banklessbank.BanklessBankConfig;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Exports and imports every {@code banklessbank} config key for one RS profile as a single JSON
 * document, so a player can move their bank layout and tracked storages to another machine or
 * account without waiting on RuneLite's own cloud sync (which only covers the signed-in case).
 *
 * <p>An export is a faithful copy of both config scopes {@link ConfigManager} uses for this
 * plugin: the active-profile scope (view state, settings) and the RS-profile scope (layout,
 * placeholder ignore list, every tracked storage). Import always replaces the *currently
 * logged-in* character's data, never the one recorded in the file, which is what makes moving
 * between machines and accounts work.
 */
@Slf4j
@Singleton
public class DataExporter
{
	public static final int SCHEMA_VERSION = 1;
	static final String SCHEMA_PLUGIN = "banklessbank";

	/**
	 * One exported document. Plain fields, no Lombok, so Gson round-trips it without Unsafe.
	 *
	 * <p>{@code schema} and {@code plugin} deliberately carry no field initialiser: they are what
	 * {@link #parse(String)} identifies the file by, and a default would make every JSON object
	 * (including an unrelated one, or {@code "{}"}) validate as an empty export of ours - which an
	 * import would honour by wiping both config scopes and writing nothing back. {@link
	 * #buildExport} sets both explicitly.
	 */
	public static class ExportDocument
	{
		public int schema;
		public String plugin;
		public long exportedAt;
		public String exportedAtIso;
		public String rsProfileKey;
		public String displayName;
		public Map<String, String> profileConfig = new LinkedHashMap<>();
		public Map<String, String> rsProfileConfig = new LinkedHashMap<>();
	}

	@Value
	public static class ImportResult
	{
		int keysRemoved;
		int profileKeysWritten;
		int rsProfileKeysWritten;
		int keysSkipped;
		String message;
	}

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public DataExporter(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson.newBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	}

	/** Snapshots both config scopes for one RS profile. Any thread; {@link ConfigManager} is thread safe. */
	public ExportDocument buildExport(@Nullable String rsProfileKey, @Nullable String displayName)
	{
		ExportDocument doc = new ExportDocument();
		doc.schema = SCHEMA_VERSION;
		doc.plugin = SCHEMA_PLUGIN;
		doc.exportedAt = System.currentTimeMillis();
		doc.exportedAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(doc.exportedAt));
		doc.rsProfileKey = rsProfileKey;
		doc.displayName = displayName;

		String prefix = BanklessBankConfig.CONFIG_GROUP + ".";
		for (String key : new TreeSet<>(configManager.getConfigurationKeys(prefix)))
		{
			String shortKey = key.substring(prefix.length());
			String value = configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, shortKey);
			if (value != null)
			{
				doc.profileConfig.put(shortKey, value);
			}
		}

		if (rsProfileKey != null)
		{
			for (String key : new TreeSet<>(configManager.getRSProfileConfigurationKeys(
				BanklessBankConfig.CONFIG_GROUP, rsProfileKey, "")))
			{
				String value = configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, rsProfileKey, key);
				if (value != null)
				{
					doc.rsProfileConfig.put(key, value);
				}
			}
		}

		return doc;
	}

	/** Pretty-prints a document. */
	public String toJson(ExportDocument doc)
	{
		return gson.toJson(doc);
	}

	/** @throws IllegalArgumentException with a user-showable message if the file is not ours. */
	public ExportDocument parse(String json)
	{
		ExportDocument doc;
		try
		{
			doc = gson.fromJson(json, ExportDocument.class);
		}
		catch (JsonSyntaxException e)
		{
			throw new IllegalArgumentException("File is not valid JSON.");
		}

		if (doc == null)
		{
			throw new IllegalArgumentException("File is empty or not JSON.");
		}
		if (!SCHEMA_PLUGIN.equals(doc.plugin))
		{
			throw new IllegalArgumentException("Not a Bankless Bank export file.");
		}
		if (doc.schema < 1)
		{
			throw new IllegalArgumentException("Export file has no version; it is not a Bankless Bank"
				+ " export file.");
		}
		if (doc.schema > SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported export version " + doc.schema
				+ "; this build reads up to " + SCHEMA_VERSION + ".");
		}
		if (doc.profileConfig == null)
		{
			doc.profileConfig = new LinkedHashMap<>();
		}
		if (doc.rsProfileConfig == null)
		{
			doc.rsProfileConfig = new LinkedHashMap<>();
		}
		return doc;
	}

	/** Replaces every banklessbank key in both scopes with the document's. Any thread. */
	public ImportResult applyImport(ExportDocument doc, @Nullable String targetRsProfileKey)
	{
		int removed = 0;
		int skipped = 0;

		// 1. clear active-profile scope
		String prefix = BanklessBankConfig.CONFIG_GROUP + ".";
		for (String whole : new ArrayList<>(configManager.getConfigurationKeys(prefix)))
		{
			configManager.unsetConfiguration(BanklessBankConfig.CONFIG_GROUP, null, whole.substring(prefix.length()));
			removed++;
		}

		// 2. clear RS-profile scope (only when we have a target profile)
		if (targetRsProfileKey != null)
		{
			for (String key : new ArrayList<>(configManager.getRSProfileConfigurationKeys(
				BanklessBankConfig.CONFIG_GROUP, targetRsProfileKey, "")))
			{
				configManager.unsetConfiguration(BanklessBankConfig.CONFIG_GROUP, targetRsProfileKey, key);
				removed++;
			}
		}

		// 3. write active-profile scope
		int profileKeysWritten = 0;
		for (Map.Entry<String, String> e : doc.profileConfig.entrySet())
		{
			if (!isWritableKey(e.getKey()) || e.getValue() == null)
			{
				skipped++;
				continue;
			}
			configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, e.getKey(), e.getValue());
			profileKeysWritten++;
		}

		// 4. write RS-profile scope
		int rsProfileKeysWritten = 0;
		if (targetRsProfileKey != null)
		{
			for (Map.Entry<String, String> e : doc.rsProfileConfig.entrySet())
			{
				if (!isWritableKey(e.getKey()) || e.getValue() == null)
				{
					skipped++;
					continue;
				}
				configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, targetRsProfileKey, e.getKey(), e.getValue());
				rsProfileKeysWritten++;
			}
		}
		else
		{
			skipped += doc.rsProfileConfig.size();
		}

		StringBuilder message = new StringBuilder("Imported ")
			.append(profileKeysWritten)
			.append(" settings and ")
			.append(rsProfileKeysWritten)
			.append(" storage keys.");
		if (skipped > 0)
		{
			message.append(" Skipped ").append(skipped).append(" unusable keys.");
		}
		if (targetRsProfileKey == null)
		{
			message.append(" Not logged in, so per-character data was not imported.");
		}

		return new ImportResult(removed, profileKeysWritten, rsProfileKeysWritten, skipped, message.toString());
	}

	/** e.g. banklessbank-Zezima-20260905-142233.json */
	public static String defaultFileName(@Nullable String displayName, long whenMillis)
	{
		String safeName = displayName == null || displayName.trim().isEmpty()
			? "export"
			: displayName.replaceAll("[^A-Za-z0-9_-]", "_");
		String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date(whenMillis));
		return "banklessbank-" + safeName + "-" + timestamp + ".json";
	}

	/** Mirrors {@code ConfigManager.setConfiguration}'s own rejects, so a hostile file cannot throw. */
	static boolean isWritableKey(String key)
	{
		return key != null && !key.isEmpty() && key.indexOf(':') == -1 && !key.startsWith("$");
	}
}
