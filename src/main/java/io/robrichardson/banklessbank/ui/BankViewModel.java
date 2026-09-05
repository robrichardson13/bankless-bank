package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The whole view: owns a {@link BankLayout}, storage snapshots, search text, mode, active tab,
 * scroll, drag state and context menu. Builds rows, hit-tests, and mutates the layout.
 *
 * <p>Client-thread confined at runtime, but has zero platform dependencies so tests drive it
 * directly.
 */
public class BankViewModel
{
	private static final String UNKNOWN_ITEM_PREFIX = "Item ";

	// ---- inputs ----
	private BankLayout layout = new BankLayout();
	private List<StorageSnapshot> snapshots = Collections.emptyList();
	private Map<Integer, String> knownNames = new HashMap<>();
	private boolean placeholdersEnabled = true;
	private boolean showEmptyStorages;
	private int visibleRows = BankGeometry.DEFAULT_ROWS;

	// ---- view state ----
	private ViewMode mode = ViewMode.TABS;
	private int activeTab = -1;
	private String search = "";
	/** {@link #search} lower-cased once, so the filter does not re-fold it per item. */
	private String searchLower = "";
	private boolean searchFocused;

	// ---- owned-item data, recomputed whenever snapshots change (independent of dirty rows) ----
	private Set<Integer> ownedIds = new LinkedHashSet<>();
	private Map<Integer, Long> qtyById = new LinkedHashMap<>();
	private Map<Integer, List<SlotSource>> sourcesById = new LinkedHashMap<>();
	private Map<Integer, ItemSnapshot> firstSnapshotById = new LinkedHashMap<>();

	// ---- rebuild output ----
	private boolean dirty = true;
	private List<BankRow> rows = Collections.emptyList();
	private List<BankSlot> slots = Collections.emptyList();
	private int contentHeight;

	// ---- scroll ----
	private int scroll;

	// ---- drag ----
	private boolean dragging;
	private BankSlot dragSlot;
	private Point dragPoint;
	private int dropCaretIndex = -1;

	// ---- context menu ----
	private boolean menuOpen;
	private ContextMenu menu;

	/** Raised by the title-bar menu's "Close" entry; the controller consumes it. */
	private boolean closeRequested;

	// =========================================================================================
	// Inputs
	// =========================================================================================

	public void setSnapshots(List<StorageSnapshot> snapshots)
	{
		this.snapshots = new ArrayList<>(snapshots);
		recomputeOwnedData();
		invalidate();
	}

	public void setLayout(BankLayout layout)
	{
		this.layout = layout != null ? layout : new BankLayout();
		invalidate();
	}

	public BankLayout getLayout()
	{
		return layout;
	}

	public void setPlaceholdersEnabled(boolean enabled)
	{
		if (this.placeholdersEnabled == enabled)
		{
			return;
		}

		this.placeholdersEnabled = enabled;
		invalidate();
	}

	public boolean isPlaceholdersEnabled()
	{
		return placeholdersEnabled;
	}

	public void setShowEmptyStorages(boolean enabled)
	{
		if (this.showEmptyStorages == enabled)
		{
			return;
		}

		this.showEmptyStorages = enabled;
		invalidate();
	}

	public boolean isShowEmptyStorages()
	{
		return showEmptyStorages;
	}

	public void setVisibleRows(int rows)
	{
		this.visibleRows = clamp(rows, BankGeometry.MIN_ROWS, BankGeometry.MAX_ROWS);
		this.scroll = clamp(scroll, 0, getMaxScroll());
	}

	public int getVisibleRows()
	{
		return visibleRows;
	}

	/** Canonical id -> display name, used for placeholders whose id is not in any snapshot. */
	public void setKnownNames(Map<Integer, String> names)
	{
		this.knownNames = new HashMap<>(names);
		invalidate();
	}

	// =========================================================================================
	// View state
	// =========================================================================================

	public ViewMode getMode()
	{
		return mode;
	}

	public void setMode(ViewMode mode)
	{
		this.mode = mode;
		this.scroll = 0;
		invalidate();
	}

	public int getActiveTab()
	{
		return activeTab;
	}

	public void setActiveTab(int tabIndex)
	{
		this.activeTab = tabIndex;
		this.scroll = 0;
		invalidate();
	}

	/** The {@link BankTab} instance currently active, or null when no tab is active. */
	private BankTab activeTabRef()
	{
		return activeTab >= 0 ? layout.getTab(activeTab) : null;
	}

	/**
	 * Re-resolves {@link #activeTab} to wherever {@code ref} now lives after a layout mutation
	 * (tabs can be deleted or pruned, shifting indices). Matches by identity, not equality, since
	 * {@link BankTab} is a data class and distinct tabs can otherwise compare equal.
	 */
	private void syncActiveTab(BankTab ref)
	{
		if (ref == null)
		{
			if (activeTab >= layout.getTabs().size())
			{
				activeTab = -1;
			}
			return;
		}

		for (int i = 0; i < layout.getTabs().size(); i++)
		{
			if (layout.getTab(i) == ref)
			{
				activeTab = i;
				return;
			}
		}
		activeTab = -1;
	}

	public String getSearch()
	{
		return search;
	}

	public void setSearch(String text)
	{
		this.search = text == null ? "" : text;
		this.searchLower = this.search.toLowerCase(Locale.ROOT);
		this.scroll = 0;
		invalidate();
	}

	public boolean isSearchFocused()
	{
		return searchFocused;
	}

	public void setSearchFocused(boolean focused)
	{
		this.searchFocused = focused;
	}

	// =========================================================================================
	// Rebuild
	// =========================================================================================

	public void invalidate()
	{
		dirty = true;
	}

	public boolean isDirty()
	{
		return dirty;
	}

	/** Recomputes owned ids, rows and content height. Idempotent; no-op when not dirty. */
	public void rebuild()
	{
		if (!dirty)
		{
			return;
		}

		List<BankRow> newRows = new ArrayList<>();
		List<BankSlot> newSlots = new ArrayList<>();
		int y = 0;

		if (mode == ViewMode.TABS)
		{
			List<BankSlot> built = new ArrayList<>();
			for (int id : candidateIdsForTabs())
			{
				BankSlot slot = buildTabSlot(id);
				if (slot != null && matchesSearch(slot.getName()))
				{
					built.add(slot);
				}
			}
			y = appendItemRows(newRows, newSlots, built, y);
		}
		else
		{
			for (StorageSnapshot snap : snapshots)
			{
				final boolean emptyStorage = snap.getItems().isEmpty();
				if (emptyStorage && !showEmptyStorages)
				{
					continue;
				}

				List<BankSlot> built = new ArrayList<>();
				for (ItemSnapshot item : snap.getItems())
				{
					if (item.getQuantity() <= 0 || !matchesSearch(item.getName()))
					{
						continue;
					}
					List<SlotSource> src = Collections.singletonList(new SlotSource(snap.getName(), item.getQuantity()));
					built.add(new BankSlot(item.getCanonicalId(), item.getName(), item.getQuantity(),
						item.isStackable(), false, src, -1, -1));
				}

				// An empty storage still gets a header when the player asked to see empty storages and
				// is not filtering; anything else with no matching items is skipped entirely.
				final boolean keepAsEmptyStorage = emptyStorage && showEmptyStorages && search.isEmpty();
				if (built.isEmpty() && !keepAsEmptyStorage)
				{
					continue;
				}

				newRows.add(new BankRow(BankRow.Kind.HEADER, y, BankGeometry.HEADER_H, snap.getName(),
					snap.getSubtitle(), Collections.emptyList()));
				y += BankGeometry.HEADER_H;
				y = appendItemRows(newRows, newSlots, built, y);
			}
		}

		rows = Collections.unmodifiableList(newRows);
		slots = Collections.unmodifiableList(newSlots);
		contentHeight = y;
		scroll = clamp(scroll, 0, getMaxScroll());
		dirty = false;
	}

	private List<Integer> candidateIdsForTabs()
	{
		List<Integer> ids = new ArrayList<>();
		if (!search.isEmpty() || activeTab == -1)
		{
			for (BankTab tab : layout.getTabs())
			{
				ids.addAll(tab.getSlots());
			}
		}
		else
		{
			BankTab tab = layout.getTab(activeTab);
			if (tab != null)
			{
				ids.addAll(tab.getSlots());
			}
		}
		return ids;
	}

	/** Builds the slot for a TABS-mode candidate id, or null when it is a dropped placeholder. */
	private BankSlot buildTabSlot(int id)
	{
		int tabIndex = layout.indexOfTab(id);
		int indexInTab = tabIndex == -1 ? -1 : layout.getTab(tabIndex).getSlots().indexOf(id);

		if (ownedIds.contains(id))
		{
			ItemSnapshot first = firstSnapshotById.get(id);
			String name = first != null ? first.getName() : nameFor(id);
			boolean stackable = first != null && first.isStackable();
			long qty = qtyById.getOrDefault(id, 0L);
			List<SlotSource> src = sourcesById.getOrDefault(id, Collections.emptyList());
			return new BankSlot(id, name, qty, stackable, false, src, tabIndex, indexInTab);
		}

		if (!placeholdersEnabled)
		{
			return null;
		}

		return new BankSlot(id, nameFor(id), 0, false, true, Collections.emptyList(), tabIndex, indexInTab);
	}

	private String nameFor(int id)
	{
		return knownNames.getOrDefault(id, UNKNOWN_ITEM_PREFIX + id);
	}

	private boolean matchesSearch(String name)
	{
		return searchLower.isEmpty() || (name != null && name.toLowerCase(Locale.ROOT).contains(searchLower));
	}

	private static int appendItemRows(List<BankRow> rowsOut, List<BankSlot> slotsOut, List<BankSlot> built, int y)
	{
		for (int i = 0; i < built.size(); i += BankGeometry.COLS)
		{
			List<BankSlot> chunk = built.subList(i, Math.min(i + BankGeometry.COLS, built.size()));
			rowsOut.add(new BankRow(BankRow.Kind.ITEMS, y, BankGeometry.SLOT_H, null, null, chunk));
			slotsOut.addAll(chunk);
			y += BankGeometry.SLOT_H;
		}
		return y;
	}

	private void recomputeOwnedData()
	{
		Set<Integer> owned = new LinkedHashSet<>();
		Map<Integer, Long> qty = new LinkedHashMap<>();
		Map<Integer, List<SlotSource>> src = new LinkedHashMap<>();
		Map<Integer, ItemSnapshot> first = new LinkedHashMap<>();

		for (StorageSnapshot snap : snapshots)
		{
			for (ItemSnapshot item : snap.getItems())
			{
				if (item.getQuantity() <= 0)
				{
					continue;
				}
				int id = item.getCanonicalId();
				owned.add(id);
				qty.merge(id, item.getQuantity(), Long::sum);
				src.computeIfAbsent(id, k -> new ArrayList<>()).add(new SlotSource(snap.getName(), item.getQuantity()));
				first.putIfAbsent(id, item);
			}
		}

		ownedIds = owned;
		qtyById = qty;
		sourcesById = src;
		firstSnapshotById = first;
	}

	public List<BankRow> getRows()
	{
		return rows;
	}

	public List<BankSlot> getSlots()
	{
		return slots;
	}

	public Set<Integer> getOwnedIds()
	{
		return Collections.unmodifiableSet(ownedIds);
	}

	public int getItemCount()
	{
		int count = 0;
		for (BankSlot slot : slots)
		{
			if (!slot.isPlaceholder())
			{
				count++;
			}
		}
		return count;
	}

	public int getTabCount()
	{
		return layout.getTabs().size();
	}

	public int getTabIconItemId(int tabIndex)
	{
		BankTab tab = layout.getTab(tabIndex);
		return tab == null ? -1 : tab.getIconItemId();
	}

	public int getStripLength()
	{
		final int tabCount = getTabCount();
		return 1 + tabCount + (tabCount < BankLayout.MAX_TABS ? 1 : 0);
	}

	/** layout.sync(getOwnedIds(), placeholdersEnabled). Returns true when the layout changed. */
	public boolean syncLayout()
	{
		boolean changed = layout.sync(ownedIds, placeholdersEnabled);
		if (changed)
		{
			invalidate();
		}
		return changed;
	}

	// =========================================================================================
	// Scrolling
	// =========================================================================================

	public int getScroll()
	{
		return scroll;
	}

	public void setScroll(int px)
	{
		scroll = clamp(px, 0, getMaxScroll());
	}

	public void scrollBy(int px)
	{
		setScroll(scroll + px);
	}

	public int getMaxScroll()
	{
		return Math.max(0, contentHeight - getViewportHeight());
	}

	public int getContentHeight()
	{
		return contentHeight;
	}

	public int getViewportHeight()
	{
		return visibleRows * BankGeometry.SLOT_H;
	}

	public void scrollThumbTo(int localY)
	{
		Rectangle track = scrollbarRect();
		Rectangle thumbNow = scrollThumbRect();
		int range = track.height - thumbNow.height;
		if (range <= 0 || getMaxScroll() <= 0)
		{
			scroll = 0;
			return;
		}

		double t = (localY - track.y - thumbNow.height / 2.0) / range;
		setScroll((int) Math.round(t * getMaxScroll()));
	}

	// =========================================================================================
	// Geometry / hit testing
	// =========================================================================================

	public Dimension size()
	{
		return BankGeometry.size(visibleRows);
	}

	public Rectangle titleBarRect()
	{
		return BankGeometry.titleBar();
	}

	public Rectangle closeButtonRect()
	{
		return BankGeometry.closeButton();
	}

	public Rectangle tabRect(int stripIndex)
	{
		return BankGeometry.tabAt(stripIndex);
	}

	public Rectangle gridRect()
	{
		return BankGeometry.grid(visibleRows);
	}

	public Rectangle searchRect()
	{
		return BankGeometry.searchBox(visibleRows);
	}

	public Rectangle scrollbarRect()
	{
		return BankGeometry.scrollbar(visibleRows);
	}

	public Rectangle scrollThumbRect()
	{
		return BankGeometry.thumb(scrollbarRect(), scroll, getMaxScroll(), contentHeight, getViewportHeight());
	}

	/** Local rect of flat slot index i, already offset by scroll. May lie outside the grid. */
	public Rectangle slotRect(int slotIndex)
	{
		int idx = 0;
		for (BankRow row : rows)
		{
			if (row.getKind() != BankRow.Kind.ITEMS)
			{
				continue;
			}
			int size = row.getSlots().size();
			if (slotIndex < idx + size)
			{
				int col = slotIndex - idx;
				int rowLocalY = gridRect().y + row.getY() - scroll;
				return BankGeometry.slotInRow(rowLocalY, col);
			}
			idx += size;
		}

		if (slotIndex == idx && !rows.isEmpty())
		{
			for (int i = rows.size() - 1; i >= 0; i--)
			{
				BankRow row = rows.get(i);
				if (row.getKind() == BankRow.Kind.ITEMS)
				{
					int rowLocalY = gridRect().y + row.getY() - scroll;
					int col = Math.min(row.getSlots().size(), BankGeometry.COLS - 1);
					return BankGeometry.slotInRow(rowLocalY, col);
				}
			}
		}

		return new Rectangle(-1, -1, 0, 0);
	}

	public boolean isSlotVisible(int slotIndex)
	{
		if (slotIndex < 0 || slotIndex >= slots.size())
		{
			return false;
		}
		Rectangle r = slotRect(slotIndex);
		return gridRect().intersects(r);
	}

	public Hit hitTest(int x, int y)
	{
		if (menuOpen && menu != null)
		{
			if (menu.getBounds().contains(x, y))
			{
				return Hit.of(Hit.Type.MENU_ENTRY, menu.entryIndexAt(x, y));
			}
			return Hit.none();
		}

		if (closeButtonRect().contains(x, y))
		{
			return Hit.of(Hit.Type.CLOSE, -1);
		}
		if (titleBarRect().contains(x, y))
		{
			return Hit.of(Hit.Type.TITLE_BAR, -1);
		}

		int stripLength = getStripLength();
		boolean plusExists = getTabCount() < BankLayout.MAX_TABS;
		for (int i = 0; i < stripLength; i++)
		{
			if (tabRect(i).contains(x, y))
			{
				if (plusExists && i == stripLength - 1)
				{
					return Hit.of(Hit.Type.TAB_PLUS, i);
				}
				return Hit.of(Hit.Type.TAB, i);
			}
		}

		if (gridRect().contains(x, y))
		{
			for (int i = 0; i < slots.size(); i++)
			{
				if (isSlotVisible(i) && slotRect(i).contains(x, y))
				{
					return Hit.slot(i, slots.get(i));
				}
			}
			return Hit.of(Hit.Type.GRID_EMPTY, -1);
		}

		if (searchRect().contains(x, y))
		{
			return Hit.of(Hit.Type.SEARCH, -1);
		}
		if (scrollThumbRect().contains(x, y))
		{
			return Hit.of(Hit.Type.SCROLL_THUMB, -1);
		}
		if (scrollbarRect().contains(x, y))
		{
			return Hit.of(Hit.Type.SCROLL_TRACK, -1);
		}

		return Hit.none();
	}

	/** Tooltip lines for the point, or an empty list. Line 0 is the item name, then one line per source. */
	public List<String> tooltipLines(int x, int y)
	{
		Hit hit = hitTest(x, y);
		if (hit.getType() != Hit.Type.SLOT || hit.getSlot() == null)
		{
			return Collections.emptyList();
		}

		BankSlot slot = hit.getSlot();
		List<String> lines = new ArrayList<>();
		lines.add(slot.getName());
		for (SlotSource source : slot.getSources())
		{
			lines.add(source.getStorageName() + ": " + source.getQuantity());
		}
		return lines;
	}

	// =========================================================================================
	// Drag and drop
	// =========================================================================================

	public boolean isDragging()
	{
		return dragging;
	}

	public BankSlot getDragSlot()
	{
		return dragSlot;
	}

	public void beginDrag(int x, int y)
	{
		Hit hit = hitTest(x, y);
		if (hit.getType() != Hit.Type.SLOT)
		{
			return;
		}
		dragging = true;
		dragSlot = hit.getSlot();
		dragPoint = new Point(x, y);
		updateDropCaret(x, y);
	}

	public void updateDrag(int x, int y)
	{
		if (!dragging)
		{
			return;
		}
		dragPoint = new Point(x, y);
		updateDropCaret(x, y);
	}

	private void updateDropCaret(int x, int y)
	{
		Hit hit = hitTest(x, y);
		if (hit.getType() == Hit.Type.SLOT)
		{
			dropCaretIndex = hit.getIndex();
		}
		else if (hit.getType() == Hit.Type.GRID_EMPTY)
		{
			dropCaretIndex = slots.size();
		}
		else
		{
			dropCaretIndex = -1;
		}
	}

	public Point getDragPoint()
	{
		return dragPoint;
	}

	/**
	 * Resolves the drop, applies it to the layout, clears drag state.
	 *
	 * @return the resolved target (never null; CANCEL when nothing changed)
	 */
	public DropTarget endDrag(int x, int y)
	{
		if (!dragging || dragSlot == null)
		{
			clearDragState();
			return DropTarget.cancel();
		}

		DropTarget result = DropTarget.cancel();

		if (mode == ViewMode.TABS)
		{
			final BankTab activeRef = activeTabRef();
			Hit hit = hitTest(x, y);
			int itemId = dragSlot.getCanonicalId();

			if (hit.getType() == Hit.Type.SLOT && hit.getSlot() != null)
			{
				int targetTab = hit.getSlot().getTabIndex();
				int targetIndex = hit.getSlot().getIndexInTab();
				if (targetTab != -1)
				{
					layout.moveItem(itemId, targetTab, targetIndex);
					syncActiveTab(activeRef);
					result = new DropTarget(DropTarget.Type.SLOT, targetTab, targetIndex);
				}
			}
			else if (hit.getType() == Hit.Type.TAB)
			{
				int tabIndex = hit.getIndex() == 0 ? 0 : hit.getIndex() - 1;
				if (tabIndex >= 0 && tabIndex < getTabCount())
				{
					layout.moveItemToTab(itemId, tabIndex);
					syncActiveTab(activeRef);
					BankTab tab = layout.getTab(tabIndex);
					int slotIndex = tab == null ? 0 : Math.max(0, tab.getSlots().size() - 1);
					result = new DropTarget(DropTarget.Type.TAB, tabIndex, slotIndex);
				}
			}
			else if (hit.getType() == Hit.Type.TAB_PLUS)
			{
				int newIndex = layout.createTab(itemId);
				if (newIndex != -1)
				{
					syncActiveTab(activeRef);
					setActiveTab(newIndex);
					result = new DropTarget(DropTarget.Type.NEW_TAB, newIndex, 0);
				}
			}
			else if (hit.getType() == Hit.Type.GRID_EMPTY)
			{
				final int targetTab = activeTab == -1 ? 0 : activeTab;
				layout.moveItemToTab(itemId, targetTab);
				syncActiveTab(activeRef);
				final BankTab tab = layout.getTab(targetTab);
				result = new DropTarget(DropTarget.Type.TAB, targetTab, tab == null ? 0 : Math.max(0, tab.getSlots().size() - 1));
			}
		}

		if (result.getType() != DropTarget.Type.CANCEL)
		{
			invalidate();
		}

		clearDragState();
		return result;
	}

	public void cancelDrag()
	{
		clearDragState();
	}

	private void clearDragState()
	{
		dragging = false;
		dragSlot = null;
		dragPoint = null;
		dropCaretIndex = -1;
	}

	/** Preview insertion index the renderer draws a caret at, or -1. */
	public int getDropCaretIndex()
	{
		return dropCaretIndex;
	}

	// =========================================================================================
	// Context menu
	// =========================================================================================

	public boolean isMenuOpen()
	{
		return menuOpen;
	}

	public ContextMenu getMenu()
	{
		return menu;
	}

	public void openMenu(int x, int y)
	{
		Hit hit = hitTest(x, y);
		List<ContextMenuEntry> entries = buildMenuEntries(hit);
		if (entries.isEmpty())
		{
			return;
		}

		int width = 0;
		for (ContextMenuEntry entry : entries)
		{
			width = Math.max(width, entry.getLabel().length() * BankGeometry.MENU_CHAR_W + BankGeometry.MENU_PADDING);
		}
		int height = entries.size() * BankGeometry.MENU_ENTRY_H;

		Dimension overlaySize = size();
		int mx = Math.min(x, overlaySize.width - width);
		int my = Math.min(y, overlaySize.height - height);
		mx = Math.max(0, mx);
		my = Math.max(0, my);

		menu = new ContextMenu(entries, new Rectangle(mx, my, width, height));
		menuOpen = true;
	}

	private List<ContextMenuEntry> buildMenuEntries(Hit hit)
	{
		List<ContextMenuEntry> entries = new ArrayList<>();

		if (hit.getType() == Hit.Type.SLOT && hit.getSlot() != null)
		{
			BankSlot slot = hit.getSlot();
			if (slot.isPlaceholder())
			{
				entries.add(new ContextMenuEntry("Release placeholder", MenuAction.RELEASE_PLACEHOLDER, slot.getCanonicalId()));
				entries.add(new ContextMenuEntry("Release all placeholders in this tab", MenuAction.RELEASE_ALL_IN_TAB, slot.getTabIndex()));
				entries.add(new ContextMenuEntry("Release all placeholders", MenuAction.RELEASE_ALL, -1));
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
			else
			{
				if (activeTab > 0)
				{
					entries.add(new ContextMenuEntry("Set as tab icon", MenuAction.SET_TAB_ICON, slot.getCanonicalId()));
				}
				if (getTabCount() < BankLayout.MAX_TABS)
				{
					entries.add(new ContextMenuEntry("New tab from this item", MenuAction.NEW_TAB_FROM_ITEM, slot.getCanonicalId()));
				}
				if (slot.getTabIndex() != 0)
				{
					entries.add(new ContextMenuEntry("Move to main tab", MenuAction.MOVE_TO_MAIN, slot.getCanonicalId()));
				}
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
		}
		else if (hit.getType() == Hit.Type.TAB)
		{
			if (hit.getIndex() > 1)
			{
				int tabIndex = hit.getIndex() - 1;
				entries.add(new ContextMenuEntry("Release all placeholders in this tab", MenuAction.RELEASE_ALL_IN_TAB, tabIndex));
				entries.add(new ContextMenuEntry("Delete tab", MenuAction.DELETE_TAB, tabIndex));
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
			else if (hit.getIndex() == 1)
			{
				entries.add(new ContextMenuEntry("Release all placeholders in this tab", MenuAction.RELEASE_ALL_IN_TAB, 0));
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
			else
			{
				entries.add(new ContextMenuEntry("Release all placeholders", MenuAction.RELEASE_ALL, -1));
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
		}
		else if (hit.getType() == Hit.Type.TITLE_BAR)
		{
			entries.add(new ContextMenuEntry("Close", MenuAction.CLOSE_VIEW, -1));
			entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
		}

		return entries;
	}

	public void closeMenu()
	{
		menuOpen = false;
		menu = null;
	}

	/**
	 * Whether the title-bar menu's "Close" entry was activated since this was last called, clearing
	 * the request. The view model cannot close the view itself, so the controller polls this after
	 * every {@link #activateMenu(int, int)}.
	 */
	public boolean consumeCloseRequest()
	{
		boolean requested = closeRequested;
		closeRequested = false;
		return requested;
	}

	/** Runs the entry under the point (if any) against the layout. Returns true when the layout changed. */
	public boolean activateMenu(int x, int y)
	{
		if (!menuOpen || menu == null)
		{
			return false;
		}

		int idx = menu.entryIndexAt(x, y);
		if (idx < 0)
		{
			closeMenu();
			return false;
		}

		ContextMenuEntry entry = menu.getEntries().get(idx);
		final BankTab activeRef = activeTabRef();
		boolean changed = false;

		switch (entry.getAction())
		{
			case RELEASE_PLACEHOLDER:
				changed = layout.releasePlaceholder(entry.getArg(), ownedIds);
				syncActiveTab(activeRef);
				break;
			case RELEASE_ALL_IN_TAB:
				changed = layout.releaseAllPlaceholders(entry.getArg(), ownedIds) > 0;
				syncActiveTab(activeRef);
				break;
			case RELEASE_ALL:
				changed = layout.releaseAllPlaceholders(-1, ownedIds) > 0;
				syncActiveTab(activeRef);
				break;
			case NEW_TAB_FROM_ITEM:
			{
				int newIndex = layout.createTab(entry.getArg());
				changed = newIndex != -1;
				syncActiveTab(activeRef);
				if (changed)
				{
					setActiveTab(newIndex);
				}
				break;
			}
			case SET_TAB_ICON:
				if (activeTab >= 0)
				{
					layout.moveItem(entry.getArg(), activeTab, 0);
					syncActiveTab(activeRef);
					changed = true;
				}
				break;
			case MOVE_TO_MAIN:
				layout.moveItemToTab(entry.getArg(), 0);
				syncActiveTab(activeRef);
				changed = true;
				break;
			case DELETE_TAB:
			{
				int tabIndex = entry.getArg();
				layout.deleteTab(tabIndex);
				changed = true;
				syncActiveTab(activeRef);
				break;
			}
			case CLOSE_VIEW:
				closeRequested = true;
				changed = false;
				break;
			case CANCEL:
			default:
				changed = false;
				break;
		}

		closeMenu();
		if (changed)
		{
			invalidate();
		}
		return changed;
	}

	// =========================================================================================
	// Keyboard
	// =========================================================================================

	public boolean onChar(char c)
	{
		if (c < ' ' || c == 127)
		{
			return false;
		}
		setSearch(search + c);
		return true;
	}

	public boolean onBackspace()
	{
		if (search.isEmpty())
		{
			return false;
		}
		setSearch(search.substring(0, search.length() - 1));
		return true;
	}

	public void clearSearch()
	{
		setSearch("");
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
