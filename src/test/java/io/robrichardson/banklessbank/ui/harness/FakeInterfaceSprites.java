package io.robrichardson.banklessbank.ui.harness;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.SpriteID;

/**
 * Stand-in interface sprites for the headless harness. The real ones come out of the game cache,
 * which no test has, so {@code SpriteManager} is mocked to return either null (exercising
 * {@link io.robrichardson.banklessbank.ui.BankOverlay}'s flat-colour fallbacks) or these, which are
 * the right sizes and roughly the right palette and so exercise the sprite path: 9-slicing, tiling,
 * fitting and centring.
 *
 * <p>Only the ids the overlay actually asks for are generated. Anything else stays null, which is
 * also what the client does for an id its revision does not have.
 */
final class FakeInterfaceSprites
{
	private static final Color STEEL = new Color(0x3A, 0x39, 0x34);
	private static final Color STEEL_DARK = new Color(0x14, 0x14, 0x12);
	private static final Color STEEL_LIGHT = new Color(0x77, 0x75, 0x6A);
	private static final Color STONE = new Color(0x5B, 0x5A, 0x51);
	private static final Color STONE_DARK = new Color(0x2B, 0x2B, 0x26);
	private static final Color PANEL = new Color(0x49, 0x40, 0x34);
	private static final Color ORANGE = new Color(0xFF, 0x98, 0x1F);

	private final Map<Integer, BufferedImage> byId = new HashMap<>();

	FakeInterfaceSprites()
	{
		final int b = 8;
		byId.put(SpriteID.Steelborder.TOP_LEFT, corner(b, true, true));
		byId.put(SpriteID.Steelborder.TOP_RIGHT, corner(b, false, true));
		byId.put(SpriteID.Steelborder.BOTTOM_LEFT, corner(b, true, false));
		byId.put(SpriteID.Steelborder.BOTTOM_RIGHT, corner(b, false, false));
		byId.put(SpriteID.Steelborder2.EDGE_TOP, edgeTop(16, b));
		byId.put(SpriteID.Steelborder2.EDGE_RIGHT, edgeRight(b, 16));

		byId.put(SpriteID.SteelborderCloseButton._0, closeButton(21, false));
		byId.put(SpriteID.SteelborderCloseButton._1, closeButton(21, true));

		byId.put(SpriteID.Banktabs.TAB, tab(40, 40, STONE));
		byId.put(SpriteID.Banktabs.HOVERED, tab(40, 40, STONE.brighter()));
		byId.put(SpriteID.Banktabs.SELECTED, tab(40, 40, PANEL));
		byId.put(SpriteID.Banktabs.EMPTY, tab(40, 40, STONE_DARK));

		byId.put(SpriteID.BanktabIcons.ALL_ITEMS, infinity(22, 14));
		byId.put(SpriteID.BanktabIcons.ADD, plus(18, 18));

		byId.put(SpriteID.ScrollbarV2.ARROW_UP, arrow(16, true));
		byId.put(SpriteID.ScrollbarV2.ARROW_DOWN, arrow(16, false));
		byId.put(SpriteID.ScrollbarDraggerV2.TOP, dragger(16, 5, true, false));
		byId.put(SpriteID.ScrollbarDraggerV2.MIDDLE, dragger(16, 4, false, false));
		byId.put(SpriteID.ScrollbarDraggerV2.BOTTOM, dragger(16, 5, false, true));
		byId.put(SpriteID.ScrollbarDraggerV2.TRACK, track(16, 8));

		byId.put(SpriteID.Bankbuttons.SEARCH, magnifier(20));
	}

	BufferedImage get(int spriteId)
	{
		return byId.get(spriteId);
	}

	private static BufferedImage blank(int w, int h)
	{
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	private static BufferedImage corner(int size, boolean left, boolean top)
	{
		final BufferedImage image = blank(size, size);
		final Graphics2D g = image.createGraphics();
		g.setColor(STEEL);
		g.fillRect(0, 0, size, size);
		g.setColor(STEEL_DARK);
		g.drawRect(0, 0, size - 1, size - 1);
		g.setColor(STEEL_LIGHT);
		g.fillRect(left ? 2 : size - 4, top ? 2 : size - 4, 2, 2);
		g.dispose();
		return image;
	}

	private static BufferedImage edgeTop(int w, int h)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(STEEL);
		g.fillRect(0, 0, w, h);
		g.setColor(STEEL_DARK);
		g.drawLine(0, 0, w - 1, 0);
		g.drawLine(0, h - 1, w - 1, h - 1);
		g.setColor(STEEL_LIGHT);
		g.fillRect(w / 2 - 1, 2, 2, 2);
		g.dispose();
		return image;
	}

	private static BufferedImage edgeRight(int w, int h)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(STEEL);
		g.fillRect(0, 0, w, h);
		g.setColor(STEEL_DARK);
		g.drawLine(0, 0, 0, h - 1);
		g.drawLine(w - 1, 0, w - 1, h - 1);
		g.setColor(STEEL_LIGHT);
		g.fillRect(w - 4, h / 2 - 1, 2, 2);
		g.dispose();
		return image;
	}

	private static BufferedImage closeButton(int size, boolean hovered)
	{
		final BufferedImage image = blank(size, size);
		final Graphics2D g = image.createGraphics();
		g.setColor(hovered ? STONE.brighter() : STONE);
		g.fillRect(0, 0, size, size);
		g.setColor(STEEL_DARK);
		g.drawRect(0, 0, size - 1, size - 1);
		g.setColor(new Color(0xE8, 0xE0, 0xD0));
		g.setStroke(new BasicStroke(2f));
		g.drawLine(5, 5, size - 6, size - 6);
		g.drawLine(size - 6, 5, 5, size - 6);
		g.dispose();
		return image;
	}

	private static BufferedImage tab(int w, int h, Color fill)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(fill);
		g.fillRoundRect(0, 0, w, h, 8, 8);
		g.setColor(STEEL_DARK);
		g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
		g.setColor(STEEL_LIGHT);
		g.drawLine(3, 1, w - 4, 1);
		g.dispose();
		return image;
	}

	private static BufferedImage infinity(int w, int h)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(0xC8, 0xC2, 0xB0));
		g.setStroke(new BasicStroke(2f));
		g.drawOval(1, 2, h - 5, h - 5);
		g.drawOval(w - h + 3, 2, h - 5, h - 5);
		g.dispose();
		return image;
	}

	private static BufferedImage plus(int w, int h)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(0xC8, 0xC2, 0xB0));
		g.fillRect(w / 2 - 1, 2, 3, h - 4);
		g.fillRect(2, h / 2 - 1, w - 4, 3);
		g.dispose();
		return image;
	}

	private static BufferedImage arrow(int size, boolean up)
	{
		final BufferedImage image = blank(size, size);
		final Graphics2D g = image.createGraphics();
		g.setColor(STONE);
		g.fillRect(0, 0, size, size);
		g.setColor(STEEL_DARK);
		g.drawRect(0, 0, size - 1, size - 1);
		final int[] xs = {4, size - 4, size / 2};
		final int[] ys = up ? new int[]{size - 5, size - 5, 4} : new int[]{4, 4, size - 5};
		g.setColor(new Color(0x1E, 0x1B, 0x14));
		g.fillPolygon(xs, ys, 3);
		g.dispose();
		return image;
	}

	private static BufferedImage dragger(int w, int h, boolean top, boolean bottom)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x6E, 0x6B, 0x5F));
		g.fillRect(0, 0, w, h);
		g.setColor(STEEL_LIGHT);
		if (top)
		{
			g.drawLine(0, 0, w - 1, 0);
		}
		g.drawLine(0, 0, 0, h - 1);
		g.setColor(STEEL_DARK);
		g.drawLine(w - 1, 0, w - 1, h - 1);
		if (bottom)
		{
			g.drawLine(0, h - 1, w - 1, h - 1);
		}
		g.dispose();
		return image;
	}

	private static BufferedImage track(int w, int h)
	{
		final BufferedImage image = blank(w, h);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x25, 0x20, 0x19));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x14, 0x11, 0x0D));
		g.drawLine(0, 0, 0, h - 1);
		g.drawLine(w - 1, 0, w - 1, h - 1);
		g.dispose();
		return image;
	}

	private static BufferedImage magnifier(int size)
	{
		final BufferedImage image = blank(size, size);
		final Graphics2D g = image.createGraphics();
		g.setColor(ORANGE);
		g.setStroke(new BasicStroke(2f));
		g.drawOval(2, 2, size - 8, size - 8);
		g.drawLine(size - 7, size - 7, size - 3, size - 3);
		g.dispose();
		return image;
	}
}
