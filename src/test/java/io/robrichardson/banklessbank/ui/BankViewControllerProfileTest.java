package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.LayoutStore;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.Storage;
import io.robrichardson.banklessbank.tracking.StorageManagerManager;
import java.util.Collections;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Verifies {@link BankViewController#refresh()} does not touch the layout until the tracking
 * layer's deferred load for the current profile has actually run (fix-1-profile-race), and that
 * a profile switch clears the display-name cache and closes any open context menu.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class BankViewControllerProfileTest
{
	private static final String PROFILE = "profile-key";

	@Mock private BanklessBankPlugin plugin;
	@Mock private Client client;
	@Mock private ItemManager itemManager;
	@Mock private ConfigManager configManager;
	@Mock private BanklessBankConfig config;
	@Mock private LayoutStore layoutStore;
	@Mock(answer = org.mockito.Answers.RETURNS_SELF) private ChatboxItemSearch itemSearch;
	@Mock(answer = org.mockito.Answers.RETURNS_SELF) private ChatboxTextInput tabRenameInput;
	@Mock(answer = org.mockito.Answers.RETURNS_SELF) private ChatboxTextInput searchInput;
	@Mock private ChatboxPanelManager chatboxPanelManager;
	@Mock private StorageManagerManager storageManagerManager;
	@Mock private ItemComposition itemComposition;

	private BankViewController controller;

	@Before
	public void setUp()
	{
		controller = new BankViewController(plugin, client, itemManager, configManager, config,
			layoutStore, itemSearch, tabRenameInput, searchInput, chatboxPanelManager);
		controller.startUp();

		when(plugin.getStorageManagerManager()).thenReturn(storageManagerManager);
		when(storageManagerManager.getViewableStorages()).thenReturn(Stream.empty());
		when(storageManagerManager.getStorageManagers()).thenReturn(Collections.emptyList());
		when(itemManager.getItemComposition(any(Integer.class))).thenReturn(itemComposition);
		when(itemComposition.getName()).thenReturn("Some Item");
		when(itemManager.canonicalize(any(Integer.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	public void refreshDoesNothingUntilProfileIsLoaded()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(null);
		when(config.placeholders()).thenReturn(false);

		BankLayout preloaded = new BankLayout();
		preloaded.getMainTab().getSlots().add(1234);
		controller.getViewModel().setLayout(preloaded);

		controller.refresh();

		verify(layoutStore, never()).save(anyString(), any(BankLayout.class));
		assertEquals(Collections.singletonList(1234), controller.getViewModel().getLayout().getMainTab().getSlots());
	}

	@Test
	public void refreshSyncsAndSavesOnceProfileIsLoaded()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(config.placeholders()).thenReturn(false);

		controller.refresh();

		verify(layoutStore).load(PROFILE);
	}

	@Test
	public void profileSwitchClearsKnownNamesCache()
	{
		final String otherProfile = "other-profile";
		when(config.placeholders()).thenReturn(true);

		// Profile A: a storage holds id 5555, whose display name comes from the ItemStack itself
		// (not from ItemManager), and gets cached into knownNames.
		Storage<?> storage = org.mockito.Mockito.mock(Storage.class);
		ItemStack stack = new ItemStack(5555, "Cached Name", 1L, 0, 0, false);
		when(storage.getItems()).thenReturn(Collections.singletonList(stack));
		when(storage.getName()).thenReturn("Storage A");
		when(storageManagerManager.getViewableStorages()).thenAnswer(inv -> Stream.of(storage));

		BankLayout layoutA = new BankLayout();
		layoutA.getMainTab().getSlots().add(5555);
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(layoutA);

		controller.refresh();
		verify(itemManager, never()).getItemComposition(5555);

		// Profile B: id 5555 is only a placeholder in the saved layout now, no live storage holds
		// it. Without clearing knownNames on the switch, the stale cache entry from profile A
		// would mask this and ItemManager would never be asked to resolve the name.
		when(storageManagerManager.getViewableStorages()).thenReturn(Stream.empty());
		BankLayout layoutB = new BankLayout();
		layoutB.getMainTab().getSlots().add(5555);
		when(plugin.getLoadedProfileKey()).thenReturn(otherProfile);
		when(layoutStore.load(otherProfile)).thenReturn(layoutB);

		controller.refresh();

		verify(itemManager, atLeastOnce()).getItemComposition(5555);
	}

	@Test
	public void syncLayoutChangeClosesOpenContextMenu()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(config.placeholders()).thenReturn(true);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());

		// First refresh: profile loads with an empty layout and nothing owned yet.
		controller.refresh();
		java.awt.Rectangle titleBar = controller.getViewModel().titleBarRect();
		controller.getViewModel().openMenu(titleBar.x, titleBar.y);
		assertTrue(controller.getViewModel().isMenuOpen());

		// Second refresh: a newly-owned item arrives, changing the synced layout and, per the
		// fix, closing the now-stale menu.
		Storage<?> storage = org.mockito.Mockito.mock(Storage.class);
		ItemStack stack = new ItemStack(9999, "Test Item", 1L, 0, 0, false);
		when(storage.getItems()).thenReturn(Collections.singletonList(stack));
		when(storage.getName()).thenReturn("Test Storage");
		when(storageManagerManager.getViewableStorages()).thenAnswer(inv -> Stream.of(storage));
		when(plugin.isStoragesDirty()).thenReturn(true);

		controller.refresh();

		assertFalse(controller.getViewModel().isMenuOpen());
	}

	@Test
	public void refreshIsNoOpAfterStop()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(config.placeholders()).thenReturn(false);

		controller.stop();
		controller.refresh();

		verify(layoutStore, never()).load(anyString());
		verify(layoutStore, never()).save(anyString(), any(BankLayout.class));
	}

	@Test
	public void refreshClampsVisibleRowsToWhatTheCanvasCanHold()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(config.placeholders()).thenReturn(false);
		when(client.getCanvasHeight()).thenReturn(BankGeometry.height(4));

		controller.getViewModel().setVisibleRows(10);
		controller.refresh();

		assertEquals(4, controller.getViewModel().getVisibleRows());
		assertEquals(4, controller.getViewModel().getMaxRows());
	}

	@Test
	public void refreshLeavesVisibleRowsAloneWhenTheCanvasHasRoomToSpare()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(config.placeholders()).thenReturn(false);
		when(client.getCanvasHeight()).thenReturn(BankGeometry.height(BankGeometry.MAX_ROWS));

		controller.getViewModel().setVisibleRows(BankGeometry.DEFAULT_ROWS);
		controller.refresh();

		assertEquals(BankGeometry.DEFAULT_ROWS, controller.getViewModel().getVisibleRows());
	}

	@Test
	public void refreshPopulatesUnitPricesForEveryOwnedCanonicalId()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(config.placeholders()).thenReturn(false);
		when(itemManager.getItemPrice(5555)).thenReturn(4200);

		Storage<?> storage = org.mockito.Mockito.mock(Storage.class);
		ItemStack stack = new ItemStack(5555, "Whip", 1L, 0, 0, false);
		when(storage.getItems()).thenReturn(Collections.singletonList(stack));
		when(storage.getName()).thenReturn("Inventory");
		when(storageManagerManager.getViewableStorages()).thenAnswer(inv -> Stream.of(storage));

		controller.refresh();

		assertEquals(4200, controller.getViewModel().unitPrice(5555));
	}

	@Test
	public void syncProfileClearsUnitPricesOnAProfileChange()
	{
		final String otherProfile = "other-profile";
		when(config.placeholders()).thenReturn(false);
		when(itemManager.getItemPrice(5555)).thenReturn(4200);

		Storage<?> storage = org.mockito.Mockito.mock(Storage.class);
		ItemStack stack = new ItemStack(5555, "Whip", 1L, 0, 0, false);
		when(storage.getItems()).thenReturn(Collections.singletonList(stack));
		when(storage.getName()).thenReturn("Inventory");
		when(storageManagerManager.getViewableStorages()).thenAnswer(inv -> Stream.of(storage));

		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		controller.refresh();
		assertEquals(4200, controller.getViewModel().unitPrice(5555));

		when(storageManagerManager.getViewableStorages()).thenReturn(Stream.empty());
		when(plugin.getLoadedProfileKey()).thenReturn(otherProfile);
		when(layoutStore.load(otherProfile)).thenReturn(new BankLayout());
		controller.refresh();

		assertEquals("the stale price from the previous profile must not survive the switch",
			0, controller.getViewModel().unitPrice(5555));
	}

	@Test
	public void theSavedActiveTabIsRestoredOnTheFirstProfileLoad()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewTab", int.class))
			.thenReturn(1);
		when(config.placeholders()).thenReturn(true);
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);

		BankLayout saved = new BankLayout();
		saved.getMainTab().getSlots().add(1);
		saved.createTabWith(2);
		when(layoutStore.load(PROFILE)).thenReturn(saved);

		controller.startUp();
		controller.refresh();

		assertEquals(1, controller.getViewModel().getActiveTab());
	}

	@Test
	public void aLaterProfileSwitchStillFallsBackToTheAllTab()
	{
		final String otherProfile = "other-profile";
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewTab", int.class))
			.thenReturn(1);
		when(config.placeholders()).thenReturn(true);

		BankLayout saved = new BankLayout();
		saved.getMainTab().getSlots().add(1);
		saved.createTabWith(2);
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(saved);

		controller.startUp();
		controller.refresh();
		assertEquals(1, controller.getViewModel().getActiveTab());

		// A fresh stream per call: a Stream can only be consumed once, and the first refresh took it.
		when(storageManagerManager.getViewableStorages()).thenAnswer(inv -> Stream.empty());
		when(plugin.getLoadedProfileKey()).thenReturn(otherProfile);
		when(layoutStore.load(otherProfile)).thenReturn(new BankLayout());
		controller.refresh();

		assertEquals(-1, controller.getViewModel().getActiveTab());
	}

	@Test
	public void flushSavesLoadedProfileLayoutOnceAndWritesViewState()
	{
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(config.placeholders()).thenReturn(false);

		// Loads the profile so loadedProfileKey is populated.
		controller.refresh();
		controller.setPosition(0, 0);

		controller.flush();

		verify(layoutStore, atLeastOnce()).save(org.mockito.ArgumentMatchers.eq(PROFILE), any(BankLayout.class));
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewX", 0);
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewY", 0);
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows", controller.getViewModel().getVisibleRows());
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewCols", controller.getViewModel().getVisibleCols());
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewMode", controller.getViewModel().getMode().name());
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewTab", controller.getViewModel().getActiveTab());
	}

	@Test
	public void startUpRestoresTheSavedWindowSizeOnBothAxes()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows", int.class))
			.thenReturn(11);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewCols", int.class))
			.thenReturn(13);

		controller.startUp();

		assertEquals(13, controller.getViewModel().getVisibleCols());
		assertEquals(11, controller.getViewModel().getVisibleRows());
	}

	@Test
	public void aWindowWiderThanTheCanvasIsCappedByRefresh()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewCols", int.class))
			.thenReturn(BankGeometry.MAX_COLS);
		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(new BankLayout());
		when(client.getCanvasWidth()).thenReturn(BankGeometry.width(6));
		when(client.getCanvasHeight()).thenReturn(BankGeometry.height(BankGeometry.DEFAULT_ROWS));

		controller.startUp();
		assertEquals(BankGeometry.MAX_COLS, controller.getViewModel().getVisibleCols());

		controller.refresh();

		assertEquals(6, controller.getViewModel().getVisibleCols());
	}

	/**
	 * After an import replaces the config underneath the controller, {@link
	 * BankViewController#reloadFromConfig()} must make the next {@link BankViewController#refresh()}
	 * pick up the new layout and view state, and must not re-save the stale in-memory layout in
	 * between (the guard in {@code syncProfile()}).
	 */
	@Test
	public void reloadFromConfigReloadsLayoutAndViewState()
	{
		BankLayout original = new BankLayout();
		BankLayout imported = new BankLayout();
		imported.getMainTab().append(222);

		when(plugin.getLoadedProfileKey()).thenReturn(PROFILE);
		when(layoutStore.load(PROFILE)).thenReturn(original);
		when(config.placeholders()).thenReturn(false);
		when(client.getCanvasHeight()).thenReturn(BankGeometry.height(9));

		// Establish an initial loaded profile with its own (stale) layout.
		controller.refresh();
		controller.getViewModel().getLayout().getMainTab().append(111);
		controller.saveLayoutIfChanged();

		// Simulate the import: config now holds a different layout and view state.
		when(layoutStore.load(PROFILE)).thenReturn(imported);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows", int.class)).thenReturn(9);

		controller.reloadFromConfig();
		// A fresh stream per call: a Stream can only be consumed once, and the first refresh took it.
		when(storageManagerManager.getViewableStorages()).thenAnswer(inv -> Stream.empty());
		controller.refresh();

		assertEquals(java.util.Collections.singletonList(222), controller.getViewModel().getLayout().getMainTab().getSlots());
		assertEquals(9, controller.getViewModel().getVisibleRows());
		// The stale layout carrying item 111 must never have been written back over the import.
		verify(layoutStore, never()).save(anyString(), any(BankLayout.class));
	}
}
