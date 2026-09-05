/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: Swing panel, popup menu, info box and world map point code removed.
 */
package io.robrichardson.banklessbank.tracking.death;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/**
 * DeathItems mirrors what the player would lose on death. It is never shown in the bank view; it
 * exists so the death manager can build deathpiles/graves from it and for developer testing.
 */
@Slf4j
public class DeathItems extends DeathStorage {

  private final DeathStorageManager deathStorageManager;

  protected DeathItems(BanklessBankPlugin plugin, DeathStorageManager deathStorageManager) {
    super(DeathStorageType.DEATH_ITEMS, plugin);

    this.deathStorageManager = deathStorageManager;
  }

  public void createDebugDeathpile(WorldPoint worldPoint) {
    var deathpile = deathStorageManager.createDeathpile(
        RemoteDeathpileAreas.getPileArea(plugin.getClient(), worldPoint), items);
    deathStorageManager.updateStorages(Collections.singletonList(deathpile));
  }

  public void createDebugGrave(WorldPoint worldPoint) {
    var grave = deathStorageManager.createGrave(
        RemoteDeathpileAreas.getPileArea(plugin.getClient(), worldPoint), items);
    deathStorageManager.updateStorages(Collections.singletonList(grave));
  }

  public void createDebugDeathbank() {
    if (deathStorageManager.getDeathbank() != null) {
      deathStorageManager.getDeathbank().setLostAt(System.currentTimeMillis());
    }

    deathStorageManager.createMysteryDeathbank(DeathbankType.UNKNOWN);
    var deathbank = deathStorageManager.getDeathbank();
    deathbank.getItems().clear();
    deathbank.getItems().addAll(items);
  }

  @Override
  public void softUpdate() {
    items.clear();
    items.addAll(deathStorageManager.getDeathItems());
  }

  @Override
  public void save(ConfigManager configManager, String profileKey,
      String managerConfigKey) {
    // No saving
  }

  @Override
  public void load(ConfigManager configManager, String managerConfigKey, String profileKey) {
    // No loading
  }

  @Override
  public boolean isWithdrawable() {
    return false;
  }
}
