/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemContainerWatcher;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.ItemStackUtils;
import java.util.List;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;

/** Bank is responsible for tracking what the player has in their bank. */
public class Bank extends WorldStorage {

  private boolean updatedExternally;

  protected Bank(BanklessBankPlugin plugin) {
    super(WorldStorageType.BANK, plugin);
  }

  public void addItems(List<ItemStack> itemsToAdd) {
    for (ItemStack itemStack : itemsToAdd) {
      ItemStackUtils.addItemStack(items, itemStack, true);
    }

    updatedExternally = true;
  }

  @Override
  public boolean onGameTick() {
    var updated = super.onGameTick() || updatedExternally;

    var client = plugin.getClient();
    var depositBoxWidget = client.getWidget(InterfaceID.BankDepositbox.FRAME);
    if (depositBoxWidget != null && !depositBoxWidget.isHidden()) {
      PotionStorage potionStorage = null;
      if (client.getVarbitValue(VarbitID.BANK_DEPOSITPOTION) == 1) {
        potionStorage = plugin.getWorldStorageManager().getPotionStorage();
      }

      for (ItemStack itemStack :
          ItemContainerWatcher.getInventoryWatcher().getItemsRemovedLastTick()) {
        // Check if the item will be sent to PotionStorage
        if (potionStorage != null
            && potionStorage.getDoseMap().get(itemStack.getCanonicalId()) != null) {
          continue;
        }

        ItemStackUtils.addItemStack(items, itemStack, true);

        updated = true;
      }
    }

    if (updated) {
      updateLastUpdated();
    }

    return updated;
  }
}
