/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: Swing panel, popup menu, info box and world map point code removed.
 */
package io.robrichardson.banklessbank.tracking.death;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.DurationFormatter;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.Region;
import io.robrichardson.banklessbank.tracking.SaveFieldFormatter;
import io.robrichardson.banklessbank.tracking.SaveFieldLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public abstract class ExpiringDeathStorage extends DeathStorage {

  @Getter protected final DeathStorageManager deathStorageManager;
  @Getter protected WorldPoint worldPoint;
  @Getter protected WorldArea worldArea;
  @Getter @Setter protected int expiryTime;
  protected long expiredAt = -1L;
  protected UUID uuid = UUID.randomUUID();
  // when useAccountPlayTime is true, expiryTime is the account played minutes that the
  // deathpile will expire at.
  // when useAccountPlayTime is false, expiryTime is the amount of ticks left until
  // the deathpile expires, ticking down only while the player is logged in.
  //
  // This is unused for graves, because grave timers are 100% accurate and provided by jagex
  @Getter private boolean useAccountPlayTime;

  ExpiringDeathStorage(
      BanklessBankPlugin plugin,
      boolean useAccountPlayTime,
      WorldArea worldArea,
      DeathStorageManager deathStorageManager,
      List<ItemStack> deathItems,
      DeathStorageType storageType) {
    super(storageType, plugin);
    this.useAccountPlayTime = useAccountPlayTime;
    if (worldArea != null && worldArea.getWidth() == 1 && worldArea.getHeight() == 1) {
      this.worldPoint = worldArea.toWorldPoint();
    } else {
      this.worldArea = worldArea;
    }
    this.deathStorageManager = deathStorageManager;
    this.items.addAll(deathItems);

    int duration = getTotalLifeInMinutes();
    if (this instanceof Deathpile) {
      duration -= plugin.getConfig().deathpileContingencyMinutes();
    } else {
      duration -= 1;
    }
    this.expiryTime =
        useAccountPlayTime ? deathStorageManager.getPlayedMinutes() + duration : duration * 100;
  }

  @Override
  protected String getConfigKey(String managerConfigKey) {
    return super.getConfigKey(managerConfigKey) + "." + uuid;
  }

  @Override
  protected ArrayList<String> getSaveValues() {
    ArrayList<String> saveValues = super.getSaveValues();

    saveValues.add(SaveFieldFormatter.format(uuid));
    if (worldPoint != null) {
      saveValues.add(SaveFieldFormatter.format(worldPoint));
    } else {
      saveValues.add(SaveFieldFormatter.format(worldArea));
    }
    saveValues.add(SaveFieldFormatter.format(useAccountPlayTime));
    saveValues.add(SaveFieldFormatter.format(expiryTime));
    saveValues.add(SaveFieldFormatter.format(expiredAt));

    return saveValues;
  }

  @Override
  protected void loadValues(ArrayList<String> values) {
    super.loadValues(values);

    uuid = SaveFieldLoader.loadUUID(values, uuid);
    var point = SaveFieldLoader.loadWorldPoint(values, null);
    if (point != null) {
      worldPoint = point;
    } else {
      worldArea = SaveFieldLoader.loadWorldArea(values, null);
    }
    useAccountPlayTime = SaveFieldLoader.loadBoolean(values, useAccountPlayTime);
    expiryTime = SaveFieldLoader.loadInt(values, expiryTime);
    expiredAt = SaveFieldLoader.loadLong(values, expiredAt);
  }

  public String getRegionName() {
    var region = getRegion();
    if (region == null) {
      return "Unknown";
    }

    return region.getName();
  }

  public Region getRegion() {
    if (worldPoint == null && worldArea == null) {
      return null;
    }

    return Region.get((worldPoint != null ? worldPoint : worldArea.toWorldPoint()).getRegionID());
  }

  protected void setWorldPoint(WorldPoint worldPoint) {
    worldArea = null;
    this.worldPoint = worldPoint;
  }

  /** Moves this storage to the player's current tile. */
  public void moveToCurrentTile() {
    plugin.getClientThread().invokeLater(() -> {
      var player = plugin.getClient().getLocalPlayer();
      if (player == null || player.getWorldLocation() == null) {
        return;
      }

      setWorldPoint(player.getWorldLocation());
      plugin.storagesChanged();
    });
  }

  @Override
  public boolean onGameTick() {
    if (expiredAt != -1L) {
      return false;
    }

    if (!useAccountPlayTime) {
      expiryTime--;
      if (expiryTime <= 0) {
        expiredAt = System.currentTimeMillis();
      }

      return true;
    }

    if (deathStorageManager.getStartPlayedMinutes() > 0
        && deathStorageManager.getPlayedMinutes() >= expiryTime) {
      expiredAt = System.currentTimeMillis();

      return true;
    }

    return false;
  }

  @Override
  public void reset() {
    // these get removed instead of reset
  }

  public String getExpireText() {
    if (expiredAt != -1L) {
      return "Expired " + DurationFormatter.format(System.currentTimeMillis() - expiredAt) + " ago";
    }

    if (useAccountPlayTime && deathStorageManager.getStartPlayedMinutes() <= 0) {
      return "Waiting for play time";
    }

    return "Expires in " + DurationFormatter.format(getExpiryMs() - System.currentTimeMillis());
  }

  public abstract int getTotalLifeInMinutes();

  /**
   * Returns a unix timestamp of the expiry.
   *
   * @return Unix timestamp of the expiry
   */
  public long getExpiryMs() {
    if (expiredAt != -1L) {
      return expiredAt;
    }

    if (!useAccountPlayTime) {
      return System.currentTimeMillis() + (expiryTime * 600L);
    }

    // We don't know the player's play time yet, so assume the storage is fresh for sorting purposes
    if (deathStorageManager.getStartPlayedMinutes() <= 0) {
      return System.currentTimeMillis() + getTotalLifeInMinutes() * 60_000L;
    }

    int minutesLeft = expiryTime - deathStorageManager.getPlayedMinutes();

    return System.currentTimeMillis()
        + (minutesLeft * 60000L)
        - ((System.currentTimeMillis() - deathStorageManager.startMs) % 60000);
  }

  public boolean hasExpired() {
    return getExpiryMs() < System.currentTimeMillis();
  }

  @Override
  public boolean isWithdrawable() {
    return super.isWithdrawable() && !hasExpired();
  }
}
