/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.coins;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import net.runelite.api.widgets.Widget;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * ServantsMoneybag is responsible for tracking how many coins the player has stored in their
 * servant's moneybag.
 */
public class ServantsMoneybag extends CoinsStorage {

  ServantsMoneybag(BanklessBankPlugin plugin) {
    super(CoinsStorageType.SERVANT_MONEYBAG, plugin);
  }

  @Override
  public boolean onGameTick() {
    Widget widget = plugin.getClient().getWidget(193, 2);
    if (widget == null) {
      return false;
    }
    if (!widget.getText().startsWith("The money bag ")) {
      return false;
    }

    int parsedCoins = NumberUtils.toInt(widget.getText().replaceAll("\\D+", ""), 0);
    this.getCoinStack().setQuantity(parsedCoins);
    updateLastUpdated();
    return true;
  }
}
