/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking;

import java.util.List;

/** StorageType is used to identify Storages. */
public interface StorageType {

  String getName();

  int getItemContainerId();

  boolean isAutomatic();

  String getConfigKey();

  boolean isMembersOnly();

  List<Integer> getAccountTypeBlacklist();
}
