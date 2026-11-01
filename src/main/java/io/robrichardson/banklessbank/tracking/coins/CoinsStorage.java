/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.coins;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import io.robrichardson.banklessbank.tracking.Var;
import java.util.Optional;
import lombok.Getter;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;

/**
 * CoinsStorage is responsible for tracking storages that hold the players coins (coffers,
 * inventory, etc).
 */
@Getter
public class CoinsStorage extends ItemStorage<CoinsStorageType> {

  protected final ItemStack coinStack = new ItemStack(995, "Coins", 0, 1, 0, true);

  protected CoinsStorage(CoinsStorageType type, BanklessBankPlugin plugin) {
    super(type, plugin);

    hasStaticItems = true;

    items.add(coinStack);
  }

  @Override
  public boolean onGameTick() {
    if (itemContainerWatcher != null && itemContainerWatcher.wasJustUpdated()) {
      Optional<ItemStack> coinsItem =
          itemContainerWatcher.getItems().stream().filter(i -> i.getId() == 995).findFirst();
      coinStack.setQuantity(coinsItem.map(ItemStack::getQuantity).orElse(0L));

      updateLastUpdated();

      return true;
    }

    return false;
  }

  @Override
  public boolean onVarbitChanged(VarbitChanged varbitChanged) {
    if (type.getVarbitId() == -1) {
      return false;
    }

    var coinsVar = Var.bit(varbitChanged, type.getVarbitId());
    if (!coinsVar.wasChanged()) {
      return false;
    }

    var coins = coinsVar.getValue(plugin.getClient()) * type.getMultiplier();
    if (coinStack.getQuantity() == coins) {
      return false;
    }

    coinStack.setQuantity(coins);
    return true;
  }

  @Override
  public boolean onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
    if (itemContainerWatcher != null
        || type.getItemContainerId() != itemContainerChanged.getContainerId()) {
      return false;
    }

    ItemContainer itemContainer = itemContainerChanged.getItemContainer();
    if (itemContainer == null) {
      return false;
    }

    updateLastUpdated();
    int coins = itemContainer.count(995);
    if (coinStack.getQuantity() == coins) {
      return !this.getType().isAutomatic();
    }

    coinStack.setQuantity(coins);
    return true;
  }
}
