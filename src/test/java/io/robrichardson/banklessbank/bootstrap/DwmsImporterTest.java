package io.robrichardson.banklessbank.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.bootstrap.DwmsImporter.Mode;
import io.robrichardson.banklessbank.bootstrap.DwmsImporter.Result;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import io.robrichardson.banklessbank.tracking.StorageManagerManager;
import io.robrichardson.banklessbank.tracking.StorageType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Behaviour tests for {@link DwmsImporter} against the real implementation. */
@RunWith(MockitoJUnitRunner.Silent.class)
public class DwmsImporterTest
{
	private static final String PROFILE = "profile-1";

	@Mock private BanklessBankPlugin plugin;
	@Mock private Client client;
	@Mock private ClientThread clientThread;
	@Mock private ConfigManager configManager;
	@Mock private PluginManager pluginManager;
	@Mock private EventBus eventBus;
	@Mock private StorageManagerManager storageManagerManager;

	private DwmsImporter importer;

	@Before
	public void setUp()
	{
		importer = new DwmsImporter(plugin, client, clientThread, configManager, pluginManager, eventBus);

		when(plugin.getStorageManagerManager()).thenReturn(storageManagerManager);
		when(plugin.getClientThread()).thenReturn(clientThread);
		when(storageManagerManager.getStorageManagers()).thenReturn(Collections.emptyList());
		when(configManager.getRSProfileKey()).thenReturn(PROFILE);
		when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());

		// Run clientThread.invokeLater() work synchronously so tests can assert on the result.
		doAnswer(invocation ->
		{
			Runnable r = invocation.getArgument(0);
			r.run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
	}

	// ---- isImportedKey ----

	@Test
	public void isImportedKeyAcceptsTrackingPrefixesAndMinutesPlayed()
	{
		assertTrue(DwmsImporter.isImportedKey("carryable.inventory"));
		assertTrue(DwmsImporter.isImportedKey("death.deathpile1"));
		assertTrue(DwmsImporter.isImportedKey("poh.mountedItems"));
		assertTrue(DwmsImporter.isImportedKey("world.hallowedSepulchre"));
		assertTrue(DwmsImporter.isImportedKey("sailing.cargoHold"));
		assertTrue(DwmsImporter.isImportedKey("stash.unit1"));
		assertTrue(DwmsImporter.isImportedKey("minutesPlayed"));
	}

	@Test
	public void isImportedKeyRejectsCoinsAndMinigames()
	{
		assertFalse(DwmsImporter.isImportedKey("coins"));
		assertFalse(DwmsImporter.isImportedKey("minigames.temporossReward"));
		assertFalse(DwmsImporter.isImportedKey("somethingElseEntirely"));
	}

	// ---- copyConfig ----

	@Test
	public void copyConfigInFillGapsModeOnlyCopiesKeysWeHaveNoDataFor()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Arrays.asList("carryable.inventory", "poh.mountedItems", "coins"));
		when(configManager.getConfiguration(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, "carryable.inventory"))
			.thenReturn("inv-data");
		when(configManager.getConfiguration(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, "poh.mountedItems"))
			.thenReturn("poh-data");

		Set<String> existing = new HashSet<>(Collections.singletonList("poh.mountedItems"));
		int copied = importer.copyConfig(PROFILE, Mode.FILL_GAPS, existing);

		assertEquals(1, copied);
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "carryable.inventory", "inv-data");
		verify(configManager, never()).setConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "poh.mountedItems", "poh-data");
		// coins is not an imported key at all
		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("coins"), anyString());
	}

	@Test
	public void copyConfigInOverwriteModeCopiesEvenExistingKeys()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Arrays.asList("carryable.inventory", "poh.mountedItems"));
		when(configManager.getConfiguration(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, "carryable.inventory"))
			.thenReturn("inv-data");
		when(configManager.getConfiguration(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, "poh.mountedItems"))
			.thenReturn("poh-data");

		Set<String> existing = new HashSet<>(Arrays.asList("carryable.inventory", "poh.mountedItems"));
		int copied = importer.copyConfig(PROFILE, Mode.OVERWRITE, existing);

		assertEquals(2, copied);
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "carryable.inventory", "inv-data");
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "poh.mountedItems", "poh-data");
	}

	@Test
	public void copyConfigIgnoresCoinsAndMinigamesCategories()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Arrays.asList("coins", "minigames.tempoross"));

		int copied = importer.copyConfig(PROFILE, Mode.OVERWRITE, Collections.emptySet());

		assertEquals(0, copied);
		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("coins"), anyString());
		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("minigames.tempoross"), anyString());
	}

	@Test
	public void copyConfigSkipsKeysWithNullValue()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.singletonList("carryable.inventory"));
		when(configManager.getConfiguration(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, "carryable.inventory"))
			.thenReturn(null);

		int copied = importer.copyConfig(PROFILE, Mode.OVERWRITE, Collections.emptySet());

		assertEquals(0, copied);
	}

	@Test
	public void copyConfigAlwaysMarksImportTimestamp()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());

		importer.copyConfig(PROFILE, Mode.OVERWRITE, Collections.emptySet());

		verify(configManager).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE),
			eq(DwmsImporter.CONFIG_KEY_IMPORTED_AT), anyString());
	}

	// ---- hasImportedBefore ----

	@Test
	public void hasImportedBeforeIsTrueWhenTimestampKeyPresent()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, DwmsImporter.CONFIG_KEY_IMPORTED_AT))
			.thenReturn("123456");

		assertTrue(importer.hasImportedBefore(PROFILE));
	}

	@Test
	public void hasImportedBeforeIsFalseWhenTimestampKeyAbsent()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, DwmsImporter.CONFIG_KEY_IMPORTED_AT))
			.thenReturn(null);

		assertFalse(importer.hasImportedBefore(PROFILE));
	}

	@Test
	public void hasImportedBeforeIsFalseWithoutProfileKey()
	{
		assertFalse(importer.hasImportedBefore(null));
	}

	// ---- applyLiveStorages ----

	private static Map<String, Object> itemEntry(int id, long quantity)
	{
		Map<String, Object> item = new HashMap<>();
		item.put("id", id);
		item.put("quantity", quantity);
		return item;
	}

	private static Map<String, Object> storageEntry(String category, String name, long lastUpdated, List<Map<String, Object>> items)
	{
		Map<String, Object> entry = new HashMap<>();
		entry.put("category", category);
		entry.put("name", name);
		entry.put("lastUpdated", lastUpdated);
		entry.put("items", items);
		return entry;
	}

	@Test
	public void applyLiveStoragesImportsItemsIntoMatchingStorage()
	{
		ItemStorage<?> storage = mock(ItemStorage.class);
		StorageType type = mock(StorageType.class);
		when(type.getConfigKey()).thenReturn("inventory");
		when(storage.getType()).thenReturn(type);
		when(storageManagerManager.findStorage("carryable", "Inventory")).thenReturn(Optional.of(storage));

		List<Map<String, Object>> items = Arrays.asList(itemEntry(995, 100L), itemEntry(526, 5L));
		Map<String, Object> data = new HashMap<>();
		data.put("storages", Collections.singletonList(storageEntry("carryable", "Inventory", 42L, items)));

		int applied = importer.applyLiveStorages(data, Mode.OVERWRITE, Collections.emptySet());

		assertEquals(1, applied);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List> stacksCaptor = ArgumentCaptor.forClass(List.class);
		verify(storage).importItems(stacksCaptor.capture(), eq(42L));
		assertEquals(2, stacksCaptor.getValue().size());
	}

	@Test
	public void applyLiveStoragesSkipsDeathCategory()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("storages", Collections.singletonList(storageEntry("death", "Deathpile", 1L, Collections.emptyList())));

		int applied = importer.applyLiveStorages(data, Mode.OVERWRITE, Collections.emptySet());

		assertEquals(0, applied);
		verify(storageManagerManager, never()).findStorage(eq("death"), anyString());
	}

	@Test
	public void applyLiveStoragesSkipsUnknownStorage()
	{
		when(storageManagerManager.findStorage("carryable", "Inventory")).thenReturn(Optional.empty());
		Map<String, Object> data = new HashMap<>();
		data.put("storages", Collections.singletonList(storageEntry("carryable", "Inventory", 1L, Collections.emptyList())));

		int applied = importer.applyLiveStorages(data, Mode.OVERWRITE, Collections.emptySet());

		assertEquals(0, applied);
	}

	@Test
	public void applyLiveStoragesInFillGapsModeSkipsStoragesWeAlreadyHaveData()
	{
		ItemStorage<?> storage = mock(ItemStorage.class);
		StorageType type = mock(StorageType.class);
		when(type.getConfigKey()).thenReturn("inventory");
		when(storage.getType()).thenReturn(type);
		when(storageManagerManager.findStorage("carryable", "Inventory")).thenReturn(Optional.of(storage));

		Map<String, Object> data = new HashMap<>();
		data.put("storages", Collections.singletonList(storageEntry("carryable", "Inventory", 1L, Collections.emptyList())));

		Set<String> existing = new HashSet<>(Collections.singletonList("carryable.inventory"));
		int applied = importer.applyLiveStorages(data, Mode.FILL_GAPS, existing);

		assertEquals(0, applied);
		verify(storage, never()).importItems(any(), any(Long.class));
	}

	@Test
	public void applyLiveStoragesInFillGapsModeAppliesStoragesWeHadNoDataFor()
	{
		ItemStorage<?> storage = mock(ItemStorage.class);
		StorageType type = mock(StorageType.class);
		when(type.getConfigKey()).thenReturn("inventory");
		when(storage.getType()).thenReturn(type);
		when(storageManagerManager.findStorage("carryable", "Inventory")).thenReturn(Optional.of(storage));

		Map<String, Object> data = new HashMap<>();
		data.put("storages", Collections.singletonList(storageEntry("carryable", "Inventory", 1L, Collections.emptyList())));

		int applied = importer.applyLiveStorages(data, Mode.FILL_GAPS, Collections.emptySet());

		assertEquals(1, applied);
	}

	@Test
	public void applyLiveStoragesReturnsZeroWhenStoragesKeyMissingOrWrongType()
	{
		assertEquals(0, importer.applyLiveStorages(new HashMap<>(), Mode.OVERWRITE, Collections.emptySet()));

		Map<String, Object> badShape = new HashMap<>();
		badShape.put("storages", "not-a-list");
		assertEquals(0, importer.applyLiveStorages(badShape, Mode.OVERWRITE, Collections.emptySet()));
	}

	@Test
	public void applyLiveStoragesSkipsEntriesThatAreNotMaps()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("storages", Arrays.asList("not-a-map", 42));

		int applied = importer.applyLiveStorages(data, Mode.OVERWRITE, Collections.emptySet());

		assertEquals(0, applied);
	}

	// ---- onPluginMessage ----

	@Test
	public void onPluginMessageIgnoresWrongNamespace()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", DwmsImporter.PLUGIN_MESSAGE_SOURCE);
		PluginMessage message = new PluginMessage("someOtherPlugin", DwmsImporter.DWMS_STORAGES_RESPONSE, data);

		importer.onPluginMessage(message);

		verify(plugin, never()).storagesChanged();
	}

	@Test
	public void onPluginMessageIgnoresWrongMessageName()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", DwmsImporter.PLUGIN_MESSAGE_SOURCE);
		PluginMessage message = new PluginMessage(DwmsImporter.DWMS_CONFIG_GROUP, "some-other-message", data);

		importer.onPluginMessage(message);

		verify(plugin, never()).storagesChanged();
	}

	@Test
	public void onPluginMessageIgnoresMessagesNotTargetedAtUs()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", "SomeoneElse");
		PluginMessage message = new PluginMessage(DwmsImporter.DWMS_CONFIG_GROUP, DwmsImporter.DWMS_STORAGES_RESPONSE, data);

		importer.onPluginMessage(message);

		verify(plugin, never()).storagesChanged();
	}

	@Test
	public void onPluginMessageIgnoredWhenNoImportIsPending()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", DwmsImporter.PLUGIN_MESSAGE_SOURCE);
		data.put("storages", Collections.emptyList());
		PluginMessage message = new PluginMessage(DwmsImporter.DWMS_CONFIG_GROUP, DwmsImporter.DWMS_STORAGES_RESPONSE, data);

		importer.onPluginMessage(message);

		verify(plugin, never()).storagesChanged();
	}

	@Test
	public void onPluginMessageCompletesPendingImportAndInvokesCallback()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());
		when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(fakeDwmsPlugin()));
		when(pluginManager.isPluginEnabled(any(Plugin.class))).thenReturn(true);

		@SuppressWarnings("unchecked")
		java.util.function.Consumer<Result> callback = mock(java.util.function.Consumer.class);
		importer.importNow(Mode.OVERWRITE, callback);

		Map<String, Object> data = new HashMap<>();
		data.put("target", DwmsImporter.PLUGIN_MESSAGE_SOURCE);
		data.put("storages", Collections.emptyList());
		PluginMessage message = new PluginMessage(DwmsImporter.DWMS_CONFIG_GROUP, DwmsImporter.DWMS_STORAGES_RESPONSE, data);

		importer.onPluginMessage(message);

		verify(plugin).storagesChanged();
		ArgumentCaptor<Result> resultCaptor = ArgumentCaptor.forClass(Result.class);
		verify(callback).accept(resultCaptor.capture());
		assertTrue(resultCaptor.getValue().isLiveResponseReceived());
		assertFalse(importer.isImporting());
	}

	// ---- onGameTick timeout ----

	@Test
	public void onGameTickDoesNothingWithoutPendingImport()
	{
		importer.onGameTick();
		verify(plugin, never()).storagesChanged();
	}

	@Test
	public void onGameTickTimesOutPendingImportAndInvokesCallbackOnceDeadlinePasses()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());
		when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(fakeDwmsPlugin()));
		when(pluginManager.isPluginEnabled(any(Plugin.class))).thenReturn(true);
		when(client.getTickCount()).thenReturn(0);

		@SuppressWarnings("unchecked")
		java.util.function.Consumer<Result> callback = mock(java.util.function.Consumer.class);
		importer.importNow(Mode.OVERWRITE, callback);

		// Not yet at the deadline.
		when(client.getTickCount()).thenReturn(5);
		importer.onGameTick();
		verify(callback, never()).accept(any(Result.class));

		// Deadline reached (RESPONSE_TIMEOUT_TICKS = 10).
		when(client.getTickCount()).thenReturn(10);
		importer.onGameTick();

		ArgumentCaptor<Result> resultCaptor = ArgumentCaptor.forClass(Result.class);
		verify(callback).accept(resultCaptor.capture());
		assertFalse(resultCaptor.getValue().isLiveResponseReceived());
		assertFalse(importer.isImporting());
	}

	@Test
	public void onGameTickAfterTimeoutIsIdempotent()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());
		when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(fakeDwmsPlugin()));
		when(pluginManager.isPluginEnabled(any(Plugin.class))).thenReturn(true);
		when(client.getTickCount()).thenReturn(0);

		@SuppressWarnings("unchecked")
		java.util.function.Consumer<Result> callback = mock(java.util.function.Consumer.class);
		importer.importNow(Mode.OVERWRITE, callback);

		when(client.getTickCount()).thenReturn(20);
		importer.onGameTick();
		importer.onGameTick();

		verify(callback, times(1)).accept(any(Result.class));
	}

	@Test
	public void importNowIsNoOpWhileAlreadyImporting()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());
		when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(fakeDwmsPlugin()));
		when(pluginManager.isPluginEnabled(any(Plugin.class))).thenReturn(true);
		when(client.getTickCount()).thenReturn(0);

		importer.importNow(Mode.OVERWRITE, null);
		assertTrue(importer.isImporting());

		importer.importNow(Mode.OVERWRITE, null);

		// Only the first importNow's work should have run (one PluginMessage posted).
		verify(eventBus, times(1)).post(any(PluginMessage.class));
	}

	@Test
	public void importNowFinishesImmediatelyWhenNoProfileKey()
	{
		when(configManager.getRSProfileKey()).thenReturn(null);

		@SuppressWarnings("unchecked")
		java.util.function.Consumer<Result> callback = mock(java.util.function.Consumer.class);
		importer.importNow(Mode.OVERWRITE, callback);

		ArgumentCaptor<Result> resultCaptor = ArgumentCaptor.forClass(Result.class);
		verify(callback).accept(resultCaptor.capture());
		assertEquals(0, resultCaptor.getValue().getConfigKeysCopied());
		assertFalse(importer.isImporting());
	}

	@Test
	public void importingFlagClearsWhenTheClientThreadWorkThrows()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(plugin).reload();

		@SuppressWarnings("unchecked")
		java.util.function.Consumer<Result> callback = mock(java.util.function.Consumer.class);
		importer.importNow(Mode.OVERWRITE, callback);

		ArgumentCaptor<Result> resultCaptor = ArgumentCaptor.forClass(Result.class);
		verify(callback).accept(resultCaptor.capture());
		assertFalse(resultCaptor.getValue().isLiveResponseReceived());
		assertFalse(importer.isImporting());
	}

	@Test
	public void importNowFinishesWithoutLivePhaseWhenDwmsNotEnabled()
	{
		when(configManager.getRSProfileConfigurationKeys(DwmsImporter.DWMS_CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());
		when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());

		@SuppressWarnings("unchecked")
		java.util.function.Consumer<Result> callback = mock(java.util.function.Consumer.class);
		importer.importNow(Mode.OVERWRITE, callback);

		ArgumentCaptor<Result> resultCaptor = ArgumentCaptor.forClass(Result.class);
		verify(callback).accept(resultCaptor.capture());
		assertFalse(resultCaptor.getValue().isLiveResponseReceived());
		assertFalse(importer.isImporting());
		verify(eventBus, never()).post(any(PluginMessage.class));
	}

	private Plugin fakeDwmsPlugin()
	{
		// Class name must exactly match DwmsImporter.DWMS_PLUGIN_CLASS; see the test-only stand-in
		// at dev.thource.runelite.dudewheresmystuff.DudeWheresMyStuffPlugin.
		return new dev.thource.runelite.dudewheresmystuff.DudeWheresMyStuffPlugin();
	}
}
