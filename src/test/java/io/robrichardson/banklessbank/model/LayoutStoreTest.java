package io.robrichardson.banklessbank.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.robrichardson.banklessbank.BanklessBankConfig;
import java.util.Arrays;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Verifies {@link LayoutStore}'s JSON round-trip and its use of {@link ConfigManager}. */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LayoutStoreTest
{
	private static final String PROFILE = "profile-key";

	@Mock private ConfigManager configManager;
	private LayoutStore store;

	@Before
	public void setUp()
	{
		store = new LayoutStore(configManager, new Gson());
	}

	@Test
	public void saveWritesJsonUnderBanklessBankGroupProfileAndLayoutKey()
	{
		BankLayout layout = new BankLayout();
		layout.getMainTab().getSlots().add(42);

		store.save(PROFILE, layout);

		verify(configManager).setConfiguration(
			eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("layout"), anyString());
	}

	@Test
	public void saveIsNoOpWithoutProfileKey()
	{
		store.save(null, new BankLayout());

		verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	public void loadRoundTripsThroughGson()
	{
		BankLayout original = new BankLayout();
		original.getMainTab().getSlots().addAll(Arrays.asList(1, 2, 3));
		int tabIdx = original.createTab(10);
		original.getTab(tabIdx).getSlots().add(11);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals(original.getTabs().size(), loaded.getTabs().size());
		assertEquals(original.getMainTab().getSlots(), loaded.getMainTab().getSlots());
		assertEquals(original.getTab(tabIdx).getSlots(), loaded.getTab(tabIdx).getSlots());
	}

	@Test
	public void loadUsesProfileScopedGetConfiguration()
	{
		when(configManager.getConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("layout")))
			.thenReturn(null);

		store.load(PROFILE);

		verify(configManager).getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout");
	}

	@Test
	public void loadReturnsEmptyDefaultLayoutWhenNoProfileKey()
	{
		BankLayout layout = store.load(null);

		assertTrue(layout.getTabs().size() == 1);
		assertTrue(layout.getMainTab().getSlots().isEmpty());
	}

	@Test
	public void loadReturnsEmptyDefaultLayoutWhenConfigIsMissing()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(null);

		BankLayout layout = store.load(PROFILE);

		assertEquals(1, layout.getTabs().size());
		assertTrue(layout.getMainTab().getSlots().isEmpty());
	}

	@Test
	public void loadReturnsEmptyDefaultLayoutWhenConfigIsEmptyString()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn("");

		BankLayout layout = store.load(PROFILE);

		assertEquals(1, layout.getTabs().size());
	}

	@Test
	public void loadReturnsEmptyDefaultLayoutOnCorruptJsonWithoutThrowing()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{not valid json!!");

		BankLayout layout = store.load(PROFILE);

		assertEquals(1, layout.getTabs().size());
		assertTrue(layout.getMainTab().getSlots().isEmpty());
	}

	@Test
	public void loadReturnsEmptyDefaultLayoutWhenJsonParsesToNull()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn("null");

		BankLayout layout = store.load(PROFILE);

		assertEquals(1, layout.getTabs().size());
	}
}
