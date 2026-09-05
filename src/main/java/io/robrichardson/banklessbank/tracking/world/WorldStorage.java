/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStorage;
import lombok.Getter;

/**
 * WorldStorage is responsible for tracking storages in the world that hold the players items
 * (leprechaun, fossil storage, stash units, etc).
 */
@Getter
public class WorldStorage extends ItemStorage<WorldStorageType> {

  protected WorldStorage(WorldStorageType type, BanklessBankPlugin plugin) {
    super(type, plugin);
  }
}
