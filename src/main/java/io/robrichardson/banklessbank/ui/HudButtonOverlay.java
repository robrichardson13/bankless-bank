package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Constants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The always-visible launch button. An ordinary movable, snappable overlay, so RuneLite's alt-drag
 * moves it and {@code OverlayManager} persists its location for us. Clicks are handled by
 * {@link BankInputListener}, which is why the button publishes its bounds every frame. Drawn
 * under every widget, so the bank window (ABOVE_WIDGETS) and chat always paint over it.
 */
@Singleton
public class HudButtonOverlay extends Overlay
{
	/** The config's default size, and the size the bank-booth glyph's coordinates are drawn at. */
	public static final int DEFAULT_SIZE = 30;
	private static final int ICON_PADDING = 2;
	private static final Color OPEN_TINT = new Color(0xFF, 0xFF, 0xFF, 40);

	private final BankViewController controller;
	private final BankInputListener listener;
	private final BanklessBankConfig config;
	private final ItemManager itemManager;

	@Inject
	HudButtonOverlay(BankViewController controller, BankInputListener listener, BanklessBankConfig config,
		ItemManager itemManager)
	{
		this.controller = controller;
		this.listener = listener;
		this.config = config;
		this.itemManager = itemManager;

		setPosition(OverlayPosition.BOTTOM_LEFT);
		// ABOVE_SCENE, not UNDER_WIDGETS: OverlayManager promotes an alt-dragged UNDER_WIDGETS overlay
		// to ABOVE_WIDGETS, which would put a moved button back on top of chat and PM text.
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
		setResizable(false);

		addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Toggle", "Bankless Bank",
			e -> controller.toggle());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final int size = config.hudSize();
		final int arc = Math.max(4, size / 5);

		graphics.setColor(config.hudBackgroundColor());
		graphics.fillRoundRect(0, 0, size, size, arc, arc);
		graphics.setColor(config.hudBorderColor());
		graphics.drawRoundRect(0, 0, size - 1, size - 1, arc, arc);

		final int iconItemId = config.hudIconItemId();
		if (iconItemId > 0)
		{
			drawItemIcon(graphics, iconItemId, size);
		}
		else
		{
			drawBankBooth(graphics, size);
		}

		if (controller.isOpen())
		{
			graphics.setColor(OPEN_TINT);
			graphics.fillRoundRect(0, 0, size, size, arc, arc);
		}

		final Rectangle bounds = getBounds();
		listener.publishHud(true, new Rectangle(bounds.x, bounds.y, size, size));

		return new Dimension(size, size);
	}

	/** The item's inventory sprite, scaled to fit inside the button and centred. */
	private void drawItemIcon(Graphics2D graphics, int itemId, int size)
	{
		final BufferedImage image = itemManager.getImage(itemId);
		final double scale = Math.min(
			(size - 2.0 * ICON_PADDING) / Constants.ITEM_SPRITE_WIDTH,
			(size - 2.0 * ICON_PADDING) / Constants.ITEM_SPRITE_HEIGHT);
		final int width = (int) Math.round(Constants.ITEM_SPRITE_WIDTH * scale);
		final int height = (int) Math.round(Constants.ITEM_SPRITE_HEIGHT * scale);
		graphics.drawImage(image, (size - width) / 2, (size - height) / 2, width, height, null);
	}

	/** A bank booth: a counter with two chest slots behind it. */
	private void drawBankBooth(Graphics2D graphics, int size)
	{
		final AffineTransform oldTransform = graphics.getTransform();
		final Stroke oldStroke = graphics.getStroke();
		graphics.scale(size / (double) DEFAULT_SIZE, size / (double) DEFAULT_SIZE);
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.setColor(config.hudGlyphColor());
		graphics.drawRect(6, 8, 8, 7);
		graphics.drawRect(16, 8, 8, 7);
		graphics.fillRect(5, 19, 20, 3);
		graphics.drawLine(8, 22, 8, 25);
		graphics.drawLine(22, 22, 22, 25);
		graphics.setStroke(oldStroke);
		graphics.setTransform(oldTransform);
	}
}
