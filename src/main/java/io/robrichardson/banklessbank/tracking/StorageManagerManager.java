/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 *
 * Bankless Bank changes: dropped the coins and minigames managers (not items), the export
 * writers and the Swing panel refreshes. Added logIn() and findStorage() for the bootstrap import.
 */
package io.robrichardson.banklessbank.tracking;

import io.robrichardson.banklessbank.tracking.carryable.CarryableStorageManager;
import io.robrichardson.banklessbank.tracking.death.DeathItems;
import io.robrichardson.banklessbank.tracking.death.DeathStorageManager;
import io.robrichardson.banklessbank.tracking.death.Deathbank;
import io.robrichardson.banklessbank.tracking.death.Deathpile;
import io.robrichardson.banklessbank.tracking.playerownedhouse.PlayerOwnedHouseStorageManager;
import io.robrichardson.banklessbank.tracking.sailing.SailingStorageManager;
import io.robrichardson.banklessbank.tracking.stash.StashStorageManager;
import io.robrichardson.banklessbank.tracking.world.WorldStorageManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.game.ItemManager;

/** The manager of storage managers. */
@Slf4j
@Getter
public class StorageManagerManager {

  private final SailingStorageManager sailingStorageManager;
  private final CarryableStorageManager carryableStorageManager;
  private final DeathStorageManager deathStorageManager;
  private final StashStorageManager stashStorageManager;
  private final PlayerOwnedHouseStorageManager playerOwnedHouseStorageManager;
  private final WorldStorageManager worldStorageManager;
  @Getter private final List<StorageManager<?, ?>> storageManagers;
  private final ItemManager itemManager;

  public StorageManagerManager(
      ItemManager itemManager,
      SailingStorageManager sailingStorageManager,
      CarryableStorageManager carryableStorageManager,
      DeathStorageManager deathStorageManager,
      StashStorageManager stashStorageManager,
      PlayerOwnedHouseStorageManager playerOwnedHouseStorageManager,
      WorldStorageManager worldStorageManager) {
    this.itemManager = itemManager;
    this.sailingStorageManager = sailingStorageManager;
    this.carryableStorageManager = carryableStorageManager;
    this.deathStorageManager = deathStorageManager;
    this.stashStorageManager = stashStorageManager;
    this.playerOwnedHouseStorageManager = playerOwnedHouseStorageManager;
    this.worldStorageManager = worldStorageManager;

    storageManagers =
        Arrays.asList(
            sailingStorageManager,
            carryableStorageManager,
            deathStorageManager,
            stashStorageManager,
            playerOwnedHouseStorageManager,
            worldStorageManager);
  }

  public void reset() {
    storageManagers.forEach(StorageManager::reset);
  }

  /**
   * Disables storages (and whole managers) that this account cannot use. Mirrors the upstream
   * panel's logIn(): the death manager is never disabled as a whole.
   */
  public void logIn(boolean isMember, int accountType) {
    for (StorageManager<?, ?> storageManager : storageManagers) {
      storageManager.getStorages().forEach(storage -> storage.disable(isMember, accountType));

      if (storageManager != deathStorageManager
          && storageManager.getStorages().stream().noneMatch(Storage::isEnabled)) {
        storageManager.disable();
      }
    }
  }

  public void onActorDeath(ActorDeath actorDeath) {
    storageManagers.forEach(storageManager -> storageManager.onActorDeath(actorDeath));
  }

  public void onGameStateChanged(GameStateChanged gameStateChanged) {
    storageManagers.forEach(storageManager -> storageManager.onGameStateChanged(gameStateChanged));
  }

  /**
   * Loads the data for every storage.
   *
   * @param profileKey the profile key to load the data from
   */
  public void load(String profileKey) {
    for (StorageManager<?, ?> storageManager : storageManagers) {
      storageManager.load(profileKey);
    }
  }

  /**
   * Saves the data for every storage.
   *
   * @param profileKey the profile key to save the data under
   */
  public void save(String profileKey) {
    for (StorageManager<?, ?> storageManager : storageManagers) {
      storageManager.save(profileKey);
    }
  }

  public void onGameTick() {
    storageManagers.forEach(StorageManager::onGameTick);
  }

  public void softUpdate() {
    storageManagers.forEach(StorageManager::softUpdate);
  }

  public void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned) {
    storageManagers.forEach(m -> m.onGameObjectSpawned(gameObjectSpawned));
  }

  public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
    storageManagers.forEach(manager -> manager.onWidgetLoaded(widgetLoaded));
  }

  public void onWidgetClosed(WidgetClosed widgetClosed) {
    storageManagers.forEach(manager -> manager.onWidgetClosed(widgetClosed));
  }

  public void onVarbitChanged(VarbitChanged varbitChanged) {
    storageManagers.forEach(storageManager -> storageManager.onVarbitChanged(varbitChanged));
  }

  public void onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
    storageManagers.forEach(manager -> manager.onItemContainerChanged(itemContainerChanged));
  }

  public void onItemDespawned(ItemDespawned itemDespawned) {
    storageManagers.forEach(manager -> manager.onItemDespawned(itemDespawned));
  }

  public void onChatMessage(ChatMessage chatMessage) {
    storageManagers.forEach(manager -> manager.onChatMessage(chatMessage));
  }

  public void onMenuOptionClicked(MenuOptionClicked menuOption) {
    storageManagers.forEach(manager -> manager.onMenuOptionClicked(menuOption));
  }

  /**
   * Gets all the storages from each storage manager.
   *
   * @return a flat map of every storage from every storage manager (except for the "death items"
   *     preview, expired deathpiles and lost deathbanks.)
   */
  @SuppressWarnings("java:S1452")
  public Stream<? extends Storage<? extends Enum<? extends Enum<?>>>> getStorages() {
    return Stream.of(
            getDeathStorageManager().getStorages().stream()
                .filter(s -> !(s instanceof DeathItems))
                .filter(
                    s ->
                        (s instanceof Deathpile && !((Deathpile) s).hasExpired())
                            || (s instanceof Deathbank && ((Deathbank) s).isActive())
                            || !(s instanceof Deathpile || s instanceof Deathbank)),
            getCarryableStorageManager().getStorages().stream(),
            getStashStorageManager().getStorages().stream(),
            getSailingStorageManager().getStorages().stream(),
            getPlayerOwnedHouseStorageManager().getStorages().stream(),
            getWorldStorageManager().getStorages().stream())
        .flatMap(i -> i);
  }

  /** Every enabled, withdrawable storage: the set the bank view shows. */
  @SuppressWarnings("java:S1452")
  public Stream<? extends Storage<? extends Enum<? extends Enum<?>>>> getViewableStorages() {
    return getStorages().filter(Storage::isEnabled).filter(Storage::isWithdrawable);
  }

  public List<ItemStack> getItems() {
    return getStorages().filter(Storage::isEnabled).map(Storage::getItems).flatMap(List::stream)
        .collect(Collectors.toList());
  }

  /**
   * Finds a storage by the (category, name) pair used in the DWMS "storages-response" message.
   * Category is the manager's config key, name is the storage display name.
   */
  public Optional<Storage<?>> findStorage(String category, String name) {
    for (StorageManager<?, ?> storageManager : storageManagers) {
      if (!Objects.equals(storageManager.getConfigKey(), category)) {
        continue;
      }

      for (Storage<?> storage : storageManager.getStorages()) {
        if (Objects.equals(storage.getName(), name)) {
          return Optional.of(storage);
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Builds the storage data in the "storages-response" PluginMessage shape: one entry per
   * non-empty enabled storage, with the storage manager's config key as the category and
   * canonical item ids. Must be called on the client thread (item ids are canonicalized).
   */
  public List<Map<String, Object>> getPluginMessageStorages() {
    var includedStorages = getStorages().filter(Storage::isEnabled).collect(Collectors.toSet());
    List<Map<String, Object>> storageData = new ArrayList<>();

    for (StorageManager<?, ?> storageManager : storageManagers) {
      for (Storage<?> storage : storageManager.getStorages()) {
        if (!includedStorages.contains(storage)) {
          continue;
        }

        List<Map<String, Object>> items = pluginMessageItems(storage, itemManager);
        if (!items.isEmpty()) {
          Map<String, Object> storageDatum = new HashMap<>();
          storageDatum.put("category", storageManager.getConfigKey());
          storageDatum.put("name", storage.getName());
          storageDatum.put("lastUpdated", storage.getLastUpdated());
          storageDatum.put("items", items);
          storageData.add(storageDatum);
        }
      }
    }

    return storageData;
  }

  /** The non-empty, canonicalized {@code {id, quantity}} entries of a single storage. */
  private List<Map<String, Object>> pluginMessageItems(Storage<?> storage, ItemManager itemManager) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (ItemStack itemStack : storage.getItems()) {
      if (itemStack.getId() <= 0 || itemStack.getQuantity() <= 0) {
        continue;
      }

      Map<String, Object> item = new HashMap<>();
      item.put("id", itemManager.canonicalize(itemStack.getId()));
      item.put("quantity", (long) itemStack.getQuantity());
      items.add(item);
    }
    return items;
  }
}
