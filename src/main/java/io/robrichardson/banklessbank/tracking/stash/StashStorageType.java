/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.stash;

import io.robrichardson.banklessbank.tracking.StorageType;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** StashStorageType is used to identify StashStorages. */
@RequiredArgsConstructor
@Getter
public enum StashStorageType implements StorageType {
  STASH("Stash", -1, false, "stash", true);

  private final String name;
  private final int itemContainerId;
  // Whether the storage can be updated with no action required by the player
  private final boolean automatic;
  private final String configKey;
  private final boolean membersOnly;
  private final List<Integer> accountTypeBlacklist = null;
}
