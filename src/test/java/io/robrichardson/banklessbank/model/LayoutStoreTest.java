package io.robrichardson.banklessbank.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
		layout.getMainTab().append(42);

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
		original.getMainTab().append(1);
		original.getMainTab().append(2);
		original.getMainTab().append(3);
		int tabIdx = original.createTab(10);
		original.getTab(tabIdx).append(11);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals(original.getTabs().size(), loaded.getTabs().size());
		assertEquals(original.getMainTab().getSlots(), loaded.getMainTab().getSlots());
		assertEquals(original.getTab(tabIdx).getSlots(), loaded.getTab(tabIdx).getSlots());
	}

	@Test
	public void renamedTabNameRoundTripsThroughGson()
	{
		BankLayout original = new BankLayout();
		int tabIdx = original.createTab(10);
		original.renameTab(original.indexOfMainTab(), "Runes");
		original.renameTab(tabIdx, "Seeds");

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals("Runes", loaded.getMainTab().getName());
		assertEquals("Seeds", loaded.getTab(tabIdx).getName());
	}

	@Test
	public void legacyJsonWithoutANameFieldFallsBackToTheDefaultName()
	{
		// Saved before BankTab had a name field at all - Gson leaves the field null, and normalise()
		// (called by load()) must fall back to the positional default, not an empty string.
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{\"tabs\":[{\"main\":true,\"slots\":[1]},{\"slots\":[2]}]}");

		BankLayout loaded = store.load(PROFILE);

		assertEquals("Main", loaded.getMainTab().getName());
		assertEquals("Tab 2", loaded.getTab(1).getName());
	}

	@Test
	public void oldDenseLayoutJsonWithoutIconOrIgnoreListLoadsUnchanged()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{\"tabs\":[{\"name\":\"Main\",\"slots\":[1,2,3]}]}");

		BankLayout loaded = store.load(PROFILE);

		assertEquals(Arrays.asList(1, 2, 3), loaded.getMainTab().getSlots());
		assertEquals(-1, loaded.getMainTab().getIcon());
		assertTrue(loaded.getPlaceholderIgnoreIds().isEmpty());
	}

	@Test
	public void oldTenTabLayoutJsonMergesTheSurplusTabIntoMainInsteadOfDroppingIt()
	{
		// Saved before MAX_TABS dropped from 10 to 9: Main plus nine custom tabs.
		StringBuilder json = new StringBuilder("{\"tabs\":[{\"name\":\"Main\",\"slots\":[1]}");
		for (int i = 1; i <= 9; i++)
		{
			json.append(",{\"name\":\"Tab ").append(i).append("\",\"slots\":[").append(1000 + i).append("]}");
		}
		json.append("]}");
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn(json.toString());

		BankLayout loaded = store.load(PROFILE);

		assertEquals(BankLayout.MAX_TABS, loaded.getTabs().size());
		assertTrue("the tenth (surplus) tab's item must be merged into Main, not dropped",
			loaded.getMainTab().contains(1009));
	}

	@Test
	public void sparseSlotsRoundTripThroughGsonWithGapsIntact()
	{
		BankLayout original = new BankLayout();
		original.getMainTab().setAt(0, 1);
		original.getMainTab().setAt(3, 2);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals(Arrays.asList(1, null, null, 2), loaded.getMainTab().getSlots());
	}

	@Test
	public void mainTabFlagRoundTripsWhenMainIsNotFirst()
	{
		BankLayout original = new BankLayout();
		original.createTab(10);
		original.createTab(20);
		// Drag the main tab from index 0 to the end of the strip.
		boolean moved = original.moveTab(original.indexOfMainTab(), original.getTabs().size() - 1);
		assertTrue(moved);
		int mainIndexBeforeSave = original.indexOfMainTab();
		assertEquals(2, mainIndexBeforeSave);
		original.getMainTab().append(99);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals(3, loaded.getTabs().size());
		assertEquals(mainIndexBeforeSave, loaded.indexOfMainTab());
		assertTrue(loaded.getTab(mainIndexBeforeSave).isMain());
		assertTrue(loaded.getMainTab().contains(99));
		// Exactly one tab survives normalise() flagged main.
		int mainCount = 0;
		for (BankTab tab : loaded.getTabs())
		{
			if (tab.isMain())
			{
				mainCount++;
			}
		}
		assertEquals(1, mainCount);
	}

	@Test
	public void legacyJsonWithoutMainFlagTreatsSlotZeroAsMain()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{\"tabs\":[{\"name\":\"Main\",\"slots\":[1,2]},{\"name\":\"Tab 1\",\"slots\":[3]}]}");

		BankLayout loaded = store.load(PROFILE);

		assertEquals(0, loaded.indexOfMainTab());
		assertTrue(loaded.getTab(0).isMain());
		assertFalse(loaded.getTab(1).isMain());
	}

	@Test
	public void tabLayoutWidthRoundTrips()
	{
		BankLayout original = new BankLayout();
		original.getMainTab().append(1);
		original.getMainTab().setCols(12);
		int idx = original.createTab(5);
		original.getTab(idx).setCols(4);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		assertTrue("the width must actually be written: " + json, json.contains("\"cols\":12"));
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals(12, loaded.getMainTab().getCols());
		assertEquals(4, loaded.getTab(idx).getCols());
	}

	@Test
	public void legacyJsonWithoutATabWidthDefaultsToEightColumns()
	{
		// Every layout saved before tabs had a width was arranged on a fixed 8-column grid, so 8 is
		// the width that leaves those arrangements looking exactly as they did.
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{\"tabs\":[{\"name\":\"Main\",\"main\":true,\"slots\":[1,2,3]}]}");

		BankLayout loaded = store.load(PROFILE);

		assertEquals(BankTab.DEFAULT_COLS, loaded.getMainTab().getCols());
	}

	@Test
	public void aCorruptTabWidthIsRepairedOnLoad()
	{
		// Zero would divide the grid by zero on the very next frame.
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{\"tabs\":[{\"name\":\"Main\",\"main\":true,\"cols\":0,\"slots\":[1]},"
				+ "{\"name\":\"Tab 1\",\"cols\":9999,\"slots\":[2]}]}");

		BankLayout loaded = store.load(PROFILE);

		assertEquals(BankTab.MIN_COLS, loaded.getMainTab().getCols());
		assertEquals(BankTab.MAX_COLS, loaded.getTab(1).getCols());
	}

	@Test
	public void tabIconRoundTrips()
	{
		BankLayout original = new BankLayout();
		int idx = original.createTab(5);
		original.getTab(idx).append(6);
		original.setTabIcon(idx, 6);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertEquals(6, loaded.getTab(idx).getIcon());
	}

	@Test
	public void placeholderIgnoreListRoundTrips()
	{
		BankLayout original = new BankLayout();
		original.addPlaceholderIgnore(42);
		original.addPlaceholderIgnore(43);

		Gson gson = new Gson();
		String json = gson.toJson(original);
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(json);

		BankLayout loaded = store.load(PROFILE);

		assertTrue(loaded.isPlaceholderIgnored(42));
		assertTrue(loaded.isPlaceholderIgnored(43));
	}

	@Test
	public void layoutJsonHasNoTrailingNullsAfterASave()
	{
		BankLayout layout = new BankLayout();
		layout.getMainTab().setAt(0, 1);
		layout.getMainTab().setAt(2, 2);
		layout.getMainTab().setAt(2, null); // blanking the last occupied slot must trim, not append null

		Gson gson = new Gson();
		String json = gson.toJson(layout);

		assertEquals(Arrays.asList(1), layout.getMainTab().getSlots());
		assertFalse("saved JSON must not carry a trailing null: " + json, json.endsWith(",null]}]}"));
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
	public void loadSurvivesStructurallyValidJsonWithANullTabEntry()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout"))
			.thenReturn("{\"tabs\":[{\"name\":\"Main\",\"slots\":[11,22]},null]}");

		BankLayout layout = store.load(PROFILE);

		assertEquals(1, layout.getTabs().size());
		assertEquals(Arrays.asList(11, 22), layout.getMainTab().getSlots());
	}

	@Test
	public void loadReturnsEmptyDefaultLayoutWhenJsonParsesToNull()
	{
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn("null");

		BankLayout layout = store.load(PROFILE);

		assertEquals(1, layout.getTabs().size());
	}

	/**
	 * Guards against silently exceeding RuneLite's server-side sync limits for the $rsprofile
	 * scope every RS-profile-keyed value (including this one) rides: MAX_VALUE_LENGTH (262144
	 * bytes) and MAX_DEPTH (8 levels of JSON nesting). A maximally full layout must stay
	 * comfortably inside both.
	 */
	@Test
	public void layoutStaysWithinSyncLimits()
	{
		BankLayout layout = new BankLayout();
		int nextId = 1;
		for (int t = 0; t < BankLayout.MAX_TABS - 1; t++)
		{
			layout.createTab(nextId++);
		}
		for (BankTab tab : layout.getTabs())
		{
			// The widest layout a tab can have, so this really is the largest layout possible.
			tab.setCols(BankTab.MAX_COLS);
			for (int slot = 0; slot < BankTab.MAX_SLOTS; slot++)
			{
				if (tab.getSlots().size() > slot && tab.getSlots().get(slot) != null)
				{
					continue; // the tab-icon item createTab() already placed at slot 0
				}
				tab.setAt(slot, nextId++);
			}
		}
		for (int i = 0; i < 200; i++)
		{
			layout.addPlaceholderIgnore(nextId++);
		}

		String json = new Gson().toJson(layout);

		assertTrue("layout JSON must stay under RuneLite's 262144-byte MAX_VALUE_LENGTH: " + json.length(),
			json.length() < 200_000);
		assertTrue("layout JSON must stay under RuneLite's MAX_DEPTH of 8", maxJsonDepth(json) <= 6);
	}

	/** Small brace/bracket counter: the deepest nesting of {@code {}} and {@code []} in the string. */
	private static int maxJsonDepth(String json)
	{
		int depth = 0;
		int max = 0;
		for (int i = 0; i < json.length(); i++)
		{
			char c = json.charAt(i);
			if (c == '{' || c == '[')
			{
				depth++;
				max = Math.max(max, depth);
			}
			else if (c == '}' || c == ']')
			{
				depth--;
			}
		}
		return max;
	}
}
