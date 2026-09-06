/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import com.google.inject.Inject;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.StorageManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** WorldStorageManager is responsible for managing all WorldStorages. */
@Slf4j
public class WorldStorageManager extends StorageManager<WorldStorageType, WorldStorage> {

  @Getter private final Leprechaun leprechaun;
  @Getter private final PotionStorage potionStorage;
  @Getter private final Bank bank;

  @Inject
  private WorldStorageManager(BanklessBankPlugin plugin) {
    super(plugin);

    leprechaun = new Leprechaun(plugin);
    storages.add(leprechaun);
    bank = new Bank(plugin);
    storages.add(bank);
    storages.add(new WorldStorage(WorldStorageType.GROUP_STORAGE, plugin));
    storages.add(new WorldStorage(WorldStorageType.SEED_VAULT, plugin));
    storages.add(new BlastFurnace(plugin));
    storages.add(new LogStorage(plugin));
    storages.add(new FossilStorage(plugin));
    storages.add(new VyreWell(plugin));
    storages.add(new Annette(plugin));
    storages.add(new ElnockInquisitor(plugin));
    storages.add(new PickaxeStatue(plugin));
    storages.add(new Nulodion(plugin));
    storages.add(new ForestryShop(plugin));
    storages.add(new Sandstorm(plugin));
    storages.add(new Eyatlalli(plugin));
    potionStorage = new PotionStorage(plugin);
    storages.add(potionStorage);
    storages.add(new CompostBins(plugin));
    storages.add(new Nest(plugin));
  }

  @Override
  public String getConfigKey() {
    return "world";
  }

}
