package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class HudButtonOverlayTest
{
	private static final Color BACKGROUND = new Color(0x10, 0x20, 0x30);
	private static final Color GLYPH = new Color(0xFF, 0x98, 0x1F);
	private static final Color ICON = new Color(0xC0, 0x00, 0x00);
	private static final Component SOURCE = new Component()
	{
	};

	@Mock private BankViewController controller;
	@Mock private BanklessBankConfig config;
	@Mock private ItemManager itemManager;
	@Mock private ClientThread clientThread;

	private BankInputListener listener;
	private HudButtonOverlay hudOverlay;

	@Before
	public void setUp()
	{
		listener = new BankInputListener(config, controller);
		hudOverlay = new HudButtonOverlay(controller, listener, config, itemManager);
		when(config.hudSize()).thenReturn(HudButtonOverlay.DEFAULT_SIZE);
		when(config.hudBackgroundColor()).thenReturn(BACKGROUND);
		when(config.hudBorderColor()).thenReturn(BACKGROUND);
		when(config.hudGlyphColor()).thenReturn(GLYPH);
		listener.publish(false, null, false, false);
	}

	@Test
	public void drawsBeneathWidgetsSoChatAndPrivateMessagesStayOnTop()
	{
		// UNDER_WIDGETS would also sit beneath chat, but OverlayManager promotes it to ABOVE_WIDGETS
		// as soon as the player alt-drags the button off its snap corner.
		assertEquals(OverlayLayer.ABOVE_SCENE, hudOverlay.getLayer());
	}

	@Test
	public void sizeSettingSetsBothTheDrawnSizeAndTheClickTarget()
	{
		when(config.hudSize()).thenReturn(48);
		hudOverlay.getBounds().setLocation(5, 500);

		Dimension size = render(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
		MouseEvent press = listener.mousePressed(press(5 + 45, 500 + 45));

		assertEquals(new Dimension(48, 48), size);
		assertTrue("a click near the far corner of a 48px button lands on it", press.isConsumed());
		verify(controller).toggle();
	}

	@Test
	public void iconItemReplacesTheBankBoothGlyph()
	{
		when(config.hudIconItemId()).thenReturn(995);
		when(itemManager.getImage(995)).thenReturn(solidSprite(ICON));

		BufferedImage canvas = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
		render(canvas);

		assertEquals(ICON.getRGB(), canvas.getRGB(15, 15));
		assertNotEquals("no booth counter drawn under the icon", GLYPH.getRGB(), canvas.getRGB(15, 20));
	}

	@Test
	public void withoutAnIconItemDrawsTheBankBoothInItsConfiguredColour()
	{
		BufferedImage canvas = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
		render(canvas);

		// The booth's counter is a filled bar across y = 19..21.
		assertEquals(GLYPH.getRGB(), canvas.getRGB(15, 20));
		assertEquals(BACKGROUND.getRGB(), canvas.getRGB(15, 2));
	}

	private Dimension render(BufferedImage canvas)
	{
		final Graphics2D graphics = canvas.createGraphics();
		try
		{
			return hudOverlay.render(graphics);
		}
		finally
		{
			graphics.dispose();
		}
	}

	private AsyncBufferedImage solidSprite(Color color)
	{
		final AsyncBufferedImage sprite = new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = sprite.createGraphics();
		graphics.setColor(color);
		graphics.fillRect(0, 0, 36, 32);
		graphics.dispose();
		return sprite;
	}

	private static MouseEvent press(int x, int y)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
			MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
	}
}
