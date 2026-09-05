package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Covers the four fix-6-input-listener event-consumption findings in {@link BankInputListener}.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class BankInputListenerTest
{
	private static final Component SOURCE = new Component()
	{
	};

	@Mock private BanklessBankConfig config;
	@Mock private BankViewController controller;

	private BankInputListener listener;

	@Before
	public void setUp()
	{
		listener = new BankInputListener(config, controller);
	}

	private static MouseEvent mouseEvent(int id, int button, int x, int y)
	{
		int modifiers = button == MouseEvent.BUTTON3 ? MouseEvent.BUTTON3_DOWN_MASK : MouseEvent.BUTTON1_DOWN_MASK;
		return new MouseEvent(SOURCE, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button);
	}

	@Test
	public void altHeldClickOutsideWhileOpenIsNotConsumedAndDoesNotToggle()
	{
		// view open, window somewhere the click is not, alt held
		listener.publish(true, new Rectangle(0, 0, 400, 300), true);
		listener.publishHud(false, null);

		MouseEvent press = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 900, 900);
		MouseEvent result = listener.mousePressed(press);

		assertFalse(result.isConsumed());
		verify(controller, never()).toggle();
	}

	@Test
	public void hudClickConsumesPressReleaseAndTheFollowingClick()
	{
		// view closed, HUD button visible at a known rectangle
		listener.publish(false, null, false);
		Rectangle hud = new Rectangle(10, 10, 20, 20);
		listener.publishHud(true, hud);

		int x = 15;
		int y = 15;

		MouseEvent press = listener.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, x, y));
		MouseEvent release = listener.mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, x, y));
		MouseEvent clicked = listener.mouseClicked(mouseEvent(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, x, y));

		assertTrue(press.isConsumed());
		assertTrue(release.isConsumed());
		assertTrue(clicked.isConsumed());
	}

	@Test
	public void rightButtonDragWithNoPrecedingPressIsNotConsumed()
	{
		listener.publish(true, new Rectangle(0, 0, 400, 300), false);

		MouseEvent drag = mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON3, 100, 100);
		MouseEvent result = listener.mouseDragged(drag);

		assertFalse(result.isConsumed());
	}

	@Test
	public void keyTypedIgnoresStaleOpenFlagWhenControllerIsClosed()
	{
		// last published open was true, but the controller has synchronously closed since
		listener.publish(true, new Rectangle(0, 0, 400, 300), false);
		org.mockito.Mockito.when(controller.isOpen()).thenReturn(false);

		KeyEvent typed = new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
			KeyEvent.VK_UNDEFINED, 'b');

		listener.keyTyped(typed);

		assertFalse(typed.isConsumed());
		verify(controller, never()).post(any());
	}
}
