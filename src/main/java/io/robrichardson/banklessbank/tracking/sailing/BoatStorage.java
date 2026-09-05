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
import lombok.Getter;
import net.runelite.api.gameval.DBTableID;
import net.runelite.client.config.ConfigManager;

/** SailingStorage is the base class for sailing storages that are specifically boats. */
@Getter
public abstract class BoatStorage extends SailingStorage {

  protected int name2Id;
  protected int name3Id;

  protected BoatStorage(SailingStorageType type, BanklessBankPlugin plugin) {
    super(type, plugin);
  }

  /** The boat's name, resolved on the client thread and cached. Falls back to the type name. */
  @Getter private String boatName;

  protected void updateName() {
    if (name2Id == 0 || name3Id == 0) {
      boatName = null;
      return;
    }

    var client = plugin.getClient();
    plugin
        .getClientThread()
        .invoke(
            () -> {
              var name2 =
                  client
                      .getDBTableField(
                          DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_DESCRIPTOR_OPTIONS,
                          1,
                          0)[name2Id - 1];
              var name3 =
                  client
                      .getDBTableField(
                          DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_NOUN_OPTIONS,
                          1,
                          0)[name3Id - 1];
              boatName = name2 + " " + name3;
            });
  }

  /** Human readable location (port name, "Capsized", co-ordinates) for the view. */
  public abstract String getLocationText();

  @Override
  protected ArrayList<String> getSaveValues() {
    ArrayList<String> saveValues = super.getSaveValues();

    saveValues.add(SaveFieldFormatter.format(name2Id));
    saveValues.add(SaveFieldFormatter.format(name3Id));

    return saveValues;
  }

  @Override
  protected void loadValues(ArrayList<String> values) {
    super.loadValues(values);

    name2Id = SaveFieldLoader.loadInt(values, name2Id);
    name3Id = SaveFieldLoader.loadInt(values, name3Id);
  }

  @Override
  public void load(ConfigManager configManager, String managerConfigKey, String profileKey) {
    super.load(configManager, managerConfigKey, profileKey);

    updateName();
  }
}
