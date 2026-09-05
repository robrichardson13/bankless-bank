/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.death;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** DeathpileExpiryWarningType is used to decide how expiring deathpiles will be shown. */
@Getter
@RequiredArgsConstructor
public enum DeathpileExpiryWarningType {
  OFF("Off"),
  TEXT("Screen text"),
  INFOBOX("Infobox flashing"),
  BOTH("BOTH");

  private final String name;

  @Override
  public String toString() {
    return name;
  }
}
