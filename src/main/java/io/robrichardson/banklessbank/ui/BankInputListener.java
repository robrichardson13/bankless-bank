package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankConfig;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.util.HotkeyListener;

/**
 * All input for the bank view. Runs on the AWT event thread and never touches the view model
 * directly: it decides whether to consume from three volatile fields published by
 * {@link BankOverlay} on the client thread, and posts every mutation to the controller's queue so
 * it runs on the client thread at the top of the next frame.
 *
 * <p>Registered at mouse-listener position 0, ahead of RuneLite's own overlay listener, so a
 * consumed event never reaches the game. That means it must stand aside while alt is held, or
 * alt-dragging other overlays would stop working.
 */
@Singleton
public class BankInputListener implements MouseListener, MouseWheelListener, KeyListener
{
	private static final Rectangle EMPTY = new Rectangle();
	private static final int DRAG_SLOP = 4;

	private final BanklessBankConfig config;
	private final BankViewController controller;

	// published from the client thread
	private volatile boolean open;
	private volatile Rectangle bounds = EMPTY;
	private volatile boolean altHeld;
	private volatile boolean menuOpen;
	private volatile boolean hudVisible;
	private volatile Rectangle hudBounds = EMPTY;

	/** Canvas coordinates of the last mouse position, or null. Replaced wholesale. */
	@Getter
	private volatile Point lastMouse;

	// Armed on the client thread by the runnable posted from mousePressed, read and cleared on
	// the AWT thread, hence volatile.
	private volatile boolean titleDrag;
	private volatile boolean thumbDrag;
	private volatile boolean hThumbDrag;
	private volatile boolean slotDragArmed;
	private volatile boolean tabDragArmed;
	private volatile int tabPressStripIndex = -1;
	private volatile boolean resizeDrag;

	// AWT thread only
	private Point titleDragOffset;
	private Point resizeGrabOffset;
	private boolean slotDragActive;
	private boolean tabDragActive;
	private Point pressPoint;

	/** True while a consumed press is outstanding, so its release is consumed too. */
	private boolean pressConsumed;

	/** True when the press+release that preceded the next click were both consumed. */
	private boolean clickConsumed;

	@Getter
	private final HotkeyListener hotkeyListener;

	@Inject
	BankInputListener(BanklessBankConfig config, BankViewController controller)
	{
		this.config = config;
		this.controller = controller;
		this.hotkeyListener = new HotkeyListener(this.config::toggleKeybind)
		{
			@Override
			public void hotkeyPressed()
			{
				BankInputListener.this.controller.toggle();
			}
		};
	}

	/** Client thread, from {@link BankOverlay#render}. */
	public void publish(boolean open, Rectangle bounds, boolean altHeld, boolean menuOpen)
	{
		this.open = open;
		this.bounds = bounds == null ? EMPTY : bounds;
		this.altHeld = altHeld;
		this.menuOpen = menuOpen;
	}

	/** Client thread, from {@link HudButtonOverlay#render}. */
	public void publishHud(boolean visible, Rectangle bounds)
	{
		this.hudVisible = visible;
		this.hudBounds = bounds == null ? EMPTY : bounds;
	}

	/**
	 * Client thread. Whether the bank window is open this frame, as most recently published by
	 * {@link BankOverlay#render}. {@link HudButtonOverlay} reads this to decide whether it would be
	 * drawn on top of the bank window (see the draw-order note in its {@code render}), since
	 * OverlayManager's draw order cannot be fixed with priority alone.
	 */
	public boolean isBankOpen()
	{
		return open;
	}

	/** Client thread. The bank window's current on-screen bounds, as most recently published by
	 * {@link BankOverlay#render}; empty when the bank is closed. */
	public Rectangle getBankBounds()
	{
		return bounds;
	}

	private boolean inside(MouseEvent e)
	{
		return open && !altHeld && bounds.contains(e.getPoint());
	}

	private boolean overHud(MouseEvent e)
	{
		return hudVisible && !altHeld && hudBounds.contains(e.getPoint());
	}

	private int localX(MouseEvent e)
	{
		return e.getX() - bounds.x;
	}

	private int localY(MouseEvent e)
	{
		return e.getY() - bounds.y;
	}

	private MouseEvent consume(MouseEvent e)
	{
		e.consume();
		return e;
	}

	private void trackMouse(MouseEvent e)
	{
		lastMouse = new Point(e.getX(), e.getY());
	}

	/** Drops every in-flight drag; the caller decides what happens to the consumed-press flags. */
	private void clearDragState()
	{
		titleDrag = false;
		thumbDrag = false;
		hThumbDrag = false;
		slotDragArmed = false;
		slotDragActive = false;
		tabDragArmed = false;
		tabDragActive = false;
		tabPressStripIndex = -1;
		resizeDrag = false;
		pressPoint = null;
		titleDragOffset = null;
		resizeGrabOffset = null;
	}

	// ---- mouse -----------------------------------------------------------------------------

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		trackMouse(e);
		return inside(e) ? consume(e) : e;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		trackMouse(e);
		pressConsumed = false;

		if (!inside(e))
		{
			if (overHud(e))
			{
				controller.toggle();
				pressConsumed = true;
				return consume(e);
			}

			// A click outside an open context menu dismisses it and is eaten, as every menu does.
			// Any other outside click belongs to the game.
			if (menuOpen && !altHeld)
			{
				controller.post(() -> controller.getViewModel().closeMenu());
				pressConsumed = true;
				return consume(e);
			}

			// Clicking the world clears search focus, so typing goes back to the game.
			if (open && !altHeld)
			{
				controller.post(() -> controller.getViewModel().setSearchFocused(false));
			}
			return e;
		}

		if (SwingUtilities.isRightMouseButton(e))
		{
			final int lx = localX(e);
			final int ly = localY(e);
			controller.post(() -> controller.getViewModel().openMenu(lx, ly));
			pressConsumed = true;
			return consume(e);
		}

		if (!SwingUtilities.isLeftMouseButton(e))
		{
			pressConsumed = true;
			return consume(e);
		}

		pressConsumed = true;

		final int lx = localX(e);
		final int ly = localY(e);
		clearDragState();
		pressPoint = new Point(lx, ly);
		titleDragOffset = new Point(lx, ly);
		resizeGrabOffset = new Point(bounds.x + bounds.width - e.getX(), bounds.y + bounds.height - e.getY());

		controller.post(() ->
		{
			final BankViewModel model = controller.getViewModel();

			if (model.isMenuOpen())
			{
				if (model.activateMenu(lx, ly))
				{
					controller.saveLayoutIfChanged();
				}
				model.closeMenu();
				if (model.consumeCloseRequest())
				{
					controller.setOpen(false);
				}
				int renameTab = model.consumeRenameTabRequest();
				if (renameTab >= 0)
				{
					// Opens RuneLite's own chatbox text input, so the keys it needs are ours to leave
					// alone from here on - controller.chatboxInputOpen makes that happen.
					controller.openTabRename(renameTab);
				}
				return;
			}

			final Hit hit = model.hitTest(lx, ly);
			model.setSearchFocused(hit.getType() == Hit.Type.SEARCH
				|| hit.getType() == Hit.Type.SEARCH_BUTTON);
			switch (hit.getType())
			{
				case CLOSE:
					controller.setOpen(false);
					break;
				case TAB:
					model.setActiveTab(hit.getIndex() == 0 ? -1 : hit.getIndex() - 1);
					controller.saveViewState();
					tabDragArmed = true;
					tabPressStripIndex = hit.getIndex();
					break;
				case SEARCH:
				case SEARCH_BUTTON:
					// focus was set above
					break;
				case MODE_BUTTON:
					model.toggleMode();
					controller.saveViewState();
					break;
				case ADD_BUTTON:
					// Focus was cleared above, so the keys the chatbox item search needs are ours to
					// leave alone from here on.
					controller.openItemSearch();
					break;
				case SCROLL_UP:
					model.scrollBy(-BankGeometry.SCROLL_STEP);
					break;
				case SCROLL_DOWN:
					model.scrollBy(BankGeometry.SCROLL_STEP);
					break;
				case SCROLL_TRACK:
					model.scrollBy(ly < model.scrollThumbRect().y
						? -model.getViewportHeight() : model.getViewportHeight());
					break;
				case SCROLL_THUMB:
					thumbDrag = true;
					break;
				case HSCROLL_LEFT:
					model.hScrollBy(-BankGeometry.SCROLL_STEP);
					break;
				case HSCROLL_RIGHT:
					model.hScrollBy(BankGeometry.SCROLL_STEP);
					break;
				case HSCROLL_TRACK:
					model.hScrollBy(lx < model.hScrollThumbRect().x
						? -model.getViewportWidth() : model.getViewportWidth());
					break;
				case HSCROLL_THUMB:
					hThumbDrag = true;
					break;
				case TITLE_BAR:
					titleDrag = true;
					break;
				case SLOT:
					slotDragArmed = true;
					break;
				case RESIZE_GRIP:
					resizeDrag = true;
					break;
				default:
					break;
			}
		});

		return consume(e);
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		trackMouse(e);

		if (!open)
		{
			return e;
		}

		if (titleDrag && titleDragOffset != null)
		{
			final int x = e.getX() - titleDragOffset.x;
			final int y = e.getY() - titleDragOffset.y;
			controller.setPosition(x, y);
			return consume(e);
		}

		if (resizeDrag && resizeGrabOffset != null)
		{
			// Both axes off one grip: the pointer's distance from the window's top-left, converted to
			// whole columns and rows. The view model clamps each to its own legal range.
			final int w = e.getX() + resizeGrabOffset.x - bounds.x;
			final int h = e.getY() + resizeGrabOffset.y - bounds.y;
			final int cols = BankGeometry.colsForWidth(w);
			final int rows = BankGeometry.rowsForHeight(h);
			controller.post(() ->
			{
				controller.getViewModel().setVisibleCols(cols);
				controller.getViewModel().setVisibleRows(rows);
			});
			return consume(e);
		}

		final int lx = localX(e);
		final int ly = localY(e);

		// pressPoint is AWT-only state, cleared the moment the button comes up. Every drag branch
		// pairs its client-thread-armed flag with one of these, so a flag armed by a posted runnable
		// that ran after the release cannot make a later, unrelated drag ours.
		if (thumbDrag && pressPoint != null)
		{
			controller.post(() -> controller.getViewModel().scrollThumbTo(ly));
			return consume(e);
		}

		if (hThumbDrag && pressPoint != null)
		{
			controller.post(() -> controller.getViewModel().hScrollThumbTo(lx));
			return consume(e);
		}

		if (slotDragArmed && pressPoint != null)
		{
			if (!slotDragActive)
			{
				if (Math.abs(lx - pressPoint.x) < DRAG_SLOP && Math.abs(ly - pressPoint.y) < DRAG_SLOP)
				{
					return consume(e);
				}

				slotDragActive = true;
				final int px = pressPoint.x;
				final int py = pressPoint.y;
				controller.post(() -> controller.getViewModel().beginDrag(px, py));
			}

			controller.post(() -> controller.getViewModel().updateDrag(lx, ly));
			return consume(e);
		}

		if (tabDragArmed && pressPoint != null)
		{
			if (!tabDragActive)
			{
				if (Math.abs(lx - pressPoint.x) < DRAG_SLOP && Math.abs(ly - pressPoint.y) < DRAG_SLOP)
				{
					return consume(e);
				}

				tabDragActive = true;
				final int stripIndex = tabPressStripIndex;
				controller.post(() -> controller.getViewModel().beginTabDrag(stripIndex));
			}

			controller.post(() -> controller.getViewModel().updateTabDrag(lx, ly));
			return consume(e);
		}

		return pressConsumed ? consume(e) : e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		trackMouse(e);

		final boolean wasDragging = titleDrag || thumbDrag || hThumbDrag || slotDragActive
			|| tabDragActive || resizeDrag;
		final boolean consumed = pressConsumed || (open && (inside(e) || wasDragging));

		if (titleDrag)
		{
			controller.savePosition();
		}
		else if (resizeDrag)
		{
			// saveViewState() reads visibleCols / visibleRows / mode / activeTab straight off the view
			// model, which
			// is client-thread confined, so it goes through the queue like every other mutation.
			controller.post(controller::saveViewState);
		}
		else if (slotDragActive)
		{
			final int lx = localX(e);
			final int ly = localY(e);
			controller.post(() ->
			{
				final DropTarget target = controller.getViewModel().endDrag(lx, ly);
				if (target != null && target.getType() != DropTarget.Type.CANCEL)
				{
					controller.saveLayoutIfChanged();
				}
			});
		}
		else if (tabDragActive)
		{
			final int lx = localX(e);
			final int ly = localY(e);
			controller.post(() ->
			{
				final DropTarget target = controller.getViewModel().endTabDrag(lx, ly);
				if (target != null && target.getType() != DropTarget.Type.CANCEL)
				{
					controller.saveLayoutIfChanged();
				}
			});
		}

		clearDragState();
		clickConsumed = consumed;
		pressConsumed = false;

		return consumed ? consume(e) : e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		final boolean consume = clickConsumed || inside(e);
		clickConsumed = false;
		return consume ? consume(e) : e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		lastMouse = null;
		return e;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		if (!open || altHeld || !bounds.contains(e.getPoint()))
		{
			return e;
		}

		final int delta = e.getWheelRotation() * BankGeometry.SCROLL_STEP;
		// Shift + wheel scrolls sideways, the convention everywhere else a grid is wider than its
		// window. Without shift the wheel always scrolls vertically, as before.
		final boolean horizontal = e.isShiftDown();
		controller.post(() -> {
			final BankViewModel model = controller.getViewModel();
			if (horizontal)
			{
				model.hScrollBy(delta);
			}
			else
			{
				model.scrollBy(delta);
			}
		});
		e.consume();
		return e;
	}

	// ---- keyboard --------------------------------------------------------------------------

	@Override
	public void keyTyped(KeyEvent e)
	{
		if (!controller.isOpen() || controller.isChatboxInputOpen())
		{
			return;
		}

		final char c = e.getKeyChar();
		if (c < ' ' || c == 127)
		{
			return;
		}

		// Typing only reaches the search box while it has focus; otherwise it belongs to the game
		// (the chatbox, most commonly), so the event must be left unconsumed and untouched.
		if (!controller.isSearchFocused())
		{
			return;
		}

		controller.post(() -> controller.getViewModel().onChar(c));
		e.consume();
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		// Our key listener registers before the chatbox panel's, so while the game's item search is
		// open every key - Escape above all, which closes that search - has to be left to it. The
		// window stays open behind it either way.
		if (!controller.isOpen() || controller.isChatboxInputOpen())
		{
			return;
		}

		switch (e.getKeyCode())
		{
			case KeyEvent.VK_ESCAPE:
				// Escape always belongs to us while the view is open: it closes the topmost menu,
				// then clears the search, then unfocuses it, and only then closes the window.
				controller.post(() ->
				{
					final BankViewModel model = controller.getViewModel();
					if (model.isMenuOpen())
					{
						model.closeMenu();
					}
					else if (model.isSearchFocused() && !model.getSearch().isEmpty())
					{
						model.clearSearch();
					}
					else if (model.isSearchFocused())
					{
						model.setSearchFocused(false);
					}
					else
					{
						controller.setOpen(false);
					}
				});
				e.consume();
				break;
			case KeyEvent.VK_BACK_SPACE:
				if (!controller.isSearchFocused())
				{
					return;
				}
				controller.post(() -> controller.getViewModel().onBackspace());
				e.consume();
				break;
			case KeyEvent.VK_ENTER:
				if (!controller.isSearchFocused())
				{
					return;
				}
				controller.post(() -> controller.getViewModel().setSearchFocused(false));
				e.consume();
				break;
			default:
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		// nothing
	}

	@Override
	public void focusLost()
	{
		clearDragState();
		pressConsumed = false;
		clickConsumed = false;

		controller.post(() ->
		{
			final BankViewModel model = controller.getViewModel();
			model.cancelDrag();
			model.cancelTabDrag();
			model.closeMenu();
			model.setSearchFocused(false);
		});
	}
}
