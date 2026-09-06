/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Annette is responsible for tracking how many drift nets the player has stored with Annette. */
public class Annette extends WorldStorage {

  protected Annette(BanklessBankPlugin plugin) {
    super(WorldStorageType.ANNETTE, plugin);

    hasStaticItems = true;
    varbits = new int[]{VarbitID.FOSSIL_DRIFTNET_STORE};

    items.add(new ItemStack(ItemID.FOSSIL_DRIFT_NET, plugin));
  }
}
