/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.death;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import lombok.Getter;

/**
 * DeathStorage is responsible for tracking death storages that hold the players items (deathpiles,
 * deathbanks).
 */
@Getter
public class DeathStorage extends ItemStorage<DeathStorageType> {

  protected DeathStorage(DeathStorageType type, BanklessBankPlugin plugin) {
    super(type, plugin);
  }
}
