package io.robrichardson.banklessbank.ui.harness;

import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.Storage;
import io.robrichardson.banklessbank.tracking.death.ExpiringDeathStorage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.mockito.Mockito;

/**
 * A realistic UIM's tracked items, expressed the way the tracking layer hands them to
 * {@link io.robrichardson.banklessbank.ui.BankViewController}: mock {@link Storage} instances whose
 * {@code getItems()} return {@link ItemStack}s. The death pile is a mock
 * {@link ExpiringDeathStorage} so the controller picks up its expiry subtitle.
 *
 * <p>97 distinct item ids in total (91 owned plus 6 placeholder-only ids in the saved layout),
 * which at 8 columns is 13 content rows against a 6-row viewport, so every screenshot has a live
 * scrollbar.
 */
public final class FakeStorages
{
	/** One item in one storage. */
	public static final class Item
	{
		public final int id;
		public final String name;
		public final long quantity;
		public final boolean stackable;

		Item(int id, String name, long quantity, boolean stackable)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.stackable = stackable;
		}
	}

	/** One storage: a display name, an optional expiry subtitle, and its items. */
	public static final class Spec
	{
		public final String name;
		public final String expireText;
		public final List<Item> items;

		Spec(String name, String expireText, List<Item> items)
		{
			this.name = name;
			this.expireText = expireText;
			this.items = items;
		}
	}

	public static final int COINS = 995;
	public static final int AIR_RUNE = 556;
	public static final int LAW_RUNE = 563;
	public static final int RUNE_PLATEBODY = 1127;
	public static final int RANARR_SEED = 5295;

	/** Ids that appear only in the saved layout, never in a storage: the placeholder cases. */
	public static final List<Integer> UNOWNED_IDS =
		Arrays.asList(1215, 1319, 4153, 4587, 8839, 8850);

	private static final Map<Integer, String> UNOWNED_NAMES = new LinkedHashMap<>();

	static
	{
		UNOWNED_NAMES.put(1215, "Dragon dagger");
		UNOWNED_NAMES.put(1319, "Rune 2h sword");
		UNOWNED_NAMES.put(4153, "Granite maul");
		UNOWNED_NAMES.put(4587, "Dragon scimitar");
		UNOWNED_NAMES.put(8839, "Void knight top");
		UNOWNED_NAMES.put(8850, "Rune defender");
	}

	private final List<Spec> specs = new ArrayList<>();
	private final Map<Integer, String> names = new LinkedHashMap<>();

	private FakeStorages()
	{
	}

	public static FakeStorages uim()
	{
		final FakeStorages fixture = new FakeStorages();

		fixture.add("Inventory", null,
			stack(COINS, "Coins", 1_200_000),
			stack(AIR_RUNE, "Air rune", 4000),
			stack(LAW_RUNE, "Law rune", 1500),
			stack(560, "Death rune", 850),
			stack(565, "Blood rune", 420),
			stack(8013, "Teleport to house", 12),
			stack(8007, "Varrock teleport", 20),
			one(385, "Shark"),
			one(7946, "Monkfish"),
			one(13441, "Anglerfish"),
			one(2434, "Prayer potion(4)"),
			one(12695, "Super combat potion(4)"),
			one(12625, "Stamina potion(4)"),
			one(2444, "Ranging potion(4)"),
			one(2452, "Antifire potion(4)"),
			one(6685, "Saradomin brew(4)"),
			one(3024, "Super restore(4)"),
			one(1712, "Amulet of glory(6)"),
			one(1275, "Rune pickaxe"),
			one(6739, "Dragon axe"),
			one(1359, "Rune axe"),
			one(303, "Small fishing net"),
			one(301, "Lobster pot"),
			one(590, "Tinderbox"),
			one(946, "Knife"),
			one(2347, "Hammer"),
			one(1755, "Chisel"),
			one(952, "Spade"));

		fixture.add("Equipment", null,
			one(RUNE_PLATEBODY, "Rune platebody"),
			one(1079, "Rune platelegs"),
			one(1163, "Rune full helm"),
			one(1201, "Rune kiteshield"),
			one(1333, "Rune scimitar"),
			one(1725, "Amulet of strength"),
			one(3105, "Climbing boots"),
			one(1059, "Leather gloves"),
			one(2570, "Ring of life"),
			one(1052, "Cape of legends"),
			one(7458, "Rune gloves"));

		fixture.add("Looting bag", null,
			stack(892, "Rune arrow", 750),
			many(2363, "Runite bar", 5),
			many(453, "Coal", 120),
			many(444, "Gold ore", 40),
			many(1617, "Uncut diamond", 6),
			many(1619, "Uncut ruby", 9),
			many(536, "Dragon bones", 25),
			many(532, "Big bones", 60),
			many(245, "Wine of zamorak", 15),
			many(207, "Grimy ranarr weed", 30));

		// Overlaps the inventory's air and law runes, so those slots have a two-line tooltip.
		fixture.add("Rune pouch", null,
			stack(AIR_RUNE, "Air rune", 3000),
			stack(LAW_RUNE, "Law rune", 900),
			stack(564, "Cosmic rune", 1200));

		fixture.add("Seed box", null,
			many(RANARR_SEED, "Ranarr seed", 12),
			many(5300, "Snapdragon seed", 4),
			many(5304, "Torstol seed", 2),
			many(5321, "Watermelon seed", 30),
			one(5316, "Magic seed"),
			many(5315, "Yew seed", 3),
			many(5289, "Palm tree seed", 5),
			many(5288, "Papaya tree seed", 7));

		fixture.add("Costume room", null,
			one(1033, "Zamorak robe top"),
			one(1035, "Zamorak robe legs"),
			one(10386, "Saradomin robe top"),
			one(10388, "Guthix robe top"),
			one(1053, "Black h'ween mask"),
			one(1050, "Santa hat"),
			one(1037, "Bunny ears"),
			one(4566, "Rubber chicken"),
			one(2635, "Beret"),
			one(2639, "Cavalier"),
			one(2631, "Highwayman mask"),
			one(579, "Wizard hat"));

		fixture.add("STASH: Wizards' Tower", null,
			one(577, "Blue wizard robe"),
			one(608, "Blue skirt"),
			one(1381, "Staff of air"));

		fixture.add("STASH: Falador", null,
			one(1121, "Mithril platebody"),
			one(1021, "Blue cape"),
			one(1139, "Bronze med helm"));

		fixture.add("STASH: Draynor Village", null,
			one(1949, "Chef's hat"),
			one(1007, "Red cape"),
			one(1129, "Leather body"));

		fixture.add("Deathpile (Lumbridge)", "Expires in 42:17",
			one(4151, "Abyssal whip"),
			one(11840, "Dragon boots"),
			one(6737, "Berserker ring"),
			one(6570, "Fire cape"),
			one(7462, "Barrows gloves"),
			one(12954, "Dragon defender"),
			one(6585, "Amulet of fury"),
			one(11832, "Bandos chestplate"),
			one(11834, "Bandos tassets"),
			one(11785, "Armadyl crossbow"),
			one(12926, "Toxic blowpipe"),
			one(12002, "Occult necklace"));

		fixture.names.putAll(UNOWNED_NAMES);
		return fixture;
	}

	private static Item one(int id, String name)
	{
		return new Item(id, name, 1L, false);
	}

	private static Item many(int id, String name, long quantity)
	{
		return new Item(id, name, quantity, false);
	}

	private static Item stack(int id, String name, long quantity)
	{
		return new Item(id, name, quantity, true);
	}

	private void add(String storageName, String expireText, Item... items)
	{
		final List<Item> list = Arrays.asList(items);
		for (Item item : list)
		{
			names.putIfAbsent(item.id, item.name);
		}
		specs.add(new Spec(storageName, expireText, new ArrayList<>(list)));
	}

	/** Canonical id to display name for every id the fixture knows, owned or not. */
	public Map<Integer, String> names()
	{
		return names;
	}

	public List<Spec> specs()
	{
		return specs;
	}

	/**
	 * A fresh stream of mock storages, as {@code StorageManagerManager#getViewableStorages} hands
	 * them over. Rebuilt per call because the controller consumes the stream.
	 */
	public Stream<?> storages()
	{
		final List<Storage<?>> out = new ArrayList<>();
		for (Spec spec : specs)
		{
			final List<ItemStack> stacks = new ArrayList<>();
			for (Item item : spec.items)
			{
				stacks.add(new ItemStack(item.id, item.name, item.quantity, 0, 0, item.stackable));
			}

			final Storage<?> storage;
			if (spec.expireText != null)
			{
				final ExpiringDeathStorage death = Mockito.mock(ExpiringDeathStorage.class);
				Mockito.when(death.getExpireText()).thenReturn(spec.expireText);
				storage = death;
			}
			else
			{
				storage = Mockito.mock(Storage.class);
			}

			Mockito.when(storage.getName()).thenReturn(spec.name);
			Mockito.doReturn(stacks).when(storage).getItems();
			out.add(storage);
		}
		return out.stream();
	}

	/**
	 * The layout the player had saved before this session: six placeholder-only ids and coins at
	 * the head of the main tab, plus three custom tabs whose first item is the tab icon. Everything
	 * else lands in the main tab when the controller syncs the layout against what is owned.
	 */
	public static BankLayout savedLayout()
	{
		final BankLayout layout = new BankLayout();
		layout.getMainTab().getSlots().addAll(UNOWNED_IDS);
		layout.getMainTab().getSlots().add(COINS);

		layout.getTabs().add(tab("Gear", RUNE_PLATEBODY, 1079, 1163, 1201, 1333, 4151));
		layout.getTabs().add(tab("Runes", AIR_RUNE, LAW_RUNE, 560, 565, 564));
		layout.getTabs().add(tab("Seeds", RANARR_SEED, 5300, 5304, 5321, 5316));
		return layout;
	}

	private static BankTab tab(String name, int... ids)
	{
		final BankTab tab = new BankTab(name);
		for (int id : ids)
		{
			tab.getSlots().add(id);
		}
		return tab;
	}
}
