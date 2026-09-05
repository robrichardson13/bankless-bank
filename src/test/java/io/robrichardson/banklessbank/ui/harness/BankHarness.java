package io.robrichardson.banklessbank.ui.harness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import com.google.gson.Gson;
import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.LayoutStore;
import io.robrichardson.banklessbank.tracking.StorageManagerManager;
import io.robrichardson.banklessbank.ui.BankGeometry;
import io.robrichardson.banklessbank.ui.BankInputListener;
import io.robrichardson.banklessbank.ui.BankOverlay;
import io.robrichardson.banklessbank.ui.BankViewController;
import io.robrichardson.banklessbank.ui.BankViewModel;
import io.robrichardson.banklessbank.ui.HudButtonOverlay;
import io.robrichardson.banklessbank.ui.UiHarnessParts;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/**
 * Wires the bank view exactly as {@code BanklessBankPlugin} does, against mocks instead of a game
 * client, and renders it to a {@link BufferedImage} the size of a fixed-mode RuneLite canvas.
 *
 * <p>A "frame" here is {@link #render()}, which is the real render path: the overlay itself calls
 * {@code drainActions()}, {@code refresh()} and {@code rebuild()}. Input goes in through
 * {@link BankInputListener} as synthetic AWT events, which post work onto the controller's queue,
 * so a mutation only takes effect on the next frame - same as in the client.
 */
public final class BankHarness
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	public static final int CANVAS_W = 765;
	public static final int CANVAS_H = 503;
	public static final String PROFILE_KEY = "harness-profile";

	private static final Component EVENT_SOURCE = new Component()
	{
	};

	private final FakeStorages fixture = FakeStorages.uim();
	private final FakeItemSprites sprites;

	private final BanklessBankPlugin plugin = Mockito.mock(BanklessBankPlugin.class);
	private final StorageManagerManager storageManagerManager = Mockito.mock(StorageManagerManager.class);
	private final Client client = Mockito.mock(Client.class);
	private final ItemManager itemManager = Mockito.mock(ItemManager.class);
	private final ConfigManager configManager = Mockito.mock(ConfigManager.class);
	private final BanklessBankConfig config = Mockito.mock(BanklessBankConfig.class);
	private final TooltipManager tooltipManager = new TooltipManager();

	private final Map<String, String> configStore = new HashMap<>();

	private final BankViewController controller;
	private final BankInputListener listener;
	private final BankOverlay bankOverlay;
	private final HudButtonOverlay hudOverlay;

	private boolean storagesDirty;
	private boolean altHeld;

	/** Painted once, then used as the reference for "is this pixel still background?". */
	private final BufferedImage backgroundOnly;

	private BufferedImage lastFrame;
	private Dimension lastRenderedSize;

	public BankHarness()
	{
		sprites = new FakeItemSprites(fixture.names());

		stubConfig();
		stubClient();
		stubItemManager();
		stubConfigManager();
		stubPlugin();

		final LayoutStore layoutStore = new LayoutStore(configManager, new Gson());
		layoutStore.save(PROFILE_KEY, FakeStorages.savedLayout());

		controller = UiHarnessParts.controller(plugin, client, itemManager, configManager, config, layoutStore);
		listener = UiHarnessParts.listener(config, controller);
		bankOverlay = UiHarnessParts.bankOverlay(client, itemManager, config, controller, listener, tooltipManager);
		hudOverlay = UiHarnessParts.hudOverlay(controller, listener);

		controller.startUp();

		backgroundOnly = newCanvas();
	}

	// ---- mocks -----------------------------------------------------------------------------

	private void stubConfig()
	{
		Mockito.when(config.placeholders()).thenReturn(true);
		Mockito.when(config.showEmptyStorages()).thenReturn(false);
		Mockito.when(config.showHudButton()).thenReturn(true);
		Mockito.when(config.toggleKeybind()).thenReturn(Keybind.NOT_SET);
	}

	private void stubClient()
	{
		Mockito.when(client.getCanvasWidth()).thenReturn(CANVAS_W);
		Mockito.when(client.getCanvasHeight()).thenReturn(CANVAS_H);
		Mockito.when(client.isKeyPressed(anyInt())).thenAnswer(inv -> altHeld);
	}

	private void stubItemManager()
	{
		Mockito.when(itemManager.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
		Mockito.when(itemManager.getImage(anyInt(), anyInt(), Mockito.anyBoolean()))
			.thenAnswer(inv -> sprites.get(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
		Mockito.when(itemManager.getItemComposition(anyInt())).thenAnswer(inv ->
		{
			final int id = inv.getArgument(0);
			final ItemComposition composition = Mockito.mock(ItemComposition.class);
			Mockito.when(composition.getName()).thenReturn(fixture.names().getOrDefault(id, "Item " + id));
			return composition;
		});
	}

	private void stubConfigManager()
	{
		Mockito.when(configManager.getRSProfileKey()).thenReturn(PROFILE_KEY);

		Mockito.when(configManager.getConfiguration(anyString(), anyString()))
			.thenAnswer(inv -> configStore.get(key(inv.getArgument(0), null, inv.getArgument(1))));

		Mockito.when(configManager.getConfiguration(anyString(), anyString(), anyString()))
			.thenAnswer(inv -> configStore.get(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))));

		final Answer<Object> typed = inv ->
		{
			final String raw = configStore.get(key(inv.getArgument(0), null, inv.getArgument(1)));
			if (raw == null)
			{
				return null;
			}
			final Type type = inv.getArgument(2);
			if (type == int.class || type == Integer.class)
			{
				return Integer.valueOf(raw);
			}
			if (type == boolean.class || type == Boolean.class)
			{
				return Boolean.valueOf(raw);
			}
			return raw;
		};
		Mockito.when(configManager.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(typed);

		Mockito.doAnswer(inv ->
		{
			configStore.put(key(inv.getArgument(0), null, inv.getArgument(1)), String.valueOf((Object) inv.getArgument(2)));
			return null;
		}).when(configManager).setConfiguration(anyString(), anyString(), any());

		Mockito.doAnswer(inv ->
		{
			configStore.put(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)),
				String.valueOf((Object) inv.getArgument(3)));
			return null;
		}).when(configManager).setConfiguration(anyString(), anyString(), anyString(), anyString());
	}

	private static String key(String group, String profile, String name)
	{
		return group + "|" + profile + "|" + name;
	}

	private void stubPlugin()
	{
		Mockito.when(plugin.getStorageManagerManager()).thenReturn(storageManagerManager);
		Mockito.when(plugin.getLoadedProfileKey()).thenReturn(PROFILE_KEY);
		Mockito.when(plugin.isStoragesDirty()).thenAnswer(inv -> storagesDirty);
		Mockito.doAnswer(inv ->
		{
			storagesDirty = false;
			return null;
		}).when(plugin).clearStoragesDirty();

		Mockito.doReturn(Collections.emptyList()).when(storageManagerManager).getStorageManagers();
		Mockito.doAnswer(inv -> fixture.storages()).when(storageManagerManager).getViewableStorages();
	}

	// ---- accessors -------------------------------------------------------------------------

	public BankViewController controller()
	{
		return controller;
	}

	public BankViewModel model()
	{
		return controller.getViewModel();
	}

	public BankInputListener listener()
	{
		return listener;
	}

	public BankLayout layout()
	{
		return controller.getViewModel().getLayout();
	}

	public FakeStorages fixture()
	{
		return fixture;
	}

	public List<Tooltip> tooltips()
	{
		return tooltipManager.getTooltips();
	}

	public void clearTooltips()
	{
		tooltipManager.clear();
	}

	public BufferedImage lastFrame()
	{
		return lastFrame;
	}

	public Dimension lastRenderedSize()
	{
		return lastRenderedSize;
	}

	/** Overlay top-left in canvas coordinates. */
	public Point origin()
	{
		return controller.getPosition();
	}

	public void setAltHeld(boolean held)
	{
		altHeld = held;
	}

	/** Marks the tracking layer's snapshots stale, so the next frame rebuilds them. */
	public void markStoragesDirty()
	{
		storagesDirty = true;
	}

	// ---- rendering -------------------------------------------------------------------------

	private BufferedImage newCanvas()
	{
		final BufferedImage image = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = image.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(0x24, 0x2B, 0x1E), 0, CANVAS_H, new Color(0x0D, 0x10, 0x0B)));
		g.fillRect(0, 0, CANVAS_W, CANVAS_H);

		// A few flat shapes so the stand-in scene is not a single colour and overlay edges are legible.
		g.setColor(new Color(0x1A, 0x22, 0x16));
		g.fillRect(0, CANVAS_H - 120, CANVAS_W, 120);
		g.setColor(new Color(0x2C, 0x35, 0x24));
		g.fillRect(CANVAS_W - 250, 0, 250, CANVAS_H);
		g.dispose();
		return image;
	}

	/** One frame: HUD button, then the bank window. Returns the bank overlay's reported size. */
	public Dimension render()
	{
		final BufferedImage frame = newCanvas();
		final Graphics2D g = frame.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		renderHud(g);

		final Graphics2D bankGraphics = (Graphics2D) g.create();
		lastRenderedSize = renderBank(bankGraphics);
		bankGraphics.dispose();

		g.dispose();
		lastFrame = frame;
		return lastRenderedSize;
	}

	/**
	 * {@code HudButtonOverlay} draws at the graphics origin and publishes {@code getBounds()} as its
	 * canvas rect, which {@code OverlayRenderer} would have positioned for us. Stand in for the
	 * renderer by placing the bounds at the BOTTOM_LEFT slot and translating to match.
	 */
	private void renderHud(Graphics2D g)
	{
		final int x = 5;
		final int y = CANVAS_H - HudButtonOverlay.SIZE - 5;
		hudOverlay.getBounds().setBounds(x, y, HudButtonOverlay.SIZE, HudButtonOverlay.SIZE);

		final Graphics2D hudGraphics = (Graphics2D) g.create();
		hudGraphics.translate(x, y);
		hudOverlay.render(hudGraphics);
		hudGraphics.dispose();
	}

	private Dimension renderBank(Graphics2D g)
	{
		// Stand in for OverlayRenderer, which translates the graphics to the overlay's bounds
		// location (last frame's value) before calling render. BankOverlay then corrects that
		// translation to wherever the controller says the window is this frame.
		final Rectangle drawnAt = bankOverlay.getBounds();
		g.translate(drawnAt.x, drawnAt.y);
		return bankOverlay.render(g);
	}

	/** Renders {@code frames} times, so posted actions land and the next frame draws their effect. */
	public Dimension render(int frames)
	{
		Dimension size = null;
		for (int i = 0; i < frames; i++)
		{
			size = render();
		}
		return size;
	}

	// ---- screenshots -----------------------------------------------------------------------

	public List<Path> writePng(String name, List<Path> directories) throws IOException
	{
		final List<Path> written = new ArrayList<>();
		for (Path dir : directories)
		{
			Files.createDirectories(dir);
			final Path out = dir.resolve(name + ".png");
			ImageIO.write(lastFrame, "png", out.toFile());
			written.add(out);
		}
		return written;
	}

	public static File projectDir()
	{
		return new File(System.getProperty("user.dir"));
	}

	// ---- pixel helpers ---------------------------------------------------------------------

	/** Count of pixels in {@code rect} that differ from the untouched background. */
	public int nonBackgroundPixels(Rectangle rect)
	{
		int count = 0;
		final Rectangle clipped = rect.intersection(new Rectangle(0, 0, CANVAS_W, CANVAS_H));
		for (int y = clipped.y; y < clipped.y + clipped.height; y++)
		{
			for (int x = clipped.x; x < clipped.x + clipped.width; x++)
			{
				if (lastFrame.getRGB(x, y) != backgroundOnly.getRGB(x, y))
				{
					count++;
				}
			}
		}
		return count;
	}

	/** Distinct colours in a canvas rect; a drawn item sprite has many, an empty slot has few. */
	public int distinctColours(Rectangle rect)
	{
		final java.util.Set<Integer> seen = new java.util.HashSet<>();
		final Rectangle clipped = rect.intersection(new Rectangle(0, 0, CANVAS_W, CANVAS_H));
		for (int y = clipped.y; y < clipped.y + clipped.height; y++)
		{
			for (int x = clipped.x; x < clipped.x + clipped.width; x++)
			{
				seen.add(lastFrame.getRGB(x, y));
			}
		}
		return seen.size();
	}

	/** Draws a caption block onto the last frame, for scenes whose evidence is not pixels. */
	public void annotate(String title, List<String> lines)
	{
		final Graphics2D g = lastFrame.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));
		final int height = 16 * (lines.size() + 1) + 8;
		g.setColor(new Color(0, 0, 0, 200));
		g.fillRect(4, 4, 330, height);
		g.setColor(new Color(0xFF, 0x98, 0x1F));
		g.drawString(title, 10, 20);
		g.setColor(Color.WHITE);
		for (int i = 0; i < lines.size(); i++)
		{
			g.drawString(lines.get(i), 10, 36 + i * 16);
		}
		g.dispose();
	}

	// ---- geometry helpers ------------------------------------------------------------------

	/** Canvas point for an overlay-local point. */
	public Point canvas(int localX, int localY)
	{
		final Point o = origin();
		return new Point(o.x + localX, o.y + localY);
	}

	/** Canvas rect for the flat slot index, as the model currently lays it out. */
	public Rectangle slotRectOnCanvas(int flatIndex)
	{
		final Rectangle r = model().slotRect(flatIndex);
		final Point o = origin();
		return new Rectangle(o.x + r.x, o.y + r.y, r.width, r.height);
	}

	public Rectangle rectOnCanvas(Rectangle local)
	{
		final Point o = origin();
		return new Rectangle(o.x + local.x, o.y + local.y, local.width, local.height);
	}

	public Point centreOfSlot(int flatIndex)
	{
		final Rectangle r = slotRectOnCanvas(flatIndex);
		return new Point(r.x + r.width / 2, r.y + r.height / 2);
	}

	/** Canvas rect of the HUD button, as published to the listener. */
	public Rectangle hudRect()
	{
		return new Rectangle(hudOverlay.getBounds());
	}

	// ---- synthetic input -------------------------------------------------------------------

	private static MouseEvent mouse(int id, int button, int x, int y)
	{
		final int modifiers = button == MouseEvent.BUTTON3
			? MouseEvent.BUTTON3_DOWN_MASK : MouseEvent.BUTTON1_DOWN_MASK;
		return new MouseEvent(EVENT_SOURCE, id, System.currentTimeMillis(), modifiers, x, y, 1,
			button == MouseEvent.BUTTON3, button);
	}

	public MouseEvent moveTo(int x, int y)
	{
		return listener.mouseMoved(mouse(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, x, y));
	}

	public MouseEvent moveTo(Point p)
	{
		return moveTo(p.x, p.y);
	}

	public MouseEvent press(int x, int y)
	{
		return listener.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, x, y));
	}

	public MouseEvent release(int x, int y)
	{
		return listener.mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, x, y));
	}

	public MouseEvent drag(int x, int y)
	{
		return listener.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1, x, y));
	}

	public MouseEvent rightPress(int x, int y)
	{
		return listener.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, x, y));
	}

	/** Full left click: move, press, release, click. Returns the press event. */
	public MouseEvent click(int x, int y)
	{
		moveTo(x, y);
		final MouseEvent pressed = press(x, y);
		listener.mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, x, y));
		listener.mouseClicked(mouse(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, x, y));
		return pressed;
	}

	public MouseEvent click(Point p)
	{
		return click(p.x, p.y);
	}

	public MouseWheelEvent wheel(int x, int y, int rotation)
	{
		final MouseWheelEvent e = new MouseWheelEvent(EVENT_SOURCE, MouseEvent.MOUSE_WHEEL,
			System.currentTimeMillis(), 0, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
			1, rotation);
		return listener.mouseWheelMoved(e);
	}

	public void type(String text)
	{
		for (char c : text.toCharArray())
		{
			listener.keyTyped(new KeyEvent(EVENT_SOURCE, KeyEvent.KEY_TYPED, System.currentTimeMillis(),
				0, KeyEvent.VK_UNDEFINED, c));
		}
	}

	public KeyEvent keyPress(int keyCode)
	{
		final KeyEvent e = new KeyEvent(EVENT_SOURCE, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
			0, keyCode, KeyEvent.CHAR_UNDEFINED);
		listener.keyPressed(e);
		return e;
	}

	// ---- scene helpers ---------------------------------------------------------------------

	/** Clicks the HUD button, which is how a player without a hotkey opens the view. */
	public void openViaHudButton()
	{
		final Rectangle hud = hudRect();
		click(hud.x + hud.width / 2, hud.y + hud.height / 2);
	}

	/** Clicks a tab-strip entry: 0 = [All], 1 = main tab, 2.. = custom tabs. */
	public void clickTab(int stripIndex)
	{
		click(rectCentre(rectOnCanvas(BankGeometry.tabAt(stripIndex))));
	}

	public void clickSearchBox()
	{
		click(rectCentre(rectOnCanvas(model().searchRect())));
	}

	private static Point rectCentre(Rectangle r)
	{
		return new Point(r.x + r.width / 2, r.y + r.height / 2);
	}
}
