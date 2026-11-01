/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: only the coffers are tracked. BANK is dropped (a UIM has none), and
 * INVENTORY and LOOTING_BAG are dropped because the carryable manager already tracks those
 * containers' coins as items; keeping them would count the same coins twice.
 */
package io.robrichardson.banklessbank.tracking.coins;

import com.google.inject.Inject;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.StorageManager;
import java.util.EnumSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** CoinsStorageManager is responsible for managing all CoinsStorages. */
@Slf4j
public class CoinsStorageManager extends StorageManager<CoinsStorageType, CoinsStorage> {

  /** Upstream types we never track; their coins already live in another manager. */
  public static final Set<CoinsStorageType> UNTRACKED_TYPES =
      EnumSet.of(CoinsStorageType.BANK, CoinsStorageType.INVENTORY, CoinsStorageType.LOOTING_BAG);

  @Inject
  CoinsStorageManager(BanklessBankPlugin plugin) {
    super(plugin);

    for (CoinsStorageType type : CoinsStorageType.values()) {
      if (UNTRACKED_TYPES.contains(type)
          || type == CoinsStorageType.SERVANT_MONEYBAG
          || type == CoinsStorageType.SHILO_FURNACE
          || type == CoinsStorageType.GRAND_EXCHANGE
          || type == CoinsStorageType.BOUNTY_HUNTER
          || type == CoinsStorageType.SCAR_ESSENCE_MINE) {
        continue;
      }

      storages.add(new CoinsStorage(type, plugin));
    }

    storages.add(new ServantsMoneybag(plugin));
    storages.add(new ShiloFurnace(plugin));
    storages.add(new GrandExchange(plugin));
    storages.add(new BountyHunterCoffer(plugin));
    storages.add(new ScarEssenceMine(plugin));
  }

  @Override
  public String getConfigKey() {
    return "coins";
  }
}
