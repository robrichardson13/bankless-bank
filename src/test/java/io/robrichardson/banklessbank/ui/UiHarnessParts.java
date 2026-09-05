package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.model.LayoutStore;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Test-only bridge. {@link BankViewController}, {@link BankInputListener}, {@link BankOverlay} and
 * {@link HudButtonOverlay} all have package-private constructors (they are Guice-injected in
 * production), so the render harness in the {@code harness} sub-package cannot call them directly.
 * This lives in their package purely to hand them over. Nothing here is in {@code src/main}.
 */
public final class UiHarnessParts
{
	private UiHarnessParts()
	{
	}

	public static BankViewController controller(BanklessBankPlugin plugin, Client client,
		ItemManager itemManager, ConfigManager configManager, BanklessBankConfig config,
		LayoutStore layoutStore)
	{
		return new BankViewController(plugin, client, itemManager, configManager, config, layoutStore);
	}

	public static BankInputListener listener(BanklessBankConfig config, BankViewController controller)
	{
		return new BankInputListener(config, controller);
	}

	public static BankOverlay bankOverlay(Client client, ItemManager itemManager,
		BanklessBankConfig config, BankViewController controller, BankInputListener listener,
		TooltipManager tooltipManager)
	{
		return new BankOverlay(client, itemManager, config, controller, listener, tooltipManager);
	}

	public static HudButtonOverlay hudOverlay(BankViewController controller, BankInputListener listener)
	{
		return new HudButtonOverlay(controller, listener);
	}
}
