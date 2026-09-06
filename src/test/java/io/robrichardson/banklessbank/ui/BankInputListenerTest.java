package io.robrichardson.banklessbank.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.robrichardson.banklessbank.BanklessBankConfig;
import io.robrichardson.banklessbank.model.BankLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
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
		listener.publish(true, new Rectangle(0, 0, 400, 300), true, false);
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
		listener.publish(false, null, false, false);
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

	/**
	 * The bank window and the HUD button's own bounds can overlap (the button is movable and
	 * snappable; the window can be dragged over it). {@code mousePressed} must check {@code inside}
	 * (the bank) before {@code overHud} (the button), so a click in the overlap always lands on the
	 * bank and never toggles the window closed via the HUD button's handler underneath it.
	 */
	@Test
	public void clickInOverlapBetweenBankAndHudGoesToTheBankNotTheHud()
	{
		final BankViewModel model = new BankViewModel();
		when(controller.getViewModel()).thenReturn(model);
		when(controller.isOpen()).thenReturn(true);

		final Rectangle bankBounds = new Rectangle(0, 400, 400, 300);
		listener.publish(true, bankBounds, false, false);
		// Simulates the HUD button still reporting itself visible at a rect that overlaps the bank
		// window - e.g. before HudButtonOverlay's own draw-order fallback kicks in for a frame.
		listener.publishHud(true, new Rectangle(5, 500, HudButtonOverlay.SIZE, HudButtonOverlay.SIZE));

		final int x = 10;
		final int y = 505;

		MouseEvent press = listener.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, x, y));

		assertTrue("the click is inside the bank window, so the bank must handle it", press.isConsumed());
		verify(controller, never()).toggle();
	}

	@Test
	public void rightButtonDragWithNoPrecedingPressIsNotConsumed()
	{
		listener.publish(true, new Rectangle(0, 0, 400, 300), false, false);

		MouseEvent drag = mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON3, 100, 100);
		MouseEvent result = listener.mouseDragged(drag);

		assertFalse(result.isConsumed());
	}

	@Test
	public void keyTypedIgnoresStaleOpenFlagWhenControllerIsClosed()
	{
		// last published open was true, but the controller has synchronously closed since
		listener.publish(true, new Rectangle(0, 0, 400, 300), false, false);
		org.mockito.Mockito.when(controller.isOpen()).thenReturn(false);

		KeyEvent typed = new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
			KeyEvent.VK_UNDEFINED, 'b');

		listener.keyTyped(typed);

		assertFalse(typed.isConsumed());
		verify(controller, never()).post(any());
	}

	@Test
	public void outsidePressWithAnOpenMenuDismissesItAndIsConsumed()
	{
		listener.publish(true, new Rectangle(0, 0, 400, 300), false, true);
		listener.publishHud(false, null);

		MouseEvent press = listener.mousePressed(
			mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 900, 900));

		assertTrue(press.isConsumed());
		verify(controller).post(any());
	}

	@Test
	public void outsidePressWithNoMenuOpenIsNotConsumed()
	{
		listener.publish(true, new Rectangle(0, 0, 400, 300), false, false);
		listener.publishHud(false, null);

		MouseEvent press = listener.mousePressed(
			mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 900, 900));

		assertFalse("nothing to dismiss, so the click belongs to the game", press.isConsumed());
	}

	@Test
	public void keyTypedIsConsumedAndPostedOnlyWhenSearchIsFocused()
	{
		listener.publish(true, new Rectangle(0, 0, 400, 300), false, false);
		org.mockito.Mockito.when(controller.isOpen()).thenReturn(true);
		org.mockito.Mockito.when(controller.isSearchFocused()).thenReturn(false);

		KeyEvent unfocused = new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
			KeyEvent.VK_UNDEFINED, 'a');
		listener.keyTyped(unfocused);

		assertFalse(unfocused.isConsumed());
		verify(controller, never()).post(any());

		org.mockito.Mockito.when(controller.isSearchFocused()).thenReturn(true);
		KeyEvent focused = new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
			KeyEvent.VK_UNDEFINED, 'a');
		listener.keyTyped(focused);

		assertTrue(focused.isConsumed());
		verify(controller).post(any());
	}

	@Test
	public void backspaceIsConsumedOnlyWhenSearchIsFocused()
	{
		listener.publish(true, new Rectangle(0, 0, 400, 300), false, false);
		org.mockito.Mockito.when(controller.isOpen()).thenReturn(true);
		org.mockito.Mockito.when(controller.isSearchFocused()).thenReturn(false);

		KeyEvent unfocused = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
			KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED);
		listener.keyPressed(unfocused);

		assertFalse(unfocused.isConsumed());
		verify(controller, never()).post(any());

		org.mockito.Mockito.when(controller.isSearchFocused()).thenReturn(true);
		KeyEvent focused = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
			KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED);
		listener.keyPressed(focused);

		assertTrue(focused.isConsumed());
		verify(controller).post(any());
	}

	/**
	 * Ending a resize drag must not read the view model from the AWT thread: {@code saveViewState}
	 * reads {@code visibleRows}, {@code mode} and {@code activeTab}, none of which are volatile, so
	 * the call has to go through the controller's queue like every other mutation.
	 */
	@Test
	public void endingAResizeDragPostsTheViewStateSaveInsteadOfRunningItOnTheAwtThread()
	{
		final Deque<Runnable> queue = new ArrayDeque<>();
		doAnswer(inv ->
		{
			queue.add(inv.getArgument(0));
			return null;
		}).when(controller).post(any(Runnable.class));

		final BankViewModel model = new BankViewModel();
		when(controller.getViewModel()).thenReturn(model);
		when(controller.isOpen()).thenReturn(true);

		final Rectangle grip = model.resizeGripRect();
		final Rectangle bounds = new Rectangle(0, 0, model.size().width, model.size().height);
		listener.publish(true, bounds, false, false);
		listener.publishHud(false, null);

		final int gx = grip.x + grip.width / 2;
		final int gy = grip.y + grip.height / 2;

		listener.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, gx, gy));
		drain(queue);

		listener.mouseDragged(mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1, gx, gy + 72));
		drain(queue);

		listener.mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, gx, gy + 72));

		verify(controller, never()).saveViewState();

		drain(queue);

		verify(controller).saveViewState();
	}

	/**
	 * The drag flags are armed on the client thread inside the runnable {@link
	 * BankInputListener#mousePressed} posts, but cleared on the AWT thread when the button comes up.
	 * A press-and-release on the scrollbar thumb that completes before the queue drains therefore
	 * leaves {@code thumbDrag} set after the AWT-side state has already been cleared. Every other
	 * drag branch is additionally guarded by an AWT-only field, so only this one could go on to
	 * swallow an unrelated drag in the game world and scroll our list.
	 */
	@Test
	public void aStaleScrollThumbFlagDoesNotSwallowALaterWorldDrag()
	{
		final Deque<Runnable> queue = new ArrayDeque<>();
		doAnswer(inv ->
		{
			queue.add(inv.getArgument(0));
			return null;
		}).when(controller).post(any(Runnable.class));

		final BankViewModel model = new BankViewModel();
		when(controller.getViewModel()).thenReturn(model);
		when(controller.isOpen()).thenReturn(true);

		final Rectangle thumb = model.scrollThumbRect();
		final Rectangle bounds = new Rectangle(0, 0, model.size().width, model.size().height);
		listener.publish(true, bounds, false, false);
		listener.publishHud(false, null);

		final int tx = thumb.x + thumb.width / 2;
		final int ty = thumb.y + thumb.height / 2;

		// press and release on the thumb, with the client thread never getting a turn in between
		listener.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, tx, ty));
		listener.mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, tx, ty));
		drain(queue);

		// a later drag in the game world, well outside the window and with no press of ours
		MouseEvent worldDrag = listener.mouseDragged(
			mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1, bounds.width + 200, bounds.height + 200));

		assertFalse("a world drag must reach the game", worldDrag.isConsumed());
		assertTrue("and must not have queued a scroll", queue.isEmpty());
	}

	/** Same stale-flag hazard as the vertical thumb, for the horizontal one added with per-tab widths. */
	@Test
	public void aStaleHorizontalThumbFlagDoesNotSwallowALaterWorldDrag()
	{
		final Deque<Runnable> queue = new ArrayDeque<>();
		doAnswer(inv ->
		{
			queue.add(inv.getArgument(0));
			return null;
		}).when(controller).post(any(Runnable.class));

		final BankViewModel model = new BankViewModel();
		// The horizontal bar overlays the grid instead of reserving its own row, so it only exists,
		// and only hit-tests, while some tab is actually laid out wider than the viewport. The All
		// view (the default active tab) skips empty tabs entirely, so select the main tab directly.
		model.setActiveTab(0);
		model.setVisibleCols(4);
		model.rebuild();
		when(controller.getViewModel()).thenReturn(model);
		when(controller.isOpen()).thenReturn(true);

		assertTrue("the bar must be showing for this test to exercise it", model.isHScrollbarVisible());

		final Rectangle thumb = model.hScrollThumbRect();
		final Rectangle bounds = new Rectangle(0, 0, model.size().width, model.size().height);
		listener.publish(true, bounds, false, false);
		listener.publishHud(false, null);

		final int tx = thumb.x + thumb.width / 2;
		final int ty = thumb.y + thumb.height / 2;

		listener.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, tx, ty));
		listener.mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, tx, ty));
		drain(queue);

		MouseEvent worldDrag = listener.mouseDragged(
			mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1, bounds.width + 200, bounds.height + 200));

		assertFalse("a world drag must reach the game", worldDrag.isConsumed());
		assertTrue("and must not have queued a horizontal scroll", queue.isEmpty());
	}

	/**
	 * The wheel decides its axis on the AWT thread from the event's own shift state and posts one
	 * runnable either way, never touching the view model itself.
	 */
	@Test
	public void shiftWheelScrollsSidewaysAndAPlainWheelStillScrollsDown()
	{
		final Deque<Runnable> queue = new ArrayDeque<>();
		doAnswer(inv ->
		{
			queue.add(inv.getArgument(0));
			return null;
		}).when(controller).post(any(Runnable.class));

		final BankViewModel model = new BankViewModel();
		when(controller.getViewModel()).thenReturn(model);

		// Enough content in one tab that both axes have somewhere to go.
		final BankLayout layout = new BankLayout();
		final java.util.List<ItemSnapshot> items = new java.util.ArrayList<>();
		for (int i = 1; i <= 60; i++)
		{
			layout.getMainTab().append(i);
			items.add(new ItemSnapshot(i, "Item " + i, 1, false));
		}
		model.setLayout(layout);
		model.setSnapshots(java.util.Collections.singletonList(
			new StorageSnapshot("carryable", "Inventory", null, items)));
		model.setActiveTab(0);
		model.setVisibleCols(BankGeometry.MIN_COLS);
		model.rebuild();

		final Rectangle bounds = new Rectangle(0, 0, model.size().width, model.size().height);
		listener.publish(true, bounds, false, false);

		listener.mouseWheelMoved(wheelEvent(bounds.width / 2, bounds.height / 2, MouseEvent.SHIFT_DOWN_MASK));
		drain(queue);
		assertTrue("shift + wheel must scroll sideways", model.getHScroll() > 0);
		assertEquals("and must leave the vertical offset alone", 0, model.getScroll());

		listener.mouseWheelMoved(wheelEvent(bounds.width / 2, bounds.height / 2, 0));
		drain(queue);
		assertTrue("a plain wheel must scroll down", model.getScroll() > 0);
	}

	private static java.awt.event.MouseWheelEvent wheelEvent(int x, int y, int modifiers)
	{
		return new java.awt.event.MouseWheelEvent(SOURCE, MouseEvent.MOUSE_WHEEL,
			System.currentTimeMillis(), modifiers, x, y, 0, false,
			java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1);
	}

	private static void drain(Deque<Runnable> queue)
	{
		while (!queue.isEmpty())
		{
			queue.poll().run();
		}
	}
}
