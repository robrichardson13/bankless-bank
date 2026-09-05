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
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Draws the bank view. Self-positioned: a {@code DYNAMIC} overlay with no preferred location is
 * drawn at whatever {@link #getBounds()} says, so writing the bounds location each frame from the
 * controller is all the positioning we need. Not movable, so RuneLite's alt-drag ignores it and
 * the title bar is the only drag handle.
 */
@Singleton
public class BankOverlay extends Overlay
{
	private static final Color CHROME = new Color(0x3E, 0x35, 0x29);
	private static final Color CHROME_BORDER = new Color(0x5F, 0x54, 0x3F);
	private static final Color TITLE_BG = new Color(0x2E, 0x27, 0x1E);
	private static final Color TEXT = new Color(0xFF, 0x98, 0x1F);
	private static final Color TEXT_DIM = new Color(0xC8, 0xC8, 0xC8);
	private static final Color SLOT_BG = new Color(0x2B, 0x25, 0x1C);
	private static final Color SLOT_BORDER = new Color(0x4A, 0x41, 0x32);
	private static final Color HOVER = new Color(255, 255, 255, 30);
	private static final Color TAB_ACTIVE = new Color(0x5A, 0x4E, 0x3B);
	private static final Color TAB_INACTIVE = new Color(0x33, 0x2C, 0x22);
	private static final Color SEARCH_BG = new Color(0x1E, 0x19, 0x12);
	private static final Color SCROLL_TRACK = new Color(0x22, 0x1D, 0x16);
	private static final Color SCROLL_THUMB = new Color(0x6B, 0x5D, 0x46);
	private static final Color MENU_BG = new Color(0x5D, 0x5D, 0x5D);
	private static final Color MENU_BORDER = new Color(0x2E, 0x2E, 0x2E);
	private static final Color MENU_HILIGHT = new Color(0x8A, 0x8A, 0x8A);
	private static final Color CARET = new Color(0xFF, 0xFF, 0x00);
	private static final Color DROP_TAB = new Color(0xFF, 0xFF, 0x00, 90);

	private static final Rectangle EMPTY = new Rectangle();

	private final Client client;
	private final ItemManager itemManager;
	private final BanklessBankConfig config;
	private final BankViewController controller;
	private final BankInputListener listener;
	private final TooltipManager tooltipManager;

	@Inject
	BankOverlay(Client client, ItemManager itemManager,
		BanklessBankConfig config, BankViewController controller, BankInputListener listener,
		TooltipManager tooltipManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
		this.controller = controller;
		this.listener = listener;
		this.tooltipManager = tooltipManager;

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
		controller.refresh();

		if (!controller.isOpen())
		{
			listener.publish(false, EMPTY, false);
			return null;
		}

		final BankViewModel model = controller.getViewModel();
		model.setPlaceholdersEnabled(config.placeholders());
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

		drawChrome(graphics, model, size);
		drawTitle(graphics, model, size);
		drawTabs(graphics, model, local);
		drawGrid(graphics, model, local);
		drawScrollbar(graphics, model);
		drawSearch(graphics, model);
		drawDrag(graphics, model, local);
		drawMenu(graphics, model, local);
		maybeTooltip(model, local);

		listener.publish(true, new Rectangle(origin.x, origin.y, size.width, size.height),
			client.isKeyPressed(KeyCode.KC_ALT));

		return size;
	}

	// ---- chrome ----------------------------------------------------------------------------

	private void drawChrome(Graphics2D graphics, BankViewModel model, Dimension size)
	{
		graphics.setColor(CHROME);
		graphics.fillRect(0, 0, size.width, size.height);
		graphics.setColor(CHROME_BORDER);
		graphics.drawRect(0, 0, size.width - 1, size.height - 1);
	}

	private void drawTitle(Graphics2D graphics, BankViewModel model, Dimension size)
	{
		final Rectangle title = model.titleBarRect();
		graphics.setColor(TITLE_BG);
		graphics.fillRect(title.x, title.y, title.width, title.height);

		graphics.setFont(FontManager.getRunescapeBoldFont());
		graphics.setColor(TEXT);
		final FontMetrics fm = graphics.getFontMetrics();
		final int baseline = title.y + (title.height + fm.getAscent() - fm.getDescent()) / 2;
		graphics.drawString("Bankless Bank", title.x + 4, baseline);

		final String count = model.getItemCount() + " items";
		final int countWidth = fm.stringWidth(count);
		graphics.setColor(TEXT_DIM);
		graphics.drawString(count, title.x + (title.width - countWidth) / 2, baseline);

		final Rectangle close = model.closeButtonRect();
		graphics.setColor(new Color(0x8B, 0x2A, 0x2A));
		graphics.fillRect(close.x, close.y, close.width, close.height);
		graphics.setColor(Color.WHITE);
		graphics.drawLine(close.x + 4, close.y + 4, close.x + close.width - 5, close.y + close.height - 5);
		graphics.drawLine(close.x + close.width - 5, close.y + 4, close.x + 4, close.y + close.height - 5);
	}

	// ---- tabs ------------------------------------------------------------------------------

	private void drawTabs(Graphics2D graphics, BankViewModel model, Point local)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());

		final int strip = model.getStripLength();
		final int tabCount = model.getTabCount();
		final int active = model.getActiveTab();
		final int dropTab = dropTabIndex(model, local);

		for (int i = 0; i < strip; i++)
		{
			final Rectangle r = model.tabRect(i);
			final boolean isActive = (i == 0 && active == -1) || (i > 0 && i - 1 == active);

			graphics.setColor(isActive ? TAB_ACTIVE : TAB_INACTIVE);
			graphics.fillRect(r.x, r.y, r.width, r.height);
			graphics.setColor(SLOT_BORDER);
			graphics.drawRect(r.x, r.y, r.width - 1, r.height - 1);

			if (i == 0)
			{
				drawCentred(graphics, "All", r, TEXT);
			}
			else if (i - 1 < tabCount)
			{
				final int iconId = model.getTabIconItemId(i - 1);
				if (iconId > 0)
				{
					drawSprite(graphics, iconId, 1, false,
						r.x + (r.width - BankGeometry.ITEM_SPRITE_W) / 2,
						r.y + (r.height - BankGeometry.ITEM_SPRITE_H) / 2, 1f);
				}
				else
				{
					drawCentred(graphics, String.valueOf(i), r, TEXT_DIM);
				}
			}
			else
			{
				drawCentred(graphics, "+", r, TEXT);
			}

			if (i == dropTab)
			{
				graphics.setColor(DROP_TAB);
				graphics.fillRect(r.x, r.y, r.width, r.height);
			}
		}
	}

	/** The strip index the current drag would drop onto, or -1. */
	private int dropTabIndex(BankViewModel model, Point local)
	{
		if (!model.isDragging() || local == null)
		{
			return -1;
		}

		final Hit hit = model.hitTest(local.x, local.y);
		if (hit.getType() == Hit.Type.TAB || hit.getType() == Hit.Type.TAB_PLUS)
		{
			return hit.getIndex();
		}
		return -1;
	}

	// ---- grid ------------------------------------------------------------------------------

	private void drawGrid(Graphics2D graphics, BankViewModel model, Point local)
	{
		final Rectangle grid = model.gridRect();
		final Shape oldClip = graphics.getClip();
		graphics.clip(grid);

		graphics.setColor(SLOT_BG);
		graphics.fillRect(grid.x, grid.y, grid.width, grid.height);

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
				drawHeader(graphics, row, grid.x, y);
				continue;
			}

			for (int i = 0; i < row.getSlots().size(); i++)
			{
				drawSlot(graphics, model, slots.get(flat + i), flat + i, local);
			}
			flat += row.getSlots().size();
		}

		graphics.setClip(oldClip);
		graphics.setColor(SLOT_BORDER);
		graphics.drawRect(grid.x, grid.y, grid.width - 1, grid.height - 1);
	}

	private void drawHeader(Graphics2D graphics, BankRow row, int x, int y)
	{
		final FontMetrics fm = graphics.getFontMetrics();
		final int baseline = y + (row.getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1;

		graphics.setColor(TEXT);
		graphics.drawString(row.getHeaderText() == null ? "" : row.getHeaderText(), x + 3, baseline);

		if (row.getHeaderSubtitle() != null && !row.getHeaderSubtitle().isEmpty())
		{
			final int nameWidth = fm.stringWidth(row.getHeaderText() == null ? "" : row.getHeaderText());
			graphics.setColor(TEXT_DIM);
			graphics.drawString(row.getHeaderSubtitle(), x + 9 + nameWidth, baseline);
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

	private void drawScrollbar(Graphics2D graphics, BankViewModel model)
	{
		final Rectangle track = model.scrollbarRect();
		graphics.setColor(SCROLL_TRACK);
		graphics.fillRect(track.x, track.y, track.width, track.height);

		if (model.getMaxScroll() <= 0)
		{
			return;
		}

		final Rectangle thumb = model.scrollThumbRect();
		graphics.setColor(SCROLL_THUMB);
		graphics.fillRect(thumb.x, thumb.y, thumb.width, thumb.height);
	}

	// ---- search ----------------------------------------------------------------------------

	private void drawSearch(Graphics2D graphics, BankViewModel model)
	{
		final Rectangle r = model.searchRect();
		graphics.setColor(SEARCH_BG);
		graphics.fillRect(r.x, r.y, r.width, r.height);
		graphics.setColor(SLOT_BORDER);
		graphics.drawRect(r.x, r.y, r.width - 1, r.height - 1);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics fm = graphics.getFontMetrics();
		final int baseline = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;

		final String search = model.getSearch();
		if (search == null || search.isEmpty())
		{
			graphics.setColor(TEXT_DIM);
			graphics.drawString("Search...", r.x + 4, baseline);
			return;
		}

		graphics.setColor(TEXT);
		graphics.drawString(search, r.x + 4, baseline);

		if (model.isSearchFocused())
		{
			final int caretX = r.x + 5 + fm.stringWidth(search);
			graphics.drawLine(caretX, r.y + 3, caretX, r.y + r.height - 4);
		}
	}

	// ---- drag ------------------------------------------------------------------------------

	private void drawDrag(Graphics2D graphics, BankViewModel model, Point local)
	{
		if (!model.isDragging())
		{
			return;
		}

		final int caret = model.getDropCaretIndex();
		if (caret >= 0)
		{
			final Rectangle r = model.slotRect(caret);
			if (r.width > 0)
			{
				final Stroke old = graphics.getStroke();
				graphics.setStroke(new BasicStroke(2f));
				graphics.setColor(CARET);
				graphics.drawLine(r.x, r.y, r.x, r.y + r.height);
				graphics.setStroke(old);
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
		graphics.setColor(MENU_BG);
		graphics.fillRect(r.x, r.y, r.width, r.height);
		graphics.setColor(MENU_BORDER);
		graphics.drawRect(r.x, r.y, r.width - 1, r.height - 1);

		final Font old = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics fm = graphics.getFontMetrics();

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

			graphics.setColor(Color.WHITE);
			graphics.drawString(entries.get(i).getLabel(), er.x + 4,
				er.y + (er.height + fm.getAscent() - fm.getDescent()) / 2);
		}

		graphics.setFont(old);
	}

	// ---- tooltip ---------------------------------------------------------------------------

	private void maybeTooltip(BankViewModel model, Point local)
	{
		if (local == null || model.isDragging() || model.isMenuOpen())
		{
			return;
		}

		final List<String> lines = model.tooltipLines(local.x, local.y);
		if (lines == null || lines.isEmpty())
		{
			return;
		}

		tooltipManager.add(new Tooltip(String.join("</br>", lines)));
	}

	private void drawCentred(Graphics2D graphics, String text, Rectangle r, Color color)
	{
		final FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(color);
		graphics.drawString(text,
			r.x + (r.width - fm.stringWidth(text)) / 2,
			r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
	}
}
