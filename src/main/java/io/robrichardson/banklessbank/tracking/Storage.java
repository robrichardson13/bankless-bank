/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: removed the Swing storage panel, popup menus and item-count tooltip
 * config; UI refresh is signalled through BanklessBankPlugin#storagesChanged() instead.
 */
package io.robrichardson.banklessbank.tracking;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;

/** Storage serves as a base class for all trackable data in the plugin. */
@Slf4j
@Getter
public abstract class Storage<T extends StorageType> {

  protected final BanklessBankPlugin plugin;
  protected final T type;
  protected boolean enabled = true;
  @Nullable protected String lastSaveString;
  @Setter protected long lastUpdated = -1L;

  protected Storage(T type, BanklessBankPlugin plugin) {
    this.type = type;
    this.plugin = plugin;
  }

  /**
   * Deletes the data for this storage from the config and resets the storage.
   *
   * @param storageManager the storage manager that relates to this storage
   */
  public void deleteData(StorageManager<?, ?> storageManager) {
    String profileKey = storageManager.getConfigManager().getRSProfileKey();

    storageManager.getConfigManager().unsetConfiguration(
        BanklessBankConfig.CONFIG_GROUP,
        profileKey,
        getConfigKey(storageManager.getConfigKey())
    );
    reset();
  }

  public long getTotalValue() {
    return 0;
  }

  public boolean onGameTick() {
    return false;
  }

  public boolean onGameObjectSpawned(GameObjectSpawned gameObjectSpawned) {
    return false;
  }

  @SuppressWarnings("java:S1172") // the parameter is used in child classes
  public boolean onWidgetLoaded(WidgetLoaded widgetLoaded) {
    return false;
  }

  @SuppressWarnings("java:S1172") // the parameter is used in child classes
  public void onWidgetClosed(WidgetClosed widgetClosed) {
  }

  @SuppressWarnings("java:S1172") // the parameter is used in child classes
  public boolean onChatMessage(ChatMessage chatMessage) {
    return false;
  }

  @SuppressWarnings("java:S1172") // the parameter is used in child classes
  public boolean onVarbitChanged(VarbitChanged varbitChanged) {
    return false;
  }

  @SuppressWarnings({"java:S1172", "unused"}) // the parameter is used in child classes
  public boolean onMenuOptionClicked(MenuOptionClicked menuOption) {
    return false;
  }

  /**
   * Can the items in this storage be withdrawn?
   *
   * <p>Should be overridden by subclasses. Should be false for things like minigame points,
   * expired deathbanks, or deposit-only storages such as balloon log storage.
   *
   * @return true if the items are withdrawable, otherwise false
   */
  public boolean isWithdrawable() {
    return enabled;
  }

  /**
   * tells the Storage that the onItemContainerChanged event was called and that it should update
   * the player's trackable data.
   *
   * @param itemContainerChanged the ItemContainerChanged event
   * @return whether the data changed
   */
  public boolean onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
    return false;
  }

  /**
   * resets the Storage back to it's initial state, useful for when the player logs out, for
   * example.
   */
  public void reset() {
    lastUpdated = -1;
    lastSaveString = null;
    enable();
  }

  /** saves the Storage data to the player's RuneLite RS profile config. */
  public void save(ConfigManager configManager, String profileKey, String managerConfigKey) {
    if (!type.isAutomatic() && lastUpdated == -1L) {
      return;
    }

    String saveString = getSaveString();
    if (Objects.equals(lastSaveString, saveString)) {
      return;
    }

    this.lastSaveString = saveString;
    configManager.setConfiguration(BanklessBankConfig.CONFIG_GROUP, profileKey,
        getConfigKey(managerConfigKey), saveString);
  }

  public String getSaveString() {
    return String.join(";", getSaveValues());
  }

  protected ArrayList<String> getSaveValues() {
    ArrayList<String> saveValues = new ArrayList<>();

    if (!type.isAutomatic()) {
      saveValues.add(SaveFieldFormatter.format(lastUpdated));
    }

    return saveValues;
  }

  /** loads the Storage data from the specified RuneLite RS profile config. */
  public void load(ConfigManager configManager, String managerConfigKey, String profileKey) {
    String data =
        configManager.getConfiguration(
            BanklessBankConfig.CONFIG_GROUP,
            profileKey,
            getConfigKey(managerConfigKey),
            String.class);

    if (data == null) {
      return;
    }

    this.lastSaveString = data;
    loadValues(new ArrayList<>(Arrays.asList(data.split(";"))));
  }

  protected void loadValues(ArrayList<String> values) {
    if (!type.isAutomatic()) {
      lastUpdated = SaveFieldLoader.loadLong(values, lastUpdated);
    }
  }

  protected String getConfigKey(String managerConfigKey) {
    return managerConfigKey + "." + type.getConfigKey();
  }

  /** Whether this storage has ever been populated (saved data or a live update). */
  public boolean hasData() {
    return lastUpdated != -1L || lastSaveString != null;
  }

  /** Disables the storage. */
  public void disable() {
    enabled = false;
  }

  /** Disables the storage if it is not available to this account (F2P, account type). */
  public void disable(boolean isMember, int accountType) {
    if ((type.isMembersOnly() && !isMember)
        || (type.getAccountTypeBlacklist() != null
        && type.getAccountTypeBlacklist().contains(accountType))) {
      disable();
    }
  }

  /** Enables the storage. */
  public void enable() {
    enabled = true;
  }

  public String getName() {
    return type.getName();
  }

  /**
   * Hook called once per game tick for time-based bookkeeping that is not driven by game events
   * (expiry text, derived item lists). Upstream used this to refresh the storage panel.
   */
  public void softUpdate() {
  }

  public List<ItemStack> getItems() {
    return new ArrayList<>();
  }

  public long getItemCount(int canonicalId) {
    return getItems().stream().filter(stack -> stack.getCanonicalId() == canonicalId)
        .mapToLong(ItemStack::getQuantity).sum();
  }

  protected void updateLastUpdated() {
    lastUpdated = System.currentTimeMillis();
  }
}
