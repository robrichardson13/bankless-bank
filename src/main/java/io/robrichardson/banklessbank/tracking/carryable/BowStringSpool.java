/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.carryable;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * BowStringSpool is responsible for tracking how many bow strings the player has on their bow
 * string spool.
 */
@Getter
public class BowStringSpool extends CarryableStorage {
  BowStringSpool(BanklessBankPlugin plugin) {
    super(CarryableStorageType.BOW_STRING_SPOOL, plugin);

    hasStaticItems = true;

    items.add(new ItemStack(ItemID.BOW_STRING, plugin));

    varbits = new int[] {
        VarbitID.BOWSTRING_SPOOL_CHARGES
    };
  }
}
