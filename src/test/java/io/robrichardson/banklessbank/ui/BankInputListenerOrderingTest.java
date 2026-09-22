package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.inject.Guice;
import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins the mouse-listener registration order that {@code BanklessBankPlugin.startUp} relies on.
 *
 * <p>RuneLite's Stretched Mode plugin registers a listener at position 0 that replaces every event
 * with a copy translated from stretched screen pixels to real canvas pixels. Core plugins start
 * before hub plugins, so a hub plugin that also inserts at position 0 lands <em>ahead</em> of that
 * translator and sees raw screen coordinates, while every rect it hit-tests against is in canvas
 * space. That is exactly what made the HUD button and the open bank window click straight through
 * on a stretched client while RuneLite's own overlay hover (appended, so behind the translator)
 * still worked. Appending puts us behind the translator in either start order.
 */
public class BankInputListenerOrderingTest
{
	private static final Component SOURCE = new Component()
	{
	};

	/** Stretched 2x: the canvas is drawn at twice its real size. */
	private static final double SCALE = 2.0;

	private BankViewController controller;
	private BankInputListener listener;
	private MouseManager mouseManager;

	@Before
	public void setUp()
	{
		controller = mock(BankViewController.class);
		listener = new BankInputListener(mock(BanklessBankConfig.class), controller);

		// MouseManager's constructor is @Inject-private; Guice builds it the way RuneLite does.
		final RuneLiteConfig runeLiteConfig = mock(RuneLiteConfig.class);
		mouseManager = Guice.createInjector(binder -> binder.bind(RuneLiteConfig.class).toInstance(runeLiteConfig))
			.getInstance(MouseManager.class);

		// Stretched Mode started first (core plugin) and took position 0.
		mouseManager.registerMouseListener(0, new StretchedTranslator());
		mouseManager.registerMouseWheelListener(0, new StretchedWheelTranslator());

		// HUD button at canvas (10,10)-(40,40); the bank is closed.
		listener.publish(false, null, false, false);
		listener.publishHud(true, new Rectangle(10, 10, HudButtonOverlay.SIZE, HudButtonOverlay.SIZE));
	}

	private static MouseEvent press(int screenX, int screenY)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
			MouseEvent.BUTTON1_DOWN_MASK, screenX, screenY, 1, false, MouseEvent.BUTTON1);
	}

	@Test
	public void appendedListenerSeesTranslatedCoordinatesAndConsumesTheHudClick()
	{
		// What the plugin does now.
		mouseManager.registerMouseListener(listener);

		// Canvas (20,20) on a 2x stretched screen arrives from AWT as (40,40).
		final MouseEvent result = mouseManager.processMousePressed(press(40, 40));

		assertTrue(result.isConsumed());
		verify(controller).toggle();
	}

	@Test
	public void listenerInsertedAtPositionZeroMissesTheHudAndTheClickReachesTheGame()
	{
		// The old registration: ahead of the translator, so the raw (40,40) is tested against a
		// rect that ends at 40 and misses. Documents the failure mode the appended order fixes.
		mouseManager.registerMouseListener(0, listener);

		final MouseEvent result = mouseManager.processMousePressed(press(40, 40));

		assertFalse(result.isConsumed());
		verify(controller, never()).toggle();
	}

	@Test
	public void appendedWheelListenerSeesTranslatedCoordinates()
	{
		final BankViewModel model = new BankViewModel();
		org.mockito.Mockito.when(controller.getViewModel()).thenReturn(model);
		listener.publish(true, new Rectangle(0, 0, 100, 100), false, false);
		mouseManager.registerMouseWheelListener(listener);

		// Canvas (50,50) arrives as (100,100), which is outside the bank rect until translated.
		final MouseWheelEvent wheel = new MouseWheelEvent(SOURCE, MouseEvent.MOUSE_WHEEL,
			System.currentTimeMillis(), 0, 100, 100, 1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1);
		final MouseWheelEvent result = mouseManager.processMouseWheelMoved(wheel);

		assertTrue(result.isConsumed());
	}

	/** Mirrors {@code net.runelite.client.plugins.stretchedmode.TranslateMouseListener}. */
	private static final class StretchedTranslator implements MouseListener
	{
		private static MouseEvent translate(MouseEvent e)
		{
			final MouseEvent out = new MouseEvent((Component) e.getSource(), e.getID(), e.getWhen(),
				e.getModifiersEx(), (int) (e.getX() / SCALE), (int) (e.getY() / SCALE), e.getClickCount(),
				e.isPopupTrigger(), e.getButton());
			if (e.isConsumed())
			{
				out.consume();
			}
			return out;
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent e)
		{
			return translate(e);
		}

		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			return translate(e);
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			return translate(e);
		}

		@Override
		public MouseEvent mouseEntered(MouseEvent e)
		{
			return translate(e);
		}

		@Override
		public MouseEvent mouseExited(MouseEvent e)
		{
			return translate(e);
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent e)
		{
			return translate(e);
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent e)
		{
			return translate(e);
		}
	}

	/** Mirrors {@code net.runelite.client.plugins.stretchedmode.TranslateMouseWheelListener}. */
	private static final class StretchedWheelTranslator implements MouseWheelListener
	{
		@Override
		public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
		{
			final MouseWheelEvent out = new MouseWheelEvent((Component) e.getSource(), e.getID(), e.getWhen(),
				e.getModifiersEx(), (int) (e.getX() / SCALE), (int) (e.getY() / SCALE), e.getClickCount(),
				e.isPopupTrigger(), e.getScrollType(), e.getScrollAmount(), e.getWheelRotation());
			if (e.isConsumed())
			{
				out.consume();
			}
			return out;
		}
	}
}
