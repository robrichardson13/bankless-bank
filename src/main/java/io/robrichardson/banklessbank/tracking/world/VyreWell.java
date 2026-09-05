/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * VyreWell is responsible for tracking how many blood runes and vials the player has stored in the
 * vyre well.
 */
public class VyreWell extends WorldStorage {

  private final ItemStack vialsOfBlood;
  private final ItemStack bloodRunes;

  protected VyreWell(BanklessBankPlugin plugin) {
    super(WorldStorageType.VYRE_WELL, plugin);

    hasStaticItems = true;

    varbits = new int[] {VarbitID.TOB_LOBBY_WELL_CONTENTS};

    vialsOfBlood = new ItemStack(ItemID.VIAL_BLOOD, plugin);
    items.add(vialsOfBlood);
    bloodRunes = new ItemStack(ItemID.BLOODRUNE, plugin);
    items.add(bloodRunes);
  }

  @Override
  public boolean onVarbitChanged(VarbitChanged varbitChanged) {
    boolean updated = super.onVarbitChanged(varbitChanged);

    if (updated) {
      bloodRunes.setQuantity(vialsOfBlood.getQuantity() * 200L);
    }

    return updated;
  }
}
