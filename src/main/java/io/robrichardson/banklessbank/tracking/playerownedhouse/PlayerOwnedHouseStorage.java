/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.playerownedhouse;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.events.ItemContainerChanged;

/** PlayerOwnedHouseStorage is responsible for tracking storages in the player's house. */
@Getter
public class PlayerOwnedHouseStorage extends ItemStorage<PlayerOwnedHouseStorageType> {

  protected PlayerOwnedHouseStorage(
      PlayerOwnedHouseStorageType type, BanklessBankPlugin plugin) {
    super(type, plugin);
  }

  @Override
  public boolean onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
    var containerId = itemContainerChanged.getContainerId();
    if (containerId > 0x8000) {
      containerId -= 0x8000;
    }

    if (containerId != type.getItemContainerId()) {
      return false;
    }

    updateLastUpdated();
    items.clear();
    for (Item item : itemContainerChanged.getItemContainer().getItems()) {
      if (type.getStorableItemIds() == null || type.getStorableItemIds().contains(item.getId())) {
        items.add(new ItemStack(item.getId(), item.getQuantity(), plugin));
      }
    }

    return true;
  }
}
