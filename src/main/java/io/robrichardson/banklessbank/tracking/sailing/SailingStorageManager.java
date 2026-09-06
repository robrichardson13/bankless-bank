/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: Swing panel, popup menu, info box and world map point code removed.
 */
package io.robrichardson.banklessbank.tracking.sailing;

import com.google.inject.Inject;
import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.StorageManager;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/** SailingStorageManager is responsible for managing all SailingStorages. */
@Slf4j
public class SailingStorageManager extends StorageManager<SailingStorageType, SailingStorage> {

  @Inject
  private SailingStorageManager(BanklessBankPlugin plugin) {
    super(plugin);

    storages.add(new ActiveBoatStorage(SailingStorageType.BOAT_1, plugin));
    storages.add(new ActiveBoatStorage(SailingStorageType.BOAT_2, plugin));
    storages.add(new ActiveBoatStorage(SailingStorageType.BOAT_3, plugin));
    storages.add(new ActiveBoatStorage(SailingStorageType.BOAT_4, plugin));
    storages.add(new ActiveBoatStorage(SailingStorageType.BOAT_5, plugin));
  }

  @Override
  public void load(String profileKey) {
    super.load(profileKey);

    if (!enabled) {
      return;
    }

    loadLostBoats(profileKey);
  }

  private void loadLostBoats(String profileKey) {
    for (var configurationKey :
        configManager.getRSProfileConfigurationKeys(
            BanklessBankConfig.CONFIG_GROUP,
            profileKey,
            getConfigKey() + "." + SailingStorageType.LOST_BOAT.getConfigKey() + ".")) {
      var lostBoat =
          LostBoatStorage.load(plugin, this, profileKey, configurationKey.split("\\.")[2]);
      storages.add(lostBoat);
    }
  }

  @Override
  public String getConfigKey() {
    return "sailing";
  }

  public void deleteStorage(LostBoatStorage lostBoatStorage) {
    storages.remove(lostBoatStorage);
    lostBoatStorage.deleteData(this);
    plugin.storagesChanged();
  }

  public void deleteLostBoats() {
    var iterator = storages.iterator();
    while (iterator.hasNext()) {
      var storage = iterator.next();
      if (!(storage instanceof LostBoatStorage)) {
        continue;
      }

      iterator.remove();
      storage.deleteData(this);
    }
    plugin.storagesChanged();
  }

  /**
   * Creates a lost boat storage from an active boat.
   *
   * @param activeBoatStorage The active boat storage that was capsized and needs to be
   *     converted to a lost boat
   */
  public void createLostBoat(ActiveBoatStorage activeBoatStorage) {
    var client = plugin.getClient();
    var lostBoat =
        new LostBoatStorage(
            plugin,
            activeBoatStorage,
            WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()));
    // Add to storages later to avoid CME
    clientThread.invokeLater(
        () -> {
          storages.add(lostBoat);
          updateStorages(Collections.singletonList(lostBoat));
        });
  }
}
