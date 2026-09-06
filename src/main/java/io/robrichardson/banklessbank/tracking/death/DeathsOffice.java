/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.death;

import io.robrichardson.banklessbank.BanklessBankPlugin;

/** DeathsOffice shows the user what items they have stored in death's office. */
public class DeathsOffice extends DeathStorage {

  protected DeathsOffice(BanklessBankPlugin plugin) {
    super(DeathStorageType.DEATHS_OFFICE, plugin);
  }
}
