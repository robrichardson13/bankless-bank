package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Covers the draw-order fallback described in {@link HudButtonOverlay#render}: RuneLite's
 * {@code OverlayManager} sorts by position group before priority, and a DYNAMIC overlay's group
 * always sorts (and so draws) ahead of a snap-corner position group, so no {@code setPriority}
 * value can make {@code BankOverlay} draw after this overlay once they land in the same screen
 * area. This overlay must instead refuse to draw itself over the bank window.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class HudButtonOverlayTest
{
	@Mock private BankViewController controller;
	@Mock private BanklessBankConfig config;

	private BankInputListener listener;
	private HudButtonOverlay hudOverlay;
	private Graphics2D graphics;

	@Before
	public void setUp()
	{
		listener = new BankInputListener(config, controller);
		hudOverlay = new HudButtonOverlay(controller, listener);
		when(controller.isOpen()).thenReturn(false);
		graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
	}

	@Test
	public void drawsNormallyWhenBankIsClosed()
	{
		listener.publish(false, null, false, false);
		hudOverlay.getBounds().setBounds(5, 500, HudButtonOverlay.SIZE, HudButtonOverlay.SIZE);

		Dimension size = hudOverlay.render(graphics);

		assertNotNull("nothing to yield to, so the button draws as usual", size);
	}

	@Test
	public void drawsNormallyWhenBankIsOpenButNotOverlapping()
	{
		listener.publish(true, new Rectangle(400, 0, 400, 300), false, false);
		hudOverlay.getBounds().setBounds(5, 500, HudButtonOverlay.SIZE, HudButtonOverlay.SIZE);

		Dimension size = hudOverlay.render(graphics);

		assertNotNull(size);
	}

	@Test
	public void yieldsToTheBankWindowWhenOpenAndOverlapping()
	{
		// The bank window covers the button's whole corner.
		listener.publish(true, new Rectangle(0, 400, 400, 300), false, false);
		hudOverlay.getBounds().setBounds(5, 500, HudButtonOverlay.SIZE, HudButtonOverlay.SIZE);

		Dimension size = hudOverlay.render(graphics);

		assertNull("must not paint over the bank window it can't be guaranteed to draw under", size);
	}
}
