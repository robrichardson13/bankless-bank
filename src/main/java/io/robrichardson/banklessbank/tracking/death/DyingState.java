/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.death;

public enum DyingState {
  NOT_DYING,
  TICK_1,
  TICK_2,
  TICK_3,
  RECORDING_DATA,
  WAITING_FOR_RESPAWN
}
