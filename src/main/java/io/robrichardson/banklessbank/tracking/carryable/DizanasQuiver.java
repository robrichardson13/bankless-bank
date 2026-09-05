/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.carryable;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.Var;
import lombok.Getter;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;

/**
 * DizanasQuiver is responsible for tracking how many arrows the player has stored in their Dizana's
 * quiver.
 */
@Getter
public class DizanasQuiver extends CarryableStorage {

  private ItemStack ammo;

  DizanasQuiver(BanklessBankPlugin plugin) {
    super(CarryableStorageType.DIZANAS_QUIVER, plugin);

    resetItems();
  }

  @Override
  public boolean onVarbitChanged(VarbitChanged varbitChanged) {
    var client = plugin.getClient();

    var typeVar = Var.player(varbitChanged, VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
    if (typeVar.wasChanged()) {
      var newType = typeVar.getValue(client);
      if (newType != ammo.getId()) {
        ammo.setId(newType, plugin);
        return true;
      }

      return false;
    }

    var quantityVar = Var.player(varbitChanged, VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
    if (quantityVar.wasChanged()) {
      var newQuantity = quantityVar.getValue(client);
      if (newQuantity != ammo.getQuantity()) {
        ammo.setQuantity(newQuantity);
        return true;
      }

      return false;
    }

    return false;
  }

  @Override
  protected void resetItems() {
    items.clear();
    ammo = new ItemStack(-1, 0, plugin);
    items.add(ammo);
  }
}
