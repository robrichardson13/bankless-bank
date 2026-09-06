package io.robrichardson.banklessbank.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The always-visible launch button. An ordinary movable, snappable overlay, so RuneLite's alt-drag
 * moves it and {@code OverlayManager} persists its location for us. Clicks are handled by
 * {@link BankInputListener}, which is why the button publishes its bounds every frame.
 */
@Singleton
public class HudButtonOverlay extends Overlay
{
	public static final int SIZE = 30;

	private static final Color PANEL = new Color(0x3E, 0x35, 0x29, 0xD0);
	private static final Color BORDER = new Color(0x5F, 0x54, 0x3F);
	private static final Color GLYPH = new Color(0xFF, 0x98, 0x1F);
	private static final Color OPEN_TINT = new Color(0xFF, 0xFF, 0xFF, 40);
	private static final Dimension DIMENSION = new Dimension(SIZE, SIZE);

	private final BankViewController controller;
	private final BankInputListener listener;

	@Inject
	HudButtonOverlay(BankViewController controller, BankInputListener listener)
	{
		this.controller = controller;
		this.listener = listener;

		setPosition(OverlayPosition.BOTTOM_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
		setResizable(false);

		addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Toggle", "Bankless Bank",
			e -> controller.toggle());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// OverlayManager's draw-order comparator (OVERLAY_COMPARATOR) sorts by position group before
		// priority, and a DYNAMIC overlay's group always sorts - and so draws - ahead of any
		// snap-corner position group, regardless of either overlay's priority. BankOverlay is
		// DYNAMIC and this overlay is a snap-corner position (BOTTOM_LEFT, movable/snappable), so no
		// setPriority value can make the bank draw after us: RuneLite always draws us last, on top,
		// once we land in the same screen area. BankOverlay renders earlier in the same frame (that
		// same ordering), so by the time we get here the listener holds this frame's bank state, not
		// last frame's - fall back to not drawing at all rather than paint over the bank's content.
		final Rectangle bounds = getBounds();
		final Rectangle hudRect = new Rectangle(bounds.x, bounds.y, SIZE, SIZE);
		if (listener.isBankOpen() && listener.getBankBounds().intersects(hudRect))
		{
			listener.publishHud(false, null);
			return null;
		}

		graphics.setColor(PANEL);
		graphics.fillRoundRect(0, 0, SIZE, SIZE, 6, 6);
		graphics.setColor(BORDER);
		graphics.drawRoundRect(0, 0, SIZE - 1, SIZE - 1, 6, 6);

		// A bank booth: a counter with two chest slots behind it.
		final Stroke old = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.setColor(GLYPH);
		graphics.drawRect(6, 8, 8, 7);
		graphics.drawRect(16, 8, 8, 7);
		graphics.fillRect(5, 19, 20, 3);
		graphics.drawLine(8, 22, 8, 25);
		graphics.drawLine(22, 22, 22, 25);
		graphics.setStroke(old);

		if (controller.isOpen())
		{
			graphics.setColor(OPEN_TINT);
			graphics.fillRoundRect(0, 0, SIZE, SIZE, 6, 6);
		}

		listener.publishHud(true, hudRect);

		return DIMENSION;
	}
}
