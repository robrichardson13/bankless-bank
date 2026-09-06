/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * BlastFurnace is responsible for tracking how many ores/bars the player has stored at Blast
 * Furnace.
 */
public class BlastFurnace extends WorldStorage {

  protected BlastFurnace(BanklessBankPlugin plugin) {
    super(WorldStorageType.BLAST_FURNACE, plugin);

    hasStaticItems = true;
    varbits =
        new int[] {
          VarbitID.BLAST_FURNACE_COPPER_ORE,
          VarbitID.BLAST_FURNACE_TIN_ORE,
          VarbitID.BLAST_FURNACE_IRON_ORE,
          VarbitID.BLAST_FURNACE_COAL,
          VarbitID.BLAST_FURNACE_MITHRIL_ORE,
          VarbitID.BLAST_FURNACE_ADAMANTITE_ORE,
          VarbitID.BLAST_FURNACE_RUNITE_ORE,
          VarbitID.BLAST_FURNACE_SILVER_ORE,
          VarbitID.BLAST_FURNACE_GOLD_ORE,
          VarbitID.BLAST_FURNACE_BRONZE_BARS,
          VarbitID.BLAST_FURNACE_IRON_BARS,
          VarbitID.BLAST_FURNACE_STEEL_BARS,
          VarbitID.BLAST_FURNACE_MITHRIL_BARS,
          VarbitID.BLAST_FURNACE_ADAMANTITE_BARS,
          VarbitID.BLAST_FURNACE_RUNITE_BARS,
          VarbitID.BLAST_FURNACE_SILVER_BARS,
          VarbitID.BLAST_FURNACE_GOLD_BARS
        };

    items.add(new ItemStack(ItemID.COPPER_ORE, plugin));
    items.add(new ItemStack(ItemID.TIN_ORE, plugin));
    items.add(new ItemStack(ItemID.IRON_ORE, plugin));
    items.add(new ItemStack(ItemID.COAL, plugin));
    items.add(new ItemStack(ItemID.MITHRIL_ORE, plugin));
    items.add(new ItemStack(ItemID.ADAMANTITE_ORE, plugin));
    items.add(new ItemStack(ItemID.RUNITE_ORE, plugin));
    items.add(new ItemStack(ItemID.SILVER_ORE, plugin));
    items.add(new ItemStack(ItemID.GOLD_ORE, plugin));
    items.add(new ItemStack(ItemID.BRONZE_BAR, plugin));
    items.add(new ItemStack(ItemID.IRON_BAR, plugin));
    items.add(new ItemStack(ItemID.STEEL_BAR, plugin));
    items.add(new ItemStack(ItemID.MITHRIL_BAR, plugin));
    items.add(new ItemStack(ItemID.ADAMANTITE_BAR, plugin));
    items.add(new ItemStack(ItemID.RUNITE_BAR, plugin));
    items.add(new ItemStack(ItemID.SILVER_BAR, plugin));
    items.add(new ItemStack(ItemID.GOLD_BAR, plugin));
  }
}
