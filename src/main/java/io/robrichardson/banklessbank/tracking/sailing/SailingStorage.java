/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.sailing;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import lombok.Getter;

/** SailingStorage is the base class for all sailing storages. */
@Getter
public abstract class SailingStorage extends ItemStorage<SailingStorageType> {

  protected SailingStorage(SailingStorageType type, BanklessBankPlugin plugin) {
    super(type, plugin);
  }
}
