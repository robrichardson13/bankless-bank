/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.carryable;

import com.google.inject.Inject;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.StorageManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** CarryableStorageManager is responsible for managing all CarryableStorages. */
@Slf4j
public class CarryableStorageManager
    extends StorageManager<CarryableStorageType, CarryableStorage> {

  @Getter private final BottomlessBucket bottomlessBucket;

  @Inject
  private CarryableStorageManager(BanklessBankPlugin plugin) {
    super(plugin);

    bottomlessBucket = new BottomlessBucket(plugin);

    storages.add(new CarryableStorage(CarryableStorageType.EQUIPMENT, plugin));
    storages.add(new CarryableStorage(CarryableStorageType.INVENTORY, plugin));
    storages.add(new LootingBag(plugin));
    storages.add(new SeedBox(plugin));
    storages.add(new RunePouch(plugin));
    storages.add(bottomlessBucket);
    storages.add(new PlankSack(plugin));
    storages.add(new BoltPouch(plugin));
    storages.add(new GnomishFirelighter(plugin));
    storages.add(new MasterScrollBook(plugin));
    storages.add(new HuntsmansKit(plugin));
    storages.add(new ForestryKit(plugin));
    storages.add(new TackleBox(plugin));
    storages.add(new HerbSack(plugin));
    storages.add(new ChuggingBarrel(plugin));
    storages.add(new DizanasQuiver(plugin));
    storages.add(new BowStringSpool(plugin));
  }

  @Override
  public String getConfigKey() {
    return "carryable";
  }

}
