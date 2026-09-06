/*
 * Ported from "Dude, Where's My Stuff?" by Thource (https://github.com/Thource/dude-wheres-my-stuff)
 * Copyright (c) 2022, Thource. Licensed under the BSD 2-Clause License.
 */
package io.robrichardson.banklessbank.tracking.world;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.tracking.ItemStack;
import io.robrichardson.banklessbank.tracking.Var;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** ElnockInquisitor is responsible for tracking imp catching tools stored at Elnock Inquisitor. */
public class ElnockInquisitor extends WorldStorage {

  private final ItemStack netStack;
  private final ItemStack magicNetStack;

  protected ElnockInquisitor(BanklessBankPlugin plugin) {
    super(WorldStorageType.ELNOCK_INQUISITOR, plugin);

    hasStaticItems = true;

    varbits = new int[] {VarbitID.II_STORED_REPELLENT, VarbitID.II_STORED_IMPLING_JARS};
    varbitItemOffset = 2;

    netStack = new ItemStack(ItemID.HUNTING_BUTTERFLY_NET, plugin);
    magicNetStack = new ItemStack(ItemID.II_MAGIC_BUTTERFLY_NET, plugin);

    items.add(netStack);
    items.add(magicNetStack);
    items.add(new ItemStack(ItemID.II_IMP_REPELLENT, plugin));
    items.add(new ItemStack(ItemID.II_IMPLING_JAR, plugin));
  }

  @Override
  public boolean onVarbitChanged(VarbitChanged varbitChanged) {
    var updated = super.onVarbitChanged(varbitChanged);

    var netVar = Var.bit(varbitChanged, VarbitID.II_STORED_NET);
    if (netVar.wasChanged()) {
      int newNet = netVar.getValue(plugin.getClient());

      if (newNet == 0 && (netStack.getQuantity() != 0 || magicNetStack.getQuantity() != 0)) {
        netStack.setQuantity(0);
        magicNetStack.setQuantity(0);
        updated = true;
      } else if (newNet == 1 && netStack.getQuantity() != 1) {
        netStack.setQuantity(1);
        magicNetStack.setQuantity(0);
        updated = true;
      } else if (newNet == 2 && magicNetStack.getQuantity() != 1) {
        netStack.setQuantity(0);
        magicNetStack.setQuantity(1);
        updated = true;
      }
    }

    return updated;
  }
}
