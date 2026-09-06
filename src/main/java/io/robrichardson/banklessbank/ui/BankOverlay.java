package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

/**
 * Draws the bank view. Self-positioned: a {@code DYNAMIC} overlay with no preferred location is
 * drawn at whatever {@link #getBounds()} says, so writing the bounds location each frame from the
 * controller is all the positioning we need. Not movable, so RuneLite's alt-drag ignores it and
 * the title bar is the only drag handle.
 *
 * <p>The chrome is drawn from the game's own interface sprites through {@link SpriteManager}: the
 * steel window frame, the close button, the bank tab buttons and their infinity / plus icons, the
 * scrollbar arrows and dragger, and the bank's search icon. {@code SpriteManager.getSprite} returns
 * null before the cache is up (and always in the headless render harness, which has no game), so
 * every sprite has a flat-colour fallback painted from the palette sampled off a real bank
 * screenshot. Sprites are cached here as well as in {@code SpriteManager}, and only ever fetched
 * from {@link #render}, which RuneLite calls on the client thread.
 */
@Singleton
public class BankOverlay extends Overlay
{
	// Palette sampled from a screenshot of the real bank interface.
	private static final Color PANEL = new Color(0x49, 0x40, 0x34);
	private static final Color PANEL_DARK = new Color(0x3A, 0x33, 0x29);
	private static final Color FRAME = new Color(0x2B, 0x2A, 0x26);
	private static final Color FRAME_LIGHT = new Color(0x54, 0x52, 0x49);
	private static final Color FRAME_DARK = new Color(0x0E, 0x0E, 0x0C);
	private static final Color RIVET = new Color(0x6B, 0x69, 0x5E);
	private static final Color TITLE = JagexColors.DARK_ORANGE_INTERFACE_TEXT;
	private static final Color COUNT = JagexColors.YELLOW_INTERFACE_TEXT;
	private static final Color TEXT_DIM = new Color(0xC8, 0xC8, 0xC8);
	private static final Color HOVER = new Color(255, 255, 255, 30);
	private static final Color STONE = new Color(0x54, 0x53, 0x4B);
	private static final Color STONE_LIGHT = new Color(0x74, 0x72, 0x67);
	private static final Color STONE_DARK = new Color(0x2A, 0x2A, 0x26);
	private static final Color STONE_PRESSED = new Color(0x3E, 0x3D, 0x37);
	private static final Color ICON = new Color(0xC8, 0xC2, 0xB0);
	private static final Color SCROLL_TRACK = new Color(0x25, 0x20, 0x19);
	private static final Color SCROLL_THUMB = new Color(0x6E, 0x6B, 0x5F);
	// The game's own "Choose Option" menu, matched exactly: a tan frame whose colour is reused for
	// the header text, a black body, a grey hover bar, white options and orange targets. RuneLite
	// only publishes the last of these (JagexColors.MENU_TARGET) - the client draws the rest
	// natively, so the others are transcribed from it.
	private static final Color MENU_FRAME = new Color(0x5D, 0x54, 0x47);
	private static final Color MENU_BG = Color.BLACK;
	private static final Color MENU_HILIGHT = new Color(0x80, 0x80, 0x80);
	private static final Color MENU_TEXT = Color.WHITE;
	private static final Color MENU_SHADOW = Color.BLACK;
	private static final Color CARET = new Color(0xFF, 0xFF, 0x00);
	private static final Color DROP_TAB = new Color(0xFF, 0xFF, 0x00, 90);
	private static final Color TAB_REORDER_MARKER = Color.WHITE;

	private static final Rectangle EMPTY = new Rectangle();

	/** Spacing of the decorative rivets along the top and bottom of the frame. */
	private static final int RIVET_PITCH = 14;

	// Self-drawn tooltip geometry, mirroring RuneLite's own tooltip placement (see the class doc on
	// drawTooltip for why we no longer route through TooltipManager).
	private static final int TOOLTIP_CURSOR_DX = 10;
	private static final int TOOLTIP_CURSOR_DY = 18;
	private static final int TOOLTIP_PAD_X = 4;
	private static final int TOOLTIP_PAD_Y = 3;
	private static final int TOOLTIP_LINE_GAP = 2;
	private static final Color TOOLTIP_BG = new Color(0x00, 0x00, 0x00, 0xC0);
	private static final Color TOOLTIP_BORDER = new Color(0x5F, 0x54, 0x3F);

	private final Client client;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final BanklessBankConfig config;
	private final BankViewController controller;
	private final BankInputListener listener;

	/** Client-thread only. Sprite id -> image, populated the first frame the cache can serve it. */
	private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();

	@Inject
	BankOverlay(Client client, ItemManager itemManager, SpriteManager spriteManager,
		BanklessBankConfig config, BankViewController controller, BankInputListener listener)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.config = config;
		this.controller = controller;
		this.listener = listener;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		setMovable(false);
		setSnappable(false);
		setResizable(false);
		setResettable(false);
		setDragTargetable(false);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		controller.drainActions();

		// Config-to-model sync must happen before refresh(), which may run syncLayout() (and persist
		// the result) even while the view is closed. Both setters no-op when the value is unchanged,
		// so this is cheap to do every frame regardless of open state.
		final BankViewModel model = controller.getViewModel();
		model.setPlaceholdersEnabled(config.placeholders());
		model.setShowEmptyStorages(config.showEmptyStorages());

		controller.refresh();

		if (!controller.isOpen())
		{
			listener.publish(false, EMPTY, false, false);
			return null;
		}

		model.rebuild();

		// OverlayRenderer translated the graphics to getBounds()'s location before calling us, using
		// the value from the previous frame. Correct the translation so the window is drawn where the
		// controller says it is this frame, not one frame late. The renderer restores the transform
		// after render() returns.
		final Point origin = controller.getPosition();
		final Point drawnAt = getBounds().getLocation();
		graphics.translate(origin.x - drawnAt.x, origin.y - drawnAt.y);
		getBounds().setLocation(origin);

		final Dimension size = model.size();
		final Point mouse = listener.getLastMouse();
		final Point local = mouse == null ? null : new Point(mouse.x - origin.x, mouse.y - origin.y);

		drawPanel(graphics, size);
		drawGrid(graphics, model, local);
		if (model.isVScrollbarVisible())
		{
			drawScrollbar(graphics, model, local);
		}
		if (model.isHScrollbarVisible())
		{
			drawHScrollbar(graphics, model, local);
		}
		drawTabs(graphics, model, local);
		drawBottomBar(graphics, model, local);
		drawFrame(graphics, size);
		drawResizeGrip(graphics, model, local);
		drawTitle(graphics, model, local);
		drawDrag(graphics, model, local);
		drawMenu(graphics, model, local);
		drawTooltip(graphics, model, local);

		listener.publish(true, new Rectangle(origin.x, origin.y, size.width, size.height),
			client.isKeyPressed(KeyCode.KC_ALT), model.isMenuOpen());
		controller.publishSearchFocused(model.isSearchFocused());

		return size;
	}

	// ---- sprites ---------------------------------------------------------------------------

	/**
	 * The interface sprite, or null while the cache cannot serve it. Client thread only:
	 * {@code SpriteManager.getSprite} asserts as much, and returns null before the login screen.
	 */
	private BufferedImage sprite(int spriteId)
	{
		final BufferedImage cached = spriteCache.get(spriteId);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage loaded = null;
		try
		{
			loaded = spriteManager.getSprite(spriteId, 0);
		}
		catch (RuntimeException ignored)
		{
			// The cache can be mid-load, or the id can be missing on an old revision. Fall back.
		}

		if (loaded != null)
		{
			spriteCache.put(spriteId, loaded);
		}
		return loaded;
	}

	/** Draws {@code image} at its natural size, centred in {@code r}. */
	private static void drawCentred(Graphics2D graphics, BufferedImage image, Rectangle r)
	{
		graphics.drawImage(image, r.x + (r.width - image.getWidth()) / 2,
			r.y + (r.height - image.getHeight()) / 2, null);
	}

	/** Draws {@code image} scaled down to fit inside {@code r}, never scaled up. */
	private static void drawFitted(Graphics2D graphics, BufferedImage image, Rectangle r)
	{
		final int w = image.getWidth();
		final int h = image.getHeight();
		if (w <= r.width && h <= r.height)
		{
			drawCentred(graphics, image, r);
			return;
		}

		final double scale = Math.min(r.width / (double) w, r.height / (double) h);
		final int dw = Math.max(1, (int) Math.round(w * scale));
		final int dh = Math.max(1, (int) Math.round(h * scale));
		graphics.drawImage(image, r.x + (r.width - dw) / 2, r.y + (r.height - dh) / 2, dw, dh, null);
	}

	/** Repeats {@code image} left to right across {@code width}, clipped to it. */
	private void tileHorizontally(Graphics2D graphics, BufferedImage image, int x, int y, int width,
		boolean flipVertically)
	{
		final Shape oldClip = graphics.getClip();
		graphics.clipRect(x, y, width, image.getHeight());
		for (int dx = 0; dx < width; dx += image.getWidth())
		{
			if (flipVertically)
			{
				graphics.drawImage(image, x + dx, y, x + dx + image.getWidth(), y + image.getHeight(),
					0, image.getHeight(), image.getWidth(), 0, null);
			}
			else
			{
				graphics.drawImage(image, x + dx, y, null);
			}
		}
		graphics.setClip(oldClip);
	}

	/** Repeats {@code image} top to bottom down {@code height}, clipped to it. */
	private void tileVertically(Graphics2D graphics, BufferedImage image, int x, int y, int height,
		boolean flipHorizontally)
	{
		final Shape oldClip = graphics.getClip();
		graphics.clipRect(x, y, image.getWidth(), height);
		for (int dy = 0; dy < height; dy += image.getHeight())
		{
			if (flipHorizontally)
			{
				graphics.drawImage(image, x, y + dy, x + image.getWidth(), y + dy + image.getHeight(),
					image.getWidth(), 0, 0, image.getHeight(), null);
			}
			else
			{
				graphics.drawImage(image, x, y + dy, null);
			}
		}
		graphics.setClip(oldClip);
	}

	// ---- chrome ----------------------------------------------------------------------------

	/** The flat interior. Everything else is drawn on top of it. */
	private void drawPanel(Graphics2D graphics, Dimension size)
	{
		graphics.setColor(PANEL);
		graphics.fillRect(0, 0, size.width, size.height);
	}

	/**
	 * The steel window frame, 9-sliced from the game's border sprites: four corners at their
	 * natural size, the top edge tiled across (and flipped for the bottom), the right edge tiled
	 * down (and flipped for the left).
	 */
	private void drawFrame(Graphics2D graphics, Dimension size)
	{
		final BufferedImage topLeft = sprite(SpriteID.Steelborder.TOP_LEFT);
		final BufferedImage topRight = sprite(SpriteID.Steelborder.TOP_RIGHT);
		final BufferedImage bottomLeft = sprite(SpriteID.Steelborder.BOTTOM_LEFT);
		final BufferedImage bottomRight = sprite(SpriteID.Steelborder.BOTTOM_RIGHT);
		final BufferedImage edgeTop = sprite(SpriteID.Steelborder2.EDGE_TOP);
		final BufferedImage edgeRight = sprite(SpriteID.Steelborder2.EDGE_RIGHT);

		if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null
			|| edgeTop == null || edgeRight == null)
		{
			drawFrameFallback(graphics, size);
			return;
		}

		final int w = size.width;
		final int h = size.height;

		final int innerX = topLeft.getWidth();
		final int innerW = Math.max(0, w - topLeft.getWidth() - topRight.getWidth());
		tileHorizontally(graphics, edgeTop, innerX, 0, innerW, false);
		tileHorizontally(graphics, edgeTop, innerX, h - edgeTop.getHeight(), innerW, true);

		final int innerY = topLeft.getHeight();
		final int innerH = Math.max(0, h - topLeft.getHeight() - bottomLeft.getHeight());
		tileVertically(graphics, edgeRight, w - edgeRight.getWidth(), innerY, innerH, false);
		tileVertically(graphics, edgeRight, 0, innerY, innerH, true);

		graphics.drawImage(topLeft, 0, 0, null);
		graphics.drawImage(topRight, w - topRight.getWidth(), 0, null);
		graphics.drawImage(bottomLeft, 0, h - bottomLeft.getHeight(), null);
		graphics.drawImage(bottomRight, w - bottomRight.getWidth(), h - bottomRight.getHeight(), null);
	}

	/** Flat stand-in for the steel frame: dark bands, a bevel, and a row of rivets top and bottom. */
	private void drawFrameFallback(Graphics2D graphics, Dimension size)
	{
		final int w = size.width;
		final int h = size.height;
		final int b = BankGeometry.BORDER;

		graphics.setColor(FRAME);
		graphics.fillRect(0, 0, w, b);
		graphics.fillRect(0, h - b, w, b);
		graphics.fillRect(0, 0, b, h);
		graphics.fillRect(w - b, 0, b, h);

		// Dark outside, light lip on the inside, which is how the steel frame reads against the panel.
		graphics.setColor(FRAME_DARK);
		graphics.drawRect(0, 0, w - 1, h - 1);
		graphics.setColor(FRAME_LIGHT);
		graphics.drawLine(1, 1, w - 2, 1);
		graphics.drawLine(1, 1, 1, h - 2);
		graphics.setColor(RIVET);
		graphics.drawRect(b - 1, b - 1, w - b * 2 + 1, h - b * 2 + 1);
		graphics.setColor(FRAME_DARK);
		graphics.drawRect(b - 2, b - 2, w - b * 2 + 3, h - b * 2 + 3);

		// The frame's segment joints, then a rivet in the middle of each segment.
		graphics.setColor(FRAME_DARK);
		for (int x = RIVET_PITCH; x < w; x += RIVET_PITCH)
		{
			graphics.drawLine(x, 2, x, b - 3);
			graphics.drawLine(x, h - b + 2, x, h - 3);
		}
		for (int y = RIVET_PITCH; y < h; y += RIVET_PITCH)
		{
			graphics.drawLine(2, y, b - 3, y);
			graphics.drawLine(w - b + 2, y, w - 3, y);
		}

		for (int x = RIVET_PITCH / 2; x < w; x += RIVET_PITCH)
		{
			rivet(graphics, x, 2);
			rivet(graphics, x, h - 5);
		}
		for (int y = RIVET_PITCH / 2; y < h; y += RIVET_PITCH)
		{
			rivet(graphics, 2, y);
			rivet(graphics, w - 5, y);
		}
	}

	/** One 3x3 stud: a light face with a dark shadow down its right and bottom. */
	private static void rivet(Graphics2D graphics, int x, int y)
	{
		graphics.setColor(RIVET);
		graphics.fillRect(x, y, 2, 2);
		graphics.setColor(FRAME_DARK);
		graphics.drawLine(x + 2, y, x + 2, y + 2);
		graphics.drawLine(x, y + 2, x + 2, y + 2);
	}

	/**
	 * Self-drawn bottom-right resize grip: three diagonal hatch lines, the same shape a resizable
	 * desktop window uses. Drawn on top of the frame corner. Resizes both axes, in whole columns
	 * and whole rows.
	 */
	private void drawResizeGrip(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle r = model.resizeGripRect();
		final boolean hovered = local != null && r.contains(local.x, local.y);

		for (int d : new int[]{3, 7, 11})
		{
			final int x1 = r.x + r.width - 2;
			final int y1 = r.y + r.height - 2 - d;
			final int x2 = r.x + r.width - 2 - d;
			final int y2 = r.y + r.height - 2;

			graphics.setColor(FRAME_DARK);
			graphics.drawLine(x1 + 1, y1 + 1, x2 + 1, y2 + 1);

			graphics.setColor(hovered ? new Color(255, 255, 255, 140) : RIVET);
			graphics.drawLine(x1, y1, x2, y2);
		}
	}

	/** A raised stone button, the shape the bank's bottom-bar buttons use. */
	private void drawStoneButton(Graphics2D graphics, Rectangle r, boolean pressed, boolean hovered)
	{
		graphics.setColor(pressed ? STONE_PRESSED : (hovered ? STONE_LIGHT : STONE));
		graphics.fillRect(r.x, r.y, r.width, r.height);

		graphics.setColor(pressed ? STONE_DARK : STONE_LIGHT);
		graphics.drawLine(r.x, r.y, r.x + r.width - 2, r.y);
		graphics.drawLine(r.x, r.y, r.x, r.y + r.height - 2);

		graphics.setColor(pressed ? STONE_LIGHT : STONE_DARK);
		graphics.drawLine(r.x + r.width - 1, r.y + 1, r.x + r.width - 1, r.y + r.height - 1);
		graphics.drawLine(r.x + 1, r.y + r.height - 1, r.x + r.width - 1, r.y + r.height - 1);

		graphics.setColor(FRAME_DARK);
		graphics.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
	}

	private void drawTitle(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle title = model.titleBarRect();

		graphics.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = graphics.getFontMetrics();
		final String name = titleText(model);
		graphics.setColor(TITLE);
		graphics.drawString(name, title.x + (title.width - fm.stringWidth(name)) / 2,
			baseline(fm, title.y, title.height));

		// The real bank puts "used / capacity" stacked top-left. A viewer has no capacity, so line 1
		// is a plain count of owned items across every tab; line 2, dim, is the placeholder total,
		// shown only when non-zero so a fully-owned layout keeps the single-line look.
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics small = graphics.getFontMetrics();
		final int totalText = model.getTotalItemCount();
		final int placeholderTotal = model.getTotalPlaceholderCount();

		if (placeholderTotal > 0)
		{
			final int lineH = small.getHeight();
			final int top = title.y + (title.height - lineH * 2) / 2 + small.getAscent();
			graphics.setColor(COUNT);
			graphics.drawString(String.valueOf(totalText), title.x + 3, top);
			graphics.setColor(TEXT_DIM);
			graphics.drawString(String.valueOf(placeholderTotal), title.x + 3, top + lineH);
		}
		else
		{
			graphics.setColor(COUNT);
			graphics.drawString(String.valueOf(totalText), title.x + 3, baseline(small, title.y, title.height));
		}

		drawCloseButton(graphics, model, local);

		// A bevelled seam between the title and the tab strip, as the bank has.
		graphics.setColor(FRAME_DARK);
		graphics.drawLine(title.x, title.y + title.height - 1, title.x + title.width - 1,
			title.y + title.height - 1);
		graphics.setColor(FRAME_LIGHT);
		graphics.drawLine(title.x, title.y + title.height, title.x + title.width - 1,
			title.y + title.height);
	}

	/**
	 * The title bar's centred text: the tab name (or "Bankless Bank" for the All tab / by-storage
	 * view), with the GE value of what it shows appended in parentheses, shortened the way
	 * {@code BankPlugin.createValueText} shortens the real bank's - "Tab 7 (905K)". Omitted when the
	 * value is zero or the config item is off.
	 */
	private String titleText(BankViewModel model)
	{
		final String base = model.titleBaseName();

		if (!config.showValue())
		{
			return base;
		}

		final long value = model.titleValue();
		return value == 0 ? base : base + " (" + QuantityFormatter.quantityToStackSize(value) + ")";
	}

	private void drawCloseButton(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle close = model.closeButtonRect();
		final boolean hovered = local != null && close.contains(local.x, local.y);

		final BufferedImage image = sprite(hovered
			? SpriteID.SteelborderCloseButton._1 : SpriteID.SteelborderCloseButton._0);
		if (image != null)
		{
			drawFitted(graphics, image, close);
			return;
		}

		drawStoneButton(graphics, close, false, hovered);
		graphics.setColor(hovered ? Color.WHITE : new Color(0xE0, 0xD8, 0xC8));
		final Stroke old = graphics.getStroke();
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawLine(close.x + 7, close.y + 7, close.x + close.width - 8, close.y + close.height - 8);
		graphics.drawLine(close.x + close.width - 8, close.y + 7, close.x + 7, close.y + close.height - 8);
		graphics.setStroke(old);
	}

	// ---- tabs ------------------------------------------------------------------------------

	private void drawTabs(Graphics2D graphics, BankViewModel model, Point local)
	{
		final int strip = model.getStripLength();
		final int tabCount = model.getTabCount();
		final int active = model.getActiveTab();
		final int dropTab = dropTabIndex(model, local);

		for (int i = 0; i < strip; i++)
		{
			final Rectangle r = model.tabRect(i);
			final boolean isActive = (i == 0 && active == -1) || (i > 0 && i - 1 == active);
			final boolean hovered = local != null && r.contains(local.x, local.y);

			drawTabBackground(graphics, r, isActive, hovered);

			if (i == 0)
			{
				drawAllTabIcon(graphics, r);
			}
			else if (i - 1 < tabCount)
			{
				final int iconId = model.getTabIconItemId(i - 1);
				if (iconId > 0)
				{
					// Unscaled at its natural 36x32 whenever the tab is its full BankGeometry.TAB_W
					// wide; drawFitted only scales when a narrow window has shrunk the strip's buttons
					// below the sprite, which is the one case an icon would otherwise bleed into its
					// neighbour.
					final BufferedImage icon = itemManager.getImage(iconId, 1, false);
					if (icon != null)
					{
						drawFitted(graphics, icon, r);
					}
				}
				else
				{
					drawCentredText(graphics, FontManager.getRunescapeSmallFont(),
						String.valueOf(i), r, TEXT_DIM);
				}
			}
			else
			{
				drawPlusTabIcon(graphics, r);
			}

			if (i == dropTab)
			{
				graphics.setColor(DROP_TAB);
				graphics.fillRect(r.x, r.y, r.width, r.height);
			}
		}

		if (model.isTabDragging() && model.getTabDropIndex() >= 0)
		{
			final Rectangle marker = model.tabRect(model.getTabDropIndex() + 1);
			graphics.setColor(TAB_REORDER_MARKER);
			graphics.fillRect(marker.x, marker.y, 2, marker.height);
		}
	}

	private void drawTabBackground(Graphics2D graphics, Rectangle r, boolean active, boolean hovered)
	{
		final int spriteId = active ? SpriteID.Banktabs.SELECTED
			: (hovered ? SpriteID.Banktabs.HOVERED : SpriteID.Banktabs.TAB);
		final BufferedImage image = sprite(spriteId);
		if (image != null)
		{
			graphics.drawImage(image, r.x, r.y, r.width, r.height, null);
			return;
		}

		// A rounded stone rim round a recessed face, as the bank's tab buttons have. The selected
		// tab is the lit one; the rest sit back in the panel colour.
		graphics.setColor(active ? STONE : (hovered ? STONE_PRESSED : PANEL));
		graphics.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 8, 8);
		graphics.setColor(active ? STONE_LIGHT : STONE);
		graphics.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, 8, 8);
		graphics.setColor(STONE_DARK);
		graphics.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 8, 8);
	}

	/** The [All] tab's infinity mark. The RuneScape fonts have no U+221E, so the fallback is drawn. */
	private void drawAllTabIcon(Graphics2D graphics, Rectangle r)
	{
		final BufferedImage image = sprite(SpriteID.BanktabIcons.ALL_ITEMS);
		if (image != null)
		{
			drawFitted(graphics, image, r);
			return;
		}

		final int d = 10;
		final int cy = r.y + (r.height - d) / 2;
		final Stroke old = graphics.getStroke();
		graphics.setStroke(new BasicStroke(2f));
		graphics.setColor(ICON);
		graphics.drawOval(r.x + r.width / 2 - d + 1, cy, d, d);
		graphics.drawOval(r.x + r.width / 2 - 1, cy, d, d);
		graphics.setStroke(old);
	}

	/** The [+] tab. */
	private void drawPlusTabIcon(Graphics2D graphics, Rectangle r)
	{
		final BufferedImage image = sprite(SpriteID.BanktabIcons.ADD);
		if (image != null)
		{
			drawFitted(graphics, image, r);
			return;
		}

		final int cx = r.x + r.width / 2;
		final int cy = r.y + r.height / 2;
		graphics.setColor(ICON);
		graphics.fillRect(cx - 1, cy - 7, 3, 15);
		graphics.fillRect(cx - 7, cy - 1, 15, 3);
	}

	/** The strip index the current drag would drop onto, or -1. */
	private int dropTabIndex(BankViewModel model, Point local)
	{
		if (local == null)
		{
			return -1;
		}
		return model.dropTabStripIndex(local.x, local.y);
	}

	// ---- grid ------------------------------------------------------------------------------

	private void drawGrid(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle grid = model.gridRect();
		final Shape oldClip = graphics.getClip();
		graphics.clip(grid);

		// The bank draws no cell borders and no separate grid background: items sit straight on the
		// panel. Only the hover highlight marks a cell out.
		graphics.setFont(FontManager.getRunescapeSmallFont());

		final List<BankRow> rows = model.getRows();
		final List<BankSlot> slots = model.getSlots();
		final int scroll = model.getScroll();

		int flat = 0;
		for (BankRow row : rows)
		{
			final int y = grid.y + row.getY() - scroll;
			if (y + row.getHeight() < grid.y || y > grid.y + grid.height)
			{
				if (row.getKind() == BankRow.Kind.ITEMS)
				{
					flat += row.getSlots().size();
				}
				continue;
			}

			if (row.getKind() == BankRow.Kind.HEADER)
			{
				drawHeader(graphics, row, grid, y);
				continue;
			}

			for (int i = 0; i < row.getSlots().size(); i++)
			{
				drawSlot(graphics, model, slots.get(flat + i), flat + i, local);
			}
			flat += row.getSlots().size();
		}

		graphics.setClip(oldClip);
	}

	private void drawHeader(Graphics2D graphics, BankRow row, Rectangle grid, int y)
	{
		final FontMetrics fm = graphics.getFontMetrics();
		final int baseline = baseline(fm, y, row.getHeight()) - 1;

		graphics.setColor(PANEL_DARK);
		graphics.fillRect(grid.x, y, grid.width, row.getHeight());

		// An All-view tab divider carries an icon and reserves a left margin for it; a BY_STORAGE
		// header (headerIconItemId -1) starts its text flush left, as before.
		final boolean hasIcon = row.getHeaderIconItemId() > 0;
		final int textX = grid.x + (hasIcon ? BankGeometry.DIVIDER_ICON_W : 3);

		if (hasIcon)
		{
			final BufferedImage icon = itemManager.getImage(row.getHeaderIconItemId(), 1, false);
			if (icon != null)
			{
				drawFitted(graphics, icon,
					new Rectangle(grid.x + 2, y + 2, BankGeometry.DIVIDER_ICON_W - 4, row.getHeight() - 4));
			}
		}

		final String name = row.getHeaderText() == null ? "" : row.getHeaderText();
		graphics.setColor(TITLE);
		graphics.drawString(name, textX, baseline);

		final String subtitle = row.getHeaderSubtitle();
		int labelEndX = textX + fm.stringWidth(name);
		if (subtitle != null && !subtitle.isEmpty())
		{
			graphics.setColor(TEXT_DIM);
			graphics.drawString(subtitle, labelEndX + 6, baseline);
			labelEndX += 6 + fm.stringWidth(subtitle);
		}

		// The divider's separator line, echoing the real bank's engraved group boundary.
		if (hasIcon)
		{
			graphics.setColor(FRAME_DARK);
			final int lineY = y + row.getHeight() / 2;
			graphics.drawLine(labelEndX + 6, lineY, grid.x + grid.width - 4, lineY);
		}
	}

	private void drawSlot(Graphics2D graphics, BankViewModel model, BankSlot slot, int flatIndex, Point local)
	{
		final Rectangle r = model.slotRect(flatIndex);
		if (r.width <= 0)
		{
			return;
		}

		if (local != null && r.contains(local.x, local.y))
		{
			graphics.setColor(HOVER);
			graphics.fillRect(r.x, r.y, r.width, r.height);
		}

		if (slot.isEmpty())
		{
			return;
		}

		if (model.isDragging() && model.getDragSlot() != null
			&& model.getDragSlot().getCanonicalId() == slot.getCanonicalId())
		{
			return;
		}

		final int x = r.x + BankGeometry.ITEM_DX;
		final int y = r.y + BankGeometry.ITEM_DY;

		if (slot.isPlaceholder())
		{
			drawSprite(graphics, slot.getCanonicalId(), 1, false, x, y, 0.35f);
		}
		else
		{
			final int qty = (int) Math.max(1, Math.min(slot.getQuantity(), Integer.MAX_VALUE));
			drawSprite(graphics, slot.getCanonicalId(), qty, qty > 1, x, y, 1f);
		}
	}

	/**
	 * {@code withCount} is passed as {@code ItemManager}'s {@code stackable} flag, which is what
	 * decides whether the quantity text is drawn at all: we want bank-style counts on everything
	 * with more than one, and no "1" on singles, regardless of whether the item really stacks.
	 */
	private void drawSprite(Graphics2D graphics, int itemId, int quantity, boolean withCount,
		int x, int y, float alpha)
	{
		final BufferedImage image = itemManager.getImage(itemId, quantity, withCount);
		if (image == null)
		{
			return;
		}

		if (alpha >= 1f)
		{
			graphics.drawImage(image, x, y, null);
			return;
		}

		final Composite old = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		graphics.drawImage(image, x, y, null);
		graphics.setComposite(old);
	}

	// ---- scrollbar -------------------------------------------------------------------------

	/**
	 * The grid's vertical scrollbar column. Unlike the geometry's rect, which is a fixed part of the
	 * grid layout, drawing it is conditional: the caller only invokes this while
	 * {@link BankViewModel#isVScrollbarVisible()}, i.e. the active tab/mode/search has content taller
	 * than the viewport. Grid slot rects never move when it is hidden - the column simply goes
	 * unpainted, so a short tab's right edge is clean instead of showing an empty track.
	 */
	private void drawScrollbar(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle track = model.scrollTrackRect();

		final BufferedImage trackSprite = sprite(SpriteID.ScrollbarDraggerV2.TRACK);
		if (trackSprite != null)
		{
			tileVertically(graphics, trackSprite, track.x, track.y, track.height, false);
		}
		else
		{
			graphics.setColor(SCROLL_TRACK);
			graphics.fillRect(track.x, track.y, track.width, track.height);
			graphics.setColor(FRAME_DARK);
			graphics.drawRect(track.x, track.y, track.width - 1, track.height - 1);
		}

		drawScrollArrow(graphics, model.scrollUpRect(), true, local);
		drawScrollArrow(graphics, model.scrollDownRect(), false, local);

		drawScrollThumb(graphics, model.scrollThumbRect());
	}

	/**
	 * The grid's horizontal scrollbar. Unlike the vertical bar, which always occupies its own column,
	 * this one is not reserved space: the caller only invokes this while
	 * {@link BankViewModel#isHScrollbarVisible()}, i.e. the active tab is laid out wider than the
	 * viewport, and it draws overlaying the bottom 16px of the grid area (the last grid row is
	 * partially covered, which is fine).
	 */
	private void drawHScrollbar(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle track = model.hScrollTrackRect();

		graphics.setColor(SCROLL_TRACK);
		graphics.fillRect(track.x, track.y, track.width, track.height);
		graphics.setColor(FRAME_DARK);
		graphics.drawRect(track.x, track.y, Math.max(0, track.width - 1), track.height - 1);

		drawHScrollArrow(graphics, model.hScrollLeftRect(), true, local);
		drawHScrollArrow(graphics, model.hScrollRightRect(), false, local);

		final Rectangle thumb = model.hScrollThumbRect();
		graphics.setColor(SCROLL_THUMB);
		graphics.fillRect(thumb.x, thumb.y, thumb.width, thumb.height);
		graphics.setColor(STONE_LIGHT);
		graphics.drawLine(thumb.x, thumb.y, thumb.x + thumb.width - 2, thumb.y);
		graphics.drawLine(thumb.x, thumb.y, thumb.x, thumb.y + thumb.height - 2);
		graphics.setColor(FRAME_DARK);
		graphics.drawLine(thumb.x + thumb.width - 1, thumb.y, thumb.x + thumb.width - 1,
			thumb.y + thumb.height - 1);
		graphics.drawLine(thumb.x, thumb.y + thumb.height - 1, thumb.x + thumb.width - 1,
			thumb.y + thumb.height - 1);
	}

	private void drawHScrollArrow(Graphics2D graphics, Rectangle r, boolean left, Point local)
	{
		final BufferedImage image = sprite(left
			? SpriteID.ScrollbarV2.ARROW_LEFT : SpriteID.ScrollbarV2.ARROW_RIGHT);
		if (image != null)
		{
			drawFitted(graphics, image, r);
			return;
		}

		final boolean hovered = local != null && r.contains(local.x, local.y);
		drawStoneButton(graphics, r, false, hovered);

		final int cy = r.y + r.height / 2;
		final int near = r.x + 5;
		final int far = r.x + r.width - 5;
		final int[] ys = {cy - 4, cy + 4, cy};
		final int[] xs = left ? new int[]{far, far, near} : new int[]{near, near, far};
		graphics.setColor(new Color(0x1E, 0x1B, 0x14));
		graphics.fillPolygon(xs, ys, 3);
	}

	private void drawScrollArrow(Graphics2D graphics, Rectangle r, boolean up, Point local)
	{
		final BufferedImage image = sprite(up
			? SpriteID.ScrollbarV2.ARROW_UP : SpriteID.ScrollbarV2.ARROW_DOWN);
		if (image != null)
		{
			drawFitted(graphics, image, r);
			return;
		}

		final boolean hovered = local != null && r.contains(local.x, local.y);
		drawStoneButton(graphics, r, false, hovered);

		final int cx = r.x + r.width / 2;
		final int top = r.y + 5;
		final int bottom = r.y + r.height - 5;
		final int[] xs = {cx - 4, cx + 4, cx};
		final int[] ys = up ? new int[]{bottom, bottom, top} : new int[]{top, top, bottom};
		graphics.setColor(new Color(0x1E, 0x1B, 0x14));
		graphics.fillPolygon(xs, ys, 3);
	}

	private void drawScrollThumb(Graphics2D graphics, Rectangle thumb)
	{
		final BufferedImage top = sprite(SpriteID.ScrollbarDraggerV2.TOP);
		final BufferedImage middle = sprite(SpriteID.ScrollbarDraggerV2.MIDDLE);
		final BufferedImage bottom = sprite(SpriteID.ScrollbarDraggerV2.BOTTOM);

		if (top != null && middle != null && bottom != null)
		{
			final int middleH = Math.max(0, thumb.height - top.getHeight() - bottom.getHeight());
			graphics.drawImage(top, thumb.x, thumb.y, null);
			tileVertically(graphics, middle, thumb.x, thumb.y + top.getHeight(), middleH, false);
			graphics.drawImage(bottom, thumb.x, thumb.y + thumb.height - bottom.getHeight(), null);
			return;
		}

		graphics.setColor(SCROLL_THUMB);
		graphics.fillRect(thumb.x, thumb.y, thumb.width, thumb.height);
		graphics.setColor(STONE_LIGHT);
		graphics.drawLine(thumb.x, thumb.y, thumb.x + thumb.width - 2, thumb.y);
		graphics.drawLine(thumb.x, thumb.y, thumb.x, thumb.y + thumb.height - 2);
		graphics.setColor(FRAME_DARK);
		graphics.drawLine(thumb.x + thumb.width - 1, thumb.y, thumb.x + thumb.width - 1,
			thumb.y + thumb.height - 1);
		graphics.drawLine(thumb.x, thumb.y + thumb.height - 1, thumb.x + thumb.width - 1,
			thumb.y + thumb.height - 1);
	}

	// ---- bottom bar ------------------------------------------------------------------------

	/**
	 * The bank's bottom button row, minus everything a viewer cannot do. Withdraw quantity, noted
	 * mode and insert/swap all describe withdrawals we never make, so the bar carries only the two
	 * controls that mean something here: search, and the tabs / by-storage view toggle.
	 */
	private void drawBottomBar(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle bar = model.bottomBarRect();
		graphics.setColor(PANEL_DARK);
		graphics.fillRect(bar.x, bar.y, bar.width, bar.height);
		graphics.setColor(FRAME_DARK);
		graphics.drawLine(bar.x, bar.y, bar.x + bar.width - 1, bar.y);
		graphics.setColor(FRAME_LIGHT);
		graphics.drawLine(bar.x, bar.y + 1, bar.x + bar.width - 1, bar.y + 1);

		drawSearchButton(graphics, model, local);
		drawSearchField(graphics, model);
		drawAddButton(graphics, model, local);
		drawModeButton(graphics, model, local);
	}

	private void drawSearchButton(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle r = model.searchButtonRect();
		final boolean hovered = local != null && r.contains(local.x, local.y);
		final boolean active = model.isSearchFocused() || !model.getSearch().isEmpty();

		final BufferedImage image = sprite(SpriteID.Bankbuttons.SEARCH);
		if (image != null)
		{
			drawStoneButton(graphics, r, active, hovered);
			drawFitted(graphics, image, r);
			return;
		}

		drawStoneButton(graphics, r, active, hovered);
		graphics.setColor(new Color(0xD8, 0xD8, 0xD0));
		final Stroke old = graphics.getStroke();
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawOval(r.x + 5, r.y + 4, 11, 11);
		graphics.drawLine(r.x + 15, r.y + 14, r.x + 19, r.y + 18);
		graphics.setStroke(old);
	}

	private void drawSearchField(Graphics2D graphics, BankViewModel model)
	{
		final Rectangle r = model.searchRect();
		if (r.width <= 0)
		{
			return;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics fm = graphics.getFontMetrics();
		final int baseline = baseline(fm, r.y, r.height);

		final String search = model.getSearch();
		if (search.isEmpty() && !model.isSearchFocused())
		{
			graphics.setColor(TEXT_DIM);
			graphics.drawString("Search...", r.x + 3, baseline);
			return;
		}

		graphics.setColor(TITLE);
		graphics.drawString(search, r.x + 3, baseline);

		if (model.isSearchFocused())
		{
			final int caretX = r.x + 4 + fm.stringWidth(search);
			graphics.setColor(CARET);
			graphics.drawLine(caretX, r.y + 4, caretX, r.y + r.height - 5);
		}
	}

	/**
	 * The "add an item" button: opens the game's own chatbox item search, whose pick is appended to
	 * the tab on show as a placeholder. Wears the bank's [+] tab glyph, the closest thing the cache
	 * has to an "add" icon.
	 */
	private void drawAddButton(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle r = model.addButtonRect();
		final boolean hovered = local != null && r.contains(local.x, local.y);

		drawStoneButton(graphics, r, controller.isChatboxInputOpen(), hovered);
		drawPlusTabIcon(graphics, r);
	}

	private void drawModeButton(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle r = model.modeButtonRect();
		final boolean hovered = local != null && r.contains(local.x, local.y);
		final boolean byStorage = model.getMode() == ViewMode.BY_STORAGE;

		drawStoneButton(graphics, r, byStorage, hovered);
		drawCentredText(graphics, FontManager.getRunescapeSmallFont(),
			byStorage ? "Storage" : "Tabs", r, TITLE);
	}

	// ---- drag ------------------------------------------------------------------------------

	private void drawDrag(Graphics2D graphics, BankViewModel model, Point local)
	{
		if (!model.isDragging())
		{
			return;
		}

		final int dropIndex = model.getDropSlotIndex();
		if (dropIndex >= 0)
		{
			final Rectangle r = model.slotRect(dropIndex);
			if (r.width > 0)
			{
				graphics.setColor(new Color(CARET.getRed(), CARET.getGreen(), CARET.getBlue(), 90));
				graphics.fillRect(r.x, r.y, r.width, r.height);
			}
		}

		final BankSlot dragged = model.getDragSlot();
		final Point at = local != null ? local : model.getDragPoint();
		if (dragged == null || at == null)
		{
			return;
		}

		drawSprite(graphics, dragged.getCanonicalId(), 1, false,
			at.x - BankGeometry.ITEM_SPRITE_W / 2, at.y - BankGeometry.ITEM_SPRITE_H / 2, 0.6f);
	}

	// ---- context menu ----------------------------------------------------------------------

	private void drawMenu(Graphics2D graphics, BankViewModel model, Point local)
	{
		if (!model.isMenuOpen())
		{
			return;
		}

		final ContextMenu menu = model.getMenu();
		if (menu == null)
		{
			return;
		}

		final Rectangle r = menu.getBounds();
		// Frame first, then the header band and the body punched out of it in black, exactly as the
		// client lays it out: 1px of frame all round, a 16px black header, and the frame showing
		// through as the 1px rule between header and body.
		graphics.setColor(MENU_FRAME);
		graphics.fillRect(r.x, r.y, r.width, r.height);
		graphics.setColor(MENU_BG);
		graphics.fillRect(r.x + 1, r.y + 1, r.width - 2, BankGeometry.MENU_HEADER_H - 2);
		graphics.fillRect(r.x + 1, r.y + BankGeometry.MENU_HEADER_H,
			r.width - 2, r.height - BankGeometry.MENU_HEADER_H - 1);

		final Font old = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeFont());
		final FontMetrics fm = graphics.getFontMetrics();

		// The header title is drawn in the frame's own colour, as the game does.
		final Rectangle header = menu.headerRect();
		graphics.setColor(MENU_FRAME);
		graphics.drawString(ContextMenu.TITLE, header.x + BankGeometry.MENU_TEXT_X - 1,
			baseline(fm, header.y, header.height));

		final List<ContextMenuEntry> entries = menu.getEntries();
		for (int i = 0; i < entries.size(); i++)
		{
			final Rectangle er = menu.entryRect(i);
			if (er == null || er.width <= 0)
			{
				continue;
			}

			if (local != null && er.contains(local.x, local.y))
			{
				graphics.setColor(MENU_HILIGHT);
				graphics.fillRect(er.x, er.y, er.width, er.height);
			}

			final ContextMenuEntry entry = entries.get(i);
			final int tx = er.x + BankGeometry.MENU_TEXT_X - 1;
			final int by = baseline(fm, er.y, er.height);
			drawShadowed(graphics, entry.getLabel(), tx, by, MENU_TEXT);
			if (entry.hasTarget())
			{
				drawShadowed(graphics, entry.getTarget(),
					tx + fm.stringWidth(entry.getLabel() + " "), by, JagexColors.MENU_TARGET);
			}
		}

		graphics.setFont(old);
	}

	/** One string with the game's 1px black drop shadow under it. */
	private void drawShadowed(Graphics2D graphics, String text, int x, int y, Color colour)
	{
		graphics.setColor(MENU_SHADOW);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(colour);
		graphics.drawString(text, x, y);
	}

	// ---- tooltip ---------------------------------------------------------------------------

	/**
	 * Draws our own tooltip at the tracked cursor position, rather than handing text to RuneLite's
	 * {@code TooltipManager}. That manager anchors at {@code client.getMouseCanvasPosition()}, which
	 * only advances on a mouse-moved event that reaches the injected client - and we consume every
	 * such event while the cursor is inside our bounds (registered ahead of the client's own
	 * handler), so the game's notion of the mouse position freezes the instant the cursor enters the
	 * window. Consuming the move is required (it is what stops the game highlighting scenery under
	 * the window), so the tooltip has to be self-drawn instead. Painted last, over everything else.
	 */
	private void drawTooltip(Graphics2D graphics, BankViewModel model, Point local)
	{
		if (local == null || model.isDragging() || model.isMenuOpen())
		{
			return;
		}

		final List<String> lines = tooltipLinesWithPrice(model, local);
		if (lines.isEmpty())
		{
			return;
		}

		graphics.setFont(FontManager.getRunescapeFont());
		final FontMetrics fm = graphics.getFontMetrics();
		final int lineH = fm.getHeight();

		int maxWidth = 0;
		for (String line : lines)
		{
			maxWidth = Math.max(maxWidth, fm.stringWidth(line));
		}

		final int w = TOOLTIP_PAD_X * 2 + maxWidth;
		final int h = TOOLTIP_PAD_Y * 2 + lines.size() * lineH + (lines.size() - 1) * TOOLTIP_LINE_GAP;

		final Point origin = controller.getPosition();
		final int canvasW = client.getCanvasWidth();
		final int canvasH = client.getCanvasHeight();

		int x = local.x + TOOLTIP_CURSOR_DX;
		x = Math.min(x, canvasW - origin.x - w);
		x = Math.max(x, -origin.x);

		int y = local.y + TOOLTIP_CURSOR_DY;
		final int clampedY = Math.min(y, canvasH - origin.y - h);
		if (y - clampedY > TOOLTIP_CURSOR_DY)
		{
			// The downward clamp would have pushed the tooltip up by more than one cursor offset;
			// flip it above the cursor instead of letting it ride the canvas edge next to the pointer.
			y = local.y - TOOLTIP_CURSOR_DY / 2 - h;
		}
		else
		{
			y = clampedY;
		}
		y = Math.max(y, -origin.y);

		graphics.setColor(TOOLTIP_BG);
		graphics.fillRect(x, y, w, h);
		graphics.setColor(TOOLTIP_BORDER);
		graphics.drawRect(x, y, w - 1, h - 1);

		int lineY = y + TOOLTIP_PAD_Y + fm.getAscent();
		for (int i = 0; i < lines.size(); i++)
		{
			graphics.setColor(i == 0 ? JagexColors.DARK_ORANGE_INTERFACE_TEXT : TEXT_DIM);
			graphics.drawString(lines.get(i), x + TOOLTIP_PAD_X, lineY);
			lineY += lineH + TOOLTIP_LINE_GAP;
		}
	}

	/**
	 * {@link BankViewModel#tooltipLines} stays free of formatting (a pure-tier method cannot reach
	 * {@code QuantityFormatter}), so for a slot this inserts the GE price line - or "Placeholder" -
	 * right after the item name here, where the formatter is available.
	 */
	private List<String> tooltipLinesWithPrice(BankViewModel model, Point local)
	{
		final List<String> base = model.tooltipLines(local.x, local.y);
		if (base.isEmpty())
		{
			return base;
		}

		final BankSlot slot = model.slotAt(local.x, local.y);
		if (slot == null)
		{
			return base;
		}

		final List<String> lines = new ArrayList<>();
		lines.add(base.get(0));

		if (slot.isPlaceholder())
		{
			lines.add("Placeholder");
		}
		else
		{
			final int price = model.unitPrice(slot.getCanonicalId());
			if (price > 0)
			{
				final long qty = slot.getQuantity();
				String geLine = "GE: " + QuantityFormatter.formatNumber(qty * price);
				if (qty > 1)
				{
					geLine += " (" + QuantityFormatter.formatNumber(price) + " ea)";
				}
				lines.add(geLine);
			}
		}

		lines.addAll(base.subList(1, base.size()));
		return lines;
	}

	private void drawCentredText(Graphics2D graphics, Font font, String text, Rectangle r, Color color)
	{
		final Font old = graphics.getFont();
		graphics.setFont(font);
		final FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(color);
		graphics.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2,
			baseline(fm, r.y, r.height));
		graphics.setFont(old);
	}

	/** Baseline y that vertically centres a line of this font in a band of {@code height} at {@code y}. */
	private static int baseline(FontMetrics fm, int y, int height)
	{
		return y + (height + fm.getAscent() - fm.getDescent()) / 2;
	}
}
