/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.death;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;

@RequiredArgsConstructor
@Getter
class SuspendedGroundItem {
  private final int id;
  private final WorldPoint worldPoint;
  @Setter private int ticksLeft;
}
