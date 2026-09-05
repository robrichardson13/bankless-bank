/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.carryable;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemContainerWatcher;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.ItemStackUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID.Inventory;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

/** TackleBox is responsible for tracking the player's items in their tackle box. */
@Getter
public class TackleBox extends CarryableStorage {

  private final List<SuspendedItem> itemsUsed = new ArrayList<>();

  public TackleBox(BanklessBankPlugin plugin) {
    super(CarryableStorageType.TACKLE_BOX, plugin);
  }

  @Override
  public boolean onGameTick() {
    boolean didUpdate = super.onGameTick();

    if (checkUsedItems()) {
      didUpdate = true;
    }

    return didUpdate;
  }

  private boolean checkUsedItems() {
    boolean updated = false;

    for (ItemStack itemStack :
        ItemContainerWatcher.getInventoryWatcher().getItemsRemovedLastTick()) {
      if (itemsUsed.stream().anyMatch(i -> i.getId() == itemStack.getId())) {
        ItemStackUtils.addItemStack(items, itemStack);
        updated = true;
      }
    }

    ListIterator<SuspendedItem> listIterator = itemsUsed.listIterator();
    while (listIterator.hasNext()) {
      SuspendedItem item = listIterator.next();

      if (item.getTicksLeft() <= 1) {
        listIterator.remove();
      } else {
        item.setTicksLeft(item.getTicksLeft() - 1);
      }
    }

    if (updated) {
      updateLastUpdated();
    }

    return updated;
  }

  @Override
  public boolean onMenuOptionClicked(MenuOptionClicked menuOption) {
    Widget item1Widget = plugin.getClient().getSelectedWidget();

    if (menuOption.getWidget() == null || menuOption.getWidget().getParentId() != Inventory.ITEMS) {
      return false;
    }

    Widget item2Widget = menuOption.getWidget();
    if (item1Widget == null) {
      if (!menuOption.getMenuOption().equals("Use")
          && !menuOption.getMenuOption().equals("Examine")) {
        itemsUsed.removeIf(item -> item.getInventorySlot() == item2Widget.getIndex());
      }

      return false;
    }

    // one of the widgets is not an item
    if (item1Widget.getItemId() == -1 || item2Widget.getItemId() == -1) {
      return false;
    }

    boolean item1IsTackleBox = type.getContainerIds().contains(item1Widget.getItemId());
    // Item wasn't used on a tackle box
    if (!item1IsTackleBox && !type.getContainerIds().contains(item2Widget.getItemId())) {
      return false;
    }

    Widget itemWidget = (item1IsTackleBox ? item2Widget : item1Widget);
    itemsUsed.add(
        new SuspendedItem(
            itemWidget.getIndex(),
            itemWidget.getItemId(),
            Objects.requireNonNull(plugin.getClient().getItemContainer(InventoryID.INV))
                .count(itemWidget.getItemId())));

    return false;
  }

  @Override
  public boolean onChatMessage(ChatMessage chatMessage) {
    if (chatMessage.getType() != ChatMessageType.SPAM
        && chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
      return false;
    }

    if (chatMessage.getMessage().startsWith("The tackle box is now empty")) {
      resetItems();
      updateLastUpdated();
    }

    return false;
  }
}
