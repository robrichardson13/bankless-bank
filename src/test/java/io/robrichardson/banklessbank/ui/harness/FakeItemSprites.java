package io.robrichardson.banklessbank.ui.harness;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Stand-in for the game cache's item sprites. Generates a deterministic 36x32
 * {@link AsyncBufferedImage} per (id, quantity, withCount) triple: a coloured rounded rect keyed by
 * item id, a three-letter abbreviation of the item's name, and, when the overlay asked for a count,
 * the bank-style quantity text in the top-left in yellow / white / green by magnitude.
 *
 * <p>{@link AsyncBufferedImage} is constructed with a null {@code ClientThread}; nothing in the
 * render path calls {@code onLoaded}, so the field is never dereferenced.
 */
public final class FakeItemSprites
{
	private static final Color QTY_YELLOW = new Color(0xFF, 0xFF, 0x00);
	private static final Color QTY_WHITE = new Color(0xFF, 0xFF, 0xFF);
	private static final Color QTY_GREEN = new Color(0x00, 0xFF, 0x80);

	private final Map<Integer, String> names;
	private final Map<String, AsyncBufferedImage> cache = new HashMap<>();

	public FakeItemSprites(Map<Integer, String> names)
	{
		this.names = names;
	}

	public AsyncBufferedImage get(int itemId, int quantity, boolean withCount)
	{
		final String key = itemId + "/" + quantity + "/" + withCount;
		return cache.computeIfAbsent(key, k -> draw(itemId, quantity, withCount));
	}

	private AsyncBufferedImage draw(int itemId, int quantity, boolean withCount)
	{
		final int w = 36;
		final int h = 32;
		final AsyncBufferedImage image = new AsyncBufferedImage(null, w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		final Color body = colourFor(itemId);
		g.setColor(body);
		g.fillRoundRect(2, 4, w - 5, h - 9, 8, 8);
		g.setColor(body.darker().darker());
		g.drawRoundRect(2, 4, w - 5, h - 9, 8, 8);

		final String abbreviation = abbreviate(names.get(itemId), itemId);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		final FontMetrics fm = g.getFontMetrics();
		g.setColor(contrastOn(body));
		g.drawString(abbreviation, (w - fm.stringWidth(abbreviation)) / 2, h / 2 + fm.getAscent() / 2 - 1);

		if (withCount && quantity > 1)
		{
			final String text = formatQuantity(quantity);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
			g.setColor(Color.BLACK);
			g.drawString(text, 2, 9);
			g.setColor(quantityColour(quantity));
			g.drawString(text, 1, 8);
		}

		g.dispose();
		return image;
	}

	/** The real bank's magnitude colouring: yellow under 100k, white under 10m, green above. */
	private static Color quantityColour(long quantity)
	{
		if (quantity < 100_000)
		{
			return QTY_YELLOW;
		}
		return quantity < 10_000_000 ? QTY_WHITE : QTY_GREEN;
	}

	private static String formatQuantity(long quantity)
	{
		if (quantity >= 10_000_000)
		{
			return (quantity / 1_000_000) + "M";
		}
		if (quantity >= 100_000)
		{
			return (quantity / 1000) + "K";
		}
		return String.valueOf(quantity);
	}

	private static String abbreviate(String name, int itemId)
	{
		if (name == null || name.isEmpty())
		{
			return String.valueOf(itemId % 1000);
		}
		final String letters = name.replaceAll("[^A-Za-z]", "");
		if (letters.length() <= 3)
		{
			return letters.isEmpty() ? String.valueOf(itemId % 1000) : letters;
		}
		return letters.substring(0, 3);
	}

	/** Stable hue per item id, so the same item is the same colour in every screenshot. */
	private static Color colourFor(int itemId)
	{
		final float hue = ((itemId * 2654435761L) % 360L) / 360f;
		return Color.getHSBColor(Math.abs(hue), 0.55f, 0.78f);
	}

	private static Color contrastOn(Color background)
	{
		final int luma = (background.getRed() * 299 + background.getGreen() * 587 + background.getBlue() * 114) / 1000;
		return luma > 140 ? Color.BLACK : Color.WHITE;
	}
}
