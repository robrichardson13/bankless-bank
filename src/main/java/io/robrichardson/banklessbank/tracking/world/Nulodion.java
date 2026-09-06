/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.util.Text;

/** Nulodion is responsible for tracking when the player can claim a new cannon from Nulodion. */
public class Nulodion extends WorldStorage {
  protected Nulodion(BanklessBankPlugin plugin) {
    super(WorldStorageType.NULODION, plugin);

    hasStaticItems = true;

    items.add(new ItemStack(ItemID.TWPART2, plugin));
    items.add(new ItemStack(ItemID.TWPART1, plugin));
    items.add(new ItemStack(ItemID.TWPART4, plugin));
    items.add(new ItemStack(ItemID.TWPART3, plugin));
  }

  @Override
  public boolean onChatMessage(ChatMessage chatMessage) {
    if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
      String message = Text.removeTags(chatMessage.getMessage());
      if (message.startsWith("Your cannon has decayed")) {
        items.forEach(itemStack -> itemStack.setQuantity(1));
        updateLastUpdated();
        return true;
      }

      if (message.startsWith("The dwarf gives you a new cannon")) {
        items.forEach(itemStack -> itemStack.setQuantity(0));
        updateLastUpdated();
        return true;
      }
    }

    return false;
  }
}
