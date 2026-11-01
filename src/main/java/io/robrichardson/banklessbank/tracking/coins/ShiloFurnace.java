/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.coins;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import net.runelite.api.widgets.Widget;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * ShiloFurnace is responsible for tracking how many coins the player has stored in the shilo
 * village furnace coffer.
 */
public class ShiloFurnace extends CoinsStorage {

  ShiloFurnace(BanklessBankPlugin plugin) {
    super(CoinsStorageType.SHILO_FURNACE, plugin);
  }

  @Override
  public boolean onGameTick() {
    Widget widget = plugin.getClient().getWidget(219, 1);
    if (widget == null) {
      return false;
    }

    Widget textWidget = widget.getChild(0);
    if (textWidget == null) {
      return false;
    }
    if (!textWidget.getText().startsWith("Furnace coffer: ")) {
      return false;
    }

    int parsedCoins = NumberUtils.toInt(textWidget.getText().replaceAll("\\D+", ""), 0);
    this.getCoinStack().setQuantity(parsedCoins);
    updateLastUpdated();
    return true;
  }
}
