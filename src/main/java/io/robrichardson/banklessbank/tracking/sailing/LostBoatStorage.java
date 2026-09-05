/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: Swing panel, popup menu, info box and world map point code removed.
 */
package io.robrichardson.banklessbank.tracking.sailing;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.SaveFieldFormatter;
import io.robrichardson.banklessbank.tracking.SaveFieldLoader;
import java.util.ArrayList;
import java.util.UUID;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * LostBoatStorage is responsible for tracking what the player lost when their boat capsized.
 */
@Getter
public class LostBoatStorage extends BoatStorage {

  protected UUID uuid = UUID.randomUUID();
  protected WorldPoint worldPoint;

  protected LostBoatStorage(BanklessBankPlugin plugin) {
    super(SailingStorageType.LOST_BOAT, plugin);
  }

  protected LostBoatStorage(
      BanklessBankPlugin plugin, ActiveBoatStorage boatStorage, WorldPoint worldPoint) {
    super(SailingStorageType.LOST_BOAT, plugin);

    this.worldPoint = worldPoint;
    items.addAll(boatStorage.getItems());
    name2Id = boatStorage.getName2Id();
    name3Id = boatStorage.getName3Id();
    updateLastUpdated();
  }

  static LostBoatStorage load(
      BanklessBankPlugin plugin,
      SailingStorageManager sailingStorageManager,
      String profileKey,
      String uuid) {
    var lostBoat = new LostBoatStorage(plugin);

    lostBoat.uuid = UUID.fromString(uuid);
    lostBoat.load(
        sailingStorageManager.getConfigManager(), sailingStorageManager.getConfigKey(), profileKey);

    return lostBoat;
  }

  @Override
  public String getLocationText() {
    if (worldPoint == null) {
      return "";
    }

    return worldPoint.getX() + ", " + worldPoint.getY();
  }

  @Override
  protected String getConfigKey(String managerConfigKey) {
    return super.getConfigKey(managerConfigKey) + "." + uuid;
  }

  @Override
  protected ArrayList<String> getSaveValues() {
    ArrayList<String> saveValues = super.getSaveValues();

    saveValues.add(SaveFieldFormatter.format(worldPoint));

    return saveValues;
  }

  @Override
  protected void loadValues(ArrayList<String> values) {
    super.loadValues(values);

    worldPoint = SaveFieldLoader.loadWorldPoint(values, worldPoint);
  }

  @Override
  public boolean isWithdrawable() {
    return false;
  }
}
