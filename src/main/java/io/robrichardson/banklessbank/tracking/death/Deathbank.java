/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: Swing panel, popup menu, info box and world map point code removed.
 */
package io.robrichardson.banklessbank.tracking.death;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.SaveFieldFormatter;
import io.robrichardson.banklessbank.tracking.SaveFieldLoader;
import java.util.ArrayList;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** Deathbank is responsible for tracking the player's deathbanked items. */
@Slf4j
@Getter
@Setter
public class Deathbank extends DeathStorage {

  protected UUID uuid = UUID.randomUUID();
  private boolean locked = false;
  @Getter private long lostAt = -1L;
  private DeathbankType deathbankType;
  private DeathStorageManager deathStorageManager;

  Deathbank(
      DeathbankType deathbankType,
      BanklessBankPlugin plugin,
      DeathStorageManager deathStorageManager) {
    super(DeathStorageType.DEATHBANK, plugin);

    this.deathbankType = deathbankType;
    this.deathStorageManager = deathStorageManager;
  }

  static Deathbank load(BanklessBankPlugin plugin, DeathStorageManager deathStorageManager,
      String profileKey, String uuid) {
    Deathbank deathbank = new Deathbank(
        DeathbankType.UNKNOWN,
        plugin,
        deathStorageManager
    );

    deathbank.uuid = UUID.fromString(uuid);
    deathbank.load(deathStorageManager.getConfigManager(), deathStorageManager.getConfigKey(),
        profileKey);

    if (deathbank.getItems().isEmpty()) {
      deathbank.deleteData(deathStorageManager);
      return null;
    }

    return deathbank;
  }

  @Override
  protected String getConfigKey(String managerConfigKey) {
    return super.getConfigKey(managerConfigKey) + "." + uuid;
  }

  @Override
  protected ArrayList<String> getSaveValues() {
    ArrayList<String> saveValues = super.getSaveValues();

    saveValues.add(SaveFieldFormatter.format(uuid));
    saveValues.add(SaveFieldFormatter.format(locked));
    saveValues.add(SaveFieldFormatter.format(lostAt));
    saveValues.add(SaveFieldFormatter.format(deathbankType));

    return saveValues;
  }

  @Override
  protected void loadValues(ArrayList<String> values) {
    super.loadValues(values);

    uuid = SaveFieldLoader.loadUUID(values, uuid);
    locked = SaveFieldLoader.loadBoolean(values, locked);
    lostAt = SaveFieldLoader.loadLong(values, lostAt);
    deathbankType = SaveFieldLoader.loadDeathbankType(values, deathbankType);
  }

  /** Removes this deathbank from tracking and deletes its saved data. */
  public void delete() {
    if (this == deathStorageManager.getDeathbank()) {
      deathStorageManager.clearDeathbank(false);
    } else {
      deathStorageManager.getStorages().remove(this);
      deleteData(deathStorageManager);
    }
    plugin.storagesChanged();
  }

  @Override
  public void reset() {
    // deathbanks get removed instead of reset
  }

  /**
   * Checks if the deathbank has not been lost.
   *
   * @return true if lostAt == -1
   */
  public boolean isActive() {
    return lostAt == -1L;
  }

  @Override
  public boolean isWithdrawable() {
    // If the items were lost, then they can't be withdrawn
    return super.isWithdrawable() && isActive();
  }
}
