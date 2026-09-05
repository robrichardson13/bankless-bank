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
	@Mock private StorageManagerManager storageManagerManager;
	@Mock private ItemComposition itemComposition;

	private BankViewController controller;

	@Before
	public void setUp()
	{
		controller = new BankViewController(plugin, client, itemManager, configManager, config, layoutStore);
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
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewMode", controller.getViewModel().getMode().name());
		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewTab", controller.getViewModel().getActiveTab());
	}
}
