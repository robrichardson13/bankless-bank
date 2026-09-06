package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.model.BankLayout;
import io.robrichardson.banklessbank.model.BankTab;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
	/** Canonical id -> GE unit price, pushed by the controller from {@code ItemManager}. */
	private Map<Integer, Integer> unitPrices = new HashMap<>();
	private boolean placeholdersEnabled = true;
	private boolean showEmptyStorages;
	private int visibleRows = BankGeometry.DEFAULT_ROWS;
	private int visibleCols = BankGeometry.DEFAULT_COLS;
	/** How many rows the canvas can actually hold, published by the controller each frame. */
	private int maxRows = BankGeometry.MAX_ROWS;
	/** How many columns the canvas can actually hold, published by the controller each frame. */
	private int maxCols = BankGeometry.MAX_COLS;
	/** Cached {@code (visibleCols, visibleRows)} geometry; replaced whenever either changes. */
	/**
	 * Cache behind {@link #geom()}; never read directly. The geometry's third dimension, the number of
	 * tab-strip rows, follows the tab count, which changes without going through any setter, so the
	 * cache is validated on every read rather than only when the window is resized.
	 */
	private BankGeometry geom = BankGeometry.defaults();

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
	/**
	 * Widest row built, in pixels - the grid's virtual width. Never below the viewport, so it is only
	 * wider than the window when some tab's layout is wider than the window.
	 */
	private int contentWidth;

	// ---- scroll ----
	private int scroll;
	/** Scroll remembered per tab, keyed by identity since indices shift under reorder/delete. */
	private final Map<BankTab, Integer> scrollByTab = new IdentityHashMap<>();
	private int allTabScroll;

	// ---- horizontal scroll: only reaches past zero when a tab is laid out wider than the window ----
	private int hScroll;
	private final Map<BankTab, Integer> hScrollByTab = new IdentityHashMap<>();
	private int allTabHScroll;

	// ---- drag ----
	private boolean dragging;
	private BankSlot dragSlot;
	private Point dragPoint;
	/** Flat index of the grid cell under the cursor while dragging, or -1. */
	private int dropSlotIndex = -1;

	// ---- tab drag ----
	private boolean tabDragging;
	private int tabDragFrom = -1;
	private int tabDropIndex = -1;

	// ---- context menu ----
	private boolean menuOpen;
	private ContextMenu menu;
	/** Anchor point the open menu was built at, so a step-2 menu can reopen at the same point. */
	private Point menuAnchor;

	/** Raised by the title-bar menu's "Close" entry; the controller consumes it. */
	private boolean closeRequested;

	/**
	 * The tab index the "Rename tab" entry was activated on, or -1. Renaming needs a chatbox text
	 * input, which is RuneLite-tier, so the pure view model only raises the request; the controller
	 * consumes it and opens the input, then posts the actual {@link #renameTab(int, String)} back.
	 */
	private int renameTabRequest = -1;

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
		scrollByTab.clear();
		allTabScroll = 0;
		hScrollByTab.clear();
		allTabHScroll = 0;
		hScroll = 0;
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

	/** Clamps to {@code [MIN_ROWS, maxRows]}; {@code maxRows} is set by the controller each frame. */
	public void setVisibleRows(int rows)
	{
		this.visibleRows = clamp(rows, BankGeometry.MIN_ROWS, maxRows);
		// The active tab renders trailing rows down to the viewport height, so the row count is an
		// input to the grid, not just a window size.
		invalidate();
		this.scroll = clamp(scroll, 0, getMaxScroll());
	}

	public int getVisibleRows()
	{
		return visibleRows;
	}

	/**
	 * Clamps to {@code [MIN_COLS, maxCols]}. This is the viewport width only: no tab's layout width
	 * changes and nothing is written to the layout, so an item never moves when the window is
	 * resized. What does change is what the viewport shows - a wider window adds blank columns to the
	 * right of a narrower tab, a narrower one starts scrolling horizontally - so the rows are rebuilt
	 * and both scroll offsets re-clamped.
	 */
	public void setVisibleCols(int cols)
	{
		int c = clamp(cols, BankGeometry.MIN_COLS, maxCols);
		if (c == visibleCols)
		{
			return;
		}

		visibleCols = c;
		invalidate();
		rebuild();
		this.scroll = clamp(scroll, 0, getMaxScroll());
		this.hScroll = clamp(hScroll, 0, getMaxHScroll());
		recordScroll();
		recordHScroll();
	}

	public int getVisibleCols()
	{
		return visibleCols;
	}

	/** How many rows the canvas can actually hold. Set each frame by the controller. */
	public void setMaxRows(int rows)
	{
		int m = clamp(rows, BankGeometry.MIN_ROWS, BankGeometry.MAX_ROWS);
		if (m != maxRows)
		{
			maxRows = m;
			setVisibleRows(visibleRows);
		}
	}

	public int getMaxRows()
	{
		return maxRows;
	}

	/** How many columns the canvas can actually hold. Set each frame by the controller. */
	public void setMaxCols(int cols)
	{
		int m = clamp(cols, BankGeometry.MIN_COLS, BankGeometry.MAX_COLS);
		if (m != maxCols)
		{
			maxCols = m;
			setVisibleCols(visibleCols);
		}
	}

	public int getMaxCols()
	{
		return maxCols;
	}

	/**
	 * The current {@code (cols, rows, stripRows)} geometry. Every rect the view exposes comes from
	 * here. Recomputed only when one of the three actually changes, so a frame that drains the action
	 * queue before it reads any rect still sees exactly one geometry.
	 */
	public BankGeometry geometry()
	{
		return geom();
	}

	private BankGeometry geom()
	{
		final int stripRows = BankGeometry.stripRowsFor(visibleCols, getStripLength());
		if (geom.getCols() != visibleCols || geom.getRows() != visibleRows
			|| geom.getStripRows() != stripRows)
		{
			geom = BankGeometry.of(visibleCols, visibleRows, stripRows);
		}
		return geom;
	}

	/**
	 * Grid rows a window of this pixel height would hold, at the strip height the current tab count
	 * needs. The AWT-side resize drag works in pixels and posts them here, since the strip row count
	 * is client-thread state.
	 */
	public int rowsForHeight(int pixelHeight)
	{
		return BankGeometry.rowsForHeight(pixelHeight, geom().getStripRows());
	}

	/** Canonical id -> display name, used for placeholders whose id is not in any snapshot. */
	public void setKnownNames(Map<Integer, String> names)
	{
		this.knownNames = new HashMap<>(names);
		invalidate();
	}

	/** Canonical id -> GE unit price. Rebuilt whenever snapshots rebuild; does not force a redraw. */
	public void setUnitPrices(Map<Integer, Integer> prices)
	{
		this.unitPrices = new HashMap<>(prices);
	}

	/** GE unit price for a canonical id, or 0 when unknown. */
	public int unitPrice(int canonicalId)
	{
		return unitPrices.getOrDefault(canonicalId, 0);
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

	/** Flips between the tab view and the by-storage view. Bound to the bottom bar's mode button. */
	public void toggleMode()
	{
		setMode(mode == ViewMode.TABS ? ViewMode.BY_STORAGE : ViewMode.TABS);
	}

	public int getActiveTab()
	{
		return activeTab;
	}

	public void setActiveTab(int tabIndex)
	{
		if (tabIndex == activeTab)
		{
			invalidate();
			return;
		}

		activeTab = tabIndex;
		invalidate();
		scroll = rememberedScrollForActiveTab();
		hScroll = rememberedHScrollForActiveTab();
	}

	private int rememberedScrollForActiveTab()
	{
		if (!search.isEmpty())
		{
			return 0;
		}
		return activeTab < 0 ? allTabScroll : scrollByTab.getOrDefault(layout.getTab(activeTab), 0);
	}

	private int rememberedHScrollForActiveTab()
	{
		if (!search.isEmpty())
		{
			return 0;
		}
		return activeTab < 0 ? allTabHScroll : hScrollByTab.getOrDefault(layout.getTab(activeTab), 0);
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
		boolean wasEmpty = search.isEmpty();
		this.search = text == null ? "" : text;
		this.searchLower = this.search.toLowerCase(Locale.ROOT);
		invalidate();

		if (wasEmpty && !search.isEmpty())
		{
			scroll = 0;
			hScroll = 0;
		}
		else if (!wasEmpty && search.isEmpty())
		{
			scroll = rememberedScrollForActiveTab();
			hScroll = rememberedHScrollForActiveTab();
		}
		else if (!search.isEmpty())
		{
			scroll = 0;
			hScroll = 0;
		}
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
			if (!search.isEmpty())
			{
				y = buildSearchRows(newRows, newSlots, y);
			}
			else if (activeTab >= 0 && layout.getTab(activeTab) != null)
			{
				y = buildTabGrid(newRows, newSlots, activeTab, layout.getTab(activeTab), true, y);
			}
			else
			{
				// A divider labels every non-empty tab's group in the All view, except when there is
				// only one tab: a single unlabelled grid reads better than a lone divider.
				final boolean emitDividers = layout.getTabs().size() > 1;
				for (int t = 0; t < layout.getTabs().size(); t++)
				{
					BankTab tab = layout.getTab(t);
					if (tab.itemCount() == 0)
					{
						continue;
					}

					if (emitDividers)
					{
						newRows.add(new BankRow(BankRow.Kind.HEADER, y, BankGeometry.HEADER_H, tab.getName(),
							null, Collections.emptyList(), t, tab.getIconItemId()));
						y += BankGeometry.HEADER_H;
					}
					y = buildTabGrid(newRows, newSlots, t, tab, false, y);
				}
			}
		}
		else
		{
			final Set<Integer> tabFilter = storageModeTabFilter();
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
					if (tabFilter != null && !tabFilter.contains(item.getCanonicalId()))
					{
						continue;
					}
					List<SlotSource> src = Collections.singletonList(new SlotSource(snap.getName(), item.getQuantity()));
					built.add(new BankSlot(item.getCanonicalId(), item.getName(), item.getQuantity(),
						item.isStackable(), false, src, -1, -1));
				}

				// An empty storage still gets a header when the player asked to see empty storages and
				// is not filtering; anything else with no matching items is skipped entirely. A tab
				// filter never keeps an empty-after-filter storage: "no items of mine in here" is not
				// the same thing as "this storage itself is empty".
				final boolean keepAsEmptyStorage =
					emptyStorage && showEmptyStorages && search.isEmpty() && tabFilter == null;
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

		// The virtual width is the widest row built - a tab laid out wider than the window, in the
		// All view the widest of them - and never less than the viewport, so a window wider than
		// every tab simply has nothing to scroll.
		int widest = visibleCols;
		for (BankRow row : newRows)
		{
			if (row.getKind() == BankRow.Kind.ITEMS)
			{
				widest = Math.max(widest, row.getSlots().size());
			}
		}
		contentWidth = widest * BankGeometry.SLOT_W;

		scroll = clamp(scroll, 0, getMaxScroll());
		hScroll = clamp(hScroll, 0, getMaxHScroll());
		dirty = false;
	}

	/**
	 * Lays out one tab's grid at the tab's own layout width, {@link BankTab#getCols()}: every row
	 * holds exactly that many cells (occupied, placeholder, or empty), whatever the window is doing,
	 * which is why resizing never moves an item.
	 *
	 * <p>{@code active} marks the one tab the window is showing on its own. That tab renders at least
	 * {@link #visibleRows} rows past the last occupied one, so the whole viewport is a drop target,
	 * and pads each row out to the window's column count when the window is wider than the tab: those
	 * extra cells are {@link BankSlot#beyondWidth} blanks, and a drop onto one widens the tab. The All
	 * view instead gives every tab exactly its own width and just enough rows to hold its items.
	 */
	private int buildTabGrid(List<BankRow> rowsOut, List<BankSlot> slotsOut, int tabIndex, BankTab tab,
		boolean active, int y)
	{
		final int layoutCols = tab.getCols();
		final int gridCols = active ? Math.max(layoutCols, visibleCols) : layoutCols;
		int occupiedRows = (tab.maxOccupiedIndex() + 1 + layoutCols - 1) / layoutCols;
		int gridRows = active ? Math.max(occupiedRows + 1, visibleRows) : Math.max(occupiedRows, 1);

		for (int r = 0; r < gridRows; r++)
		{
			List<BankSlot> cells = new ArrayList<>(gridCols);
			for (int c = 0; c < gridCols; c++)
			{
				if (c >= layoutCols)
				{
					cells.add(BankSlot.beyondWidth(tabIndex, r, c));
					continue;
				}
				int i = r * layoutCols + c;
				Integer id = tab.itemAt(i);
				BankSlot cell = id == null ? null : buildTabSlot(id, tabIndex, i);
				cells.add(cell != null ? cell : BankSlot.empty(tabIndex, i));
			}
			rowsOut.add(new BankRow(BankRow.Kind.ITEMS, y, BankGeometry.SLOT_H, null, null, cells, tabIndex, -1));
			slotsOut.addAll(cells);
			y += BankGeometry.SLOT_H;
		}
		return y;
	}

	/** Builds the slot for an occupied cell, or null when it is a dropped/ignored placeholder. */
	private BankSlot buildTabSlot(int id, int tabIndex, int indexInTab)
	{
		if (ownedIds.contains(id))
		{
			ItemSnapshot first = firstSnapshotById.get(id);
			String name = first != null ? first.getName() : nameFor(id);
			boolean stackable = first != null && first.isStackable();
			long qty = qtyById.getOrDefault(id, 0L);
			List<SlotSource> src = sourcesById.getOrDefault(id, Collections.emptyList());
			return new BankSlot(id, name, qty, stackable, false, src, tabIndex, indexInTab);
		}

		if (!placeholdersEnabled || layout.isPlaceholderIgnored(id))
		{
			return null;
		}

		return new BankSlot(id, nameFor(id), 0, false, true, Collections.emptyList(), tabIndex, indexInTab);
	}

	/**
	 * One result per distinct id (card 27): a search result answers "do I own this and where is it",
	 * and N identical cells for a duplicated item is noise that pushes real hits off the screen. The
	 * first copy in strip order wins, so the result cell still carries a real
	 * {@code (tabIndex, indexInTab)} origin and the drag-to-tab copy gesture keeps working. Deliberate
	 * asymmetry with the All view, which shows every copy under its own tab's divider.
	 */
	private int buildSearchRows(List<BankRow> rowsOut, List<BankSlot> slotsOut, int y)
	{
		List<BankSlot> built = new ArrayList<>();
		Set<Integer> emitted = new LinkedHashSet<>();
		List<Integer> tabsToScan = new ArrayList<>();
		if (activeTab >= 0 && layout.getTab(activeTab) != null)
		{
			tabsToScan.add(activeTab);
		}
		else
		{
			for (int t = 0; t < layout.getTabs().size(); t++)
			{
				tabsToScan.add(t);
			}
		}

		for (int t : tabsToScan)
		{
			BankTab tab = layout.getTab(t);
			List<Integer> tabSlots = tab.getSlots();
			for (int i = 0; i < tabSlots.size(); i++)
			{
				Integer id = tabSlots.get(i);
				if (id == null)
				{
					continue;
				}
				BankSlot slot = buildTabSlot(id, t, i);
				if (slot != null && matchesSearch(slot.getName()) && emitted.add(id))
				{
					built.add(slot);
				}
			}
		}

		return appendItemRows(rowsOut, slotsOut, built, y);
	}

	/**
	 * Ids to show in {@code BY_STORAGE} mode: {@code null} when the All tab is active (no filter -
	 * everything owned, today's behaviour), otherwise the ids present in the active layout tab's
	 * slots, so storage mode groups by storage the same items the active tab's grid would show.
	 */
	private Set<Integer> storageModeTabFilter()
	{
		BankTab tab = activeTabRef();
		return tab == null ? null : new LinkedHashSet<>(tab.itemIds());
	}

	private String nameFor(int id)
	{
		return knownNames.getOrDefault(id, UNKNOWN_ITEM_PREFIX + id);
	}

	private boolean matchesSearch(String name)
	{
		return searchLower.isEmpty() || (name != null && name.toLowerCase(Locale.ROOT).contains(searchLower));
	}

	private int appendItemRows(List<BankRow> rowsOut, List<BankSlot> slotsOut, List<BankSlot> built, int y)
	{
		for (int i = 0; i < built.size(); i += visibleCols)
		{
			List<BankSlot> chunk = built.subList(i, Math.min(i + visibleCols, built.size()));
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

	/** Non-empty, non-placeholder cells currently built. Used by BY_STORAGE and search. */
	public int getItemCount()
	{
		int count = 0;
		for (BankSlot slot : slots)
		{
			if (!slot.isEmpty() && !slot.isPlaceholder())
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Distinct owned ids across every tab. What the title bar shows. Card 27: duplicated ids count
	 * once - the title bar answers "what do I own", and a player who files one item under two tabs
	 * still owns exactly one of it.
	 */
	public int getTotalItemCount()
	{
		Set<Integer> distinct = new HashSet<>();
		for (BankTab tab : layout.getTabs())
		{
			for (int id : tab.itemIds())
			{
				if (ownedIds.contains(id))
				{
					distinct.add(id);
				}
			}
		}
		return distinct.size();
	}

	/**
	 * Distinct unowned, non-ignored ids across every tab. Ignored ids are excluded. Card 27:
	 * duplicated ids count once, for the same reason as {@link #getTotalItemCount()}.
	 */
	public int getTotalPlaceholderCount()
	{
		Set<Integer> distinct = new HashSet<>();
		for (BankTab tab : layout.getTabs())
		{
			for (int id : tab.itemIds())
			{
				if (!ownedIds.contains(id) && !layout.isPlaceholderIgnored(id))
				{
					distinct.add(id);
				}
			}
		}
		return distinct.size();
	}

	public int getTabCount()
	{
		return layout.getTabs().size();
	}

	/**
	 * Total GE value of what is currently owned in the given tab, or of every tab when
	 * {@code tabIndex == -1}. Placeholders contribute nothing because {@link #qtyById} only holds
	 * owned ids. Accumulated in {@code long}: a UIM's stack of a high-value item overflows
	 * {@code int} easily.
	 *
	 * <p>Card 27: the {@code -1} (all-tabs) roll-up sums each distinct id once across the whole
	 * layout, so a duplicated item's value is not double-counted; a real tab's own value is already
	 * id-set based per tab and needs no change.
	 */
	public long tabValue(int tabIndex)
	{
		if (tabIndex == -1)
		{
			Set<Integer> distinct = new HashSet<>();
			for (BankTab tab : layout.getTabs())
			{
				distinct.addAll(tab.itemIds());
			}
			long total = 0;
			for (int id : distinct)
			{
				Long qty = qtyById.get(id);
				if (qty != null)
				{
					total += qty * unitPrice(id);
				}
			}
			return total;
		}
		BankTab tab = layout.getTab(tabIndex);
		return tab == null ? 0 : tabValueOf(tab);
	}

	private long tabValueOf(BankTab tab)
	{
		long total = 0;
		for (int id : tab.itemIds())
		{
			Long qty = qtyById.get(id);
			if (qty != null)
			{
				total += qty * unitPrice(id);
			}
		}
		return total;
	}

	/**
	 * The title bar's base text, before the GE value suffix (a RuneLite-tier, config-gated concern
	 * left to {@code BankOverlay}): the active tab's name in TABS mode, "Bankless Bank" for the All
	 * tab and for {@code BY_STORAGE} mode. A tab's name is a plain field on {@link BankTab}, so a
	 * rename shows up here as soon as the layout is rebuilt.
	 */
	public String titleBaseName()
	{
		if (mode == ViewMode.TABS && activeTab != -1)
		{
			final BankTab tab = layout.getTab(activeTab);
			return tab != null ? tab.getName() : "Tab " + (activeTab + 1);
		}
		return "Bankless Bank";
	}

	/**
	 * The value the title should show: {@link #tabValue} of the active tab in TABS mode (every tab
	 * when All is active); in {@code BY_STORAGE} mode, the value of everything in {@link #snapshots}
	 * - or, with a layout tab active, only the ids that tab's grid is filtered down to, matching
	 * {@link #storageModeTabFilter()}. Ignores the search filter, matching the real bank's title,
	 * whose value does not change as you type.
	 */
	public long titleValue()
	{
		if (mode == ViewMode.BY_STORAGE)
		{
			final Set<Integer> tabFilter = storageModeTabFilter();
			long total = 0;
			for (StorageSnapshot snap : snapshots)
			{
				for (ItemSnapshot item : snap.getItems())
				{
					if (item.getQuantity() <= 0)
					{
						continue;
					}
					if (tabFilter != null && !tabFilter.contains(item.getCanonicalId()))
					{
						continue;
					}
					total += item.getQuantity() * unitPrice(item.getCanonicalId());
				}
			}
			return total;
		}
		return tabValue(activeTab);
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

	/**
	 * Adds an item the player need not own to the end of the tab the window is showing - the main
	 * tab when the All view is active - and scrolls it into sight. This is what the bottom bar's add
	 * button does once the game's item search hands back an id.
	 *
	 * <p>Card 27: an id already in the tab the window is showing is not added twice there - the view
	 * jumps to it instead. An id that lives in some <i>other</i> tab is still added here as a second
	 * copy, since with duplication "jump to wherever it already is" would otherwise make the feature
	 * unreachable from the add button once an item has any copy at all. Either way the search filter
	 * is cleared and the by-storage view flips back to tabs first, since neither shows a tab grid the
	 * new slot could appear in.
	 *
	 * <p>With placeholders turned off the item is still added, but nothing renders it while it is
	 * unowned and the next {@link #syncLayout()} blanks its slot again - manual adds are a
	 * placeholder feature.
	 *
	 * @return true when the layout changed and needs saving
	 */
	public boolean addItem(int itemId)
	{
		if (itemId <= 0)
		{
			return false;
		}

		if (mode != ViewMode.TABS)
		{
			setMode(ViewMode.TABS);
		}
		if (!search.isEmpty())
		{
			clearSearch();
		}
		setSearchFocused(false);

		final int target = activeTab >= 0 ? activeTab : layout.indexOfMainTab();
		final BankTab tab = layout.getTab(target);
		if (tab != null && tab.contains(itemId))
		{
			setActiveTab(target);
			scrollToItem(itemId);
			return false;
		}

		if (!layout.addItem(itemId, target))
		{
			return false;
		}

		setActiveTab(target);
		invalidate();
		scrollToItem(itemId);
		return true;
	}

	/**
	 * Scrolls both axes just far enough to bring the first cell holding this id inside the viewport.
	 * A no-op when nothing renders the id (it is hidden as an ignored placeholder, or placeholders
	 * are off and it is unowned) or when it is already fully visible.
	 *
	 * @return true when a scroll position changed
	 */
	public boolean scrollToItem(int itemId)
	{
		rebuild();

		for (BankRow row : rows)
		{
			if (row.getKind() != BankRow.Kind.ITEMS)
			{
				continue;
			}
			final List<BankSlot> cells = row.getSlots();
			for (int col = 0; col < cells.size(); col++)
			{
				final BankSlot cell = cells.get(col);
				if (cell == null || cell.isEmpty() || cell.getCanonicalId() != itemId)
				{
					continue;
				}

				final int beforeV = scroll;
				final int beforeH = hScroll;
				setScroll(clamp(scroll, row.getY() + row.getHeight() - getViewportHeight(), row.getY()));
				final int cellX = col * BankGeometry.SLOT_W;
				setHScroll(clamp(hScroll, cellX + BankGeometry.SLOT_W - getViewportWidth(), cellX));
				return scroll != beforeV || hScroll != beforeH;
			}
		}

		return false;
	}

	/**
	 * layout.sync(getOwnedIds(), placeholdersEnabled). Returns true when the layout changed.
	 *
	 * <p>Card 27: menu rows are addressed by {@code (tabIndex, slotIndex)} rather than by item id, so
	 * a tab pruned out from under an open menu (which sync does when placeholders are off and a tab's
	 * last item stops being owned) would silently re-point every row at whichever tab shifted into
	 * that index - "Remove copy" would then delete a cell the player never clicked. Closing the menu
	 * whenever the strip changes shape is the honest response: what the player right-clicked is no
	 * longer where it was. An ordinary sync that only adds or blanks items leaves the menu alone.
	 */
	public boolean syncLayout()
	{
		final int tabsBefore = layout.getTabs().size();
		boolean changed = layout.sync(ownedIds, placeholdersEnabled);
		if (changed)
		{
			if (menuOpen && layout.getTabs().size() != tabsBefore)
			{
				closeMenu();
			}
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
		recordScroll();
	}

	private void recordHScroll()
	{
		if (!search.isEmpty())
		{
			return;
		}
		if (activeTab < 0)
		{
			allTabHScroll = hScroll;
			return;
		}
		BankTab tab = layout.getTab(activeTab);
		if (tab != null)
		{
			hScrollByTab.put(tab, hScroll);
		}
	}

	private void recordScroll()
	{
		if (!search.isEmpty())
		{
			return;
		}
		if (activeTab < 0)
		{
			allTabScroll = scroll;
			return;
		}
		BankTab tab = layout.getTab(activeTab);
		if (tab != null)
		{
			scrollByTab.put(tab, scroll);
		}
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

	// ---- horizontal ----

	public int getHScroll()
	{
		return hScroll;
	}

	/** Clamps into {@code [0, getMaxHScroll()]} and remembers it for the active tab. */
	public void setHScroll(int px)
	{
		hScroll = clamp(px, 0, getMaxHScroll());
		recordHScroll();
	}

	public void hScrollBy(int px)
	{
		setHScroll(hScroll + px);
	}

	/** How far the grid can scroll sideways: zero unless a tab is laid out wider than the window. */
	public int getMaxHScroll()
	{
		return Math.max(0, contentWidth - getViewportWidth());
	}

	public int getContentWidth()
	{
		return contentWidth;
	}

	public int getViewportWidth()
	{
		return visibleCols * BankGeometry.SLOT_W;
	}

	public void hScrollThumbTo(int localX)
	{
		Rectangle track = hScrollTrackRect();
		Rectangle thumbNow = hScrollThumbRect();
		int range = track.width - thumbNow.width;
		if (range <= 0 || getMaxHScroll() <= 0)
		{
			setHScroll(0);
			return;
		}

		double t = (localX - track.x - thumbNow.width / 2.0) / range;
		setHScroll((int) Math.round(t * getMaxHScroll()));
	}

	public void scrollThumbTo(int localY)
	{
		Rectangle track = scrollTrackRect();
		Rectangle thumbNow = scrollThumbRect();
		int range = track.height - thumbNow.height;
		if (range <= 0 || getMaxScroll() <= 0)
		{
			setScroll(0);
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
		return geom().size();
	}

	public Rectangle resizeGripRect()
	{
		return geom().resizeGrip();
	}

	public Rectangle titleBarRect()
	{
		return geom().titleBar();
	}

	public Rectangle closeButtonRect()
	{
		return geom().closeButton();
	}

	/** Width of one tab button at the current window width and strip length. */
	public int tabWidth()
	{
		return geom().tabWidth(getStripLength());
	}

	public Rectangle tabRect(int stripIndex)
	{
		return geom().tabAt(stripIndex, getStripLength());
	}

	public Rectangle gridRect()
	{
		return geom().grid();
	}

	/** The whole bottom button bar. */
	public Rectangle bottomBarRect()
	{
		return geom().bottomBar();
	}

	/** The search text field, between the search button and the view-mode button. */
	public Rectangle searchRect()
	{
		return geom().searchBox();
	}

	public Rectangle searchButtonRect()
	{
		return geom().searchButton();
	}

	public Rectangle modeButtonRect()
	{
		return geom().modeButton();
	}

	/** The bottom bar's "add an item" button, which opens the game's item search. */
	public Rectangle addButtonRect()
	{
		return geom().addButton();
	}

	/** The whole scrollbar column, arrow buttons included. */
	public Rectangle scrollbarRect()
	{
		return geom().scrollbar();
	}

	public Rectangle scrollUpRect()
	{
		return geom().scrollUp();
	}

	public Rectangle scrollDownRect()
	{
		return geom().scrollDown();
	}

	/** The draggable stretch of scrollbar between the two arrow buttons. */
	public Rectangle scrollTrackRect()
	{
		return geom().scrollTrack();
	}

	public Rectangle scrollThumbRect()
	{
		return BankGeometry.thumb(scrollTrackRect(), scroll, getMaxScroll(), contentHeight, getViewportHeight());
	}

	/**
	 * The whole horizontal scrollbar row, arrow buttons included. Overlays the bottom of the grid;
	 * only meaningful while {@link #isHScrollbarVisible()}.
	 */
	public Rectangle hScrollbarRect()
	{
		return geom().hScrollbar();
	}

	public Rectangle hScrollLeftRect()
	{
		return geom().hScrollLeft();
	}

	public Rectangle hScrollRightRect()
	{
		return geom().hScrollRight();
	}

	public Rectangle hScrollTrackRect()
	{
		return geom().hScrollTrack();
	}

	public Rectangle hScrollThumbRect()
	{
		return BankGeometry.hThumb(hScrollTrackRect(), hScroll, getMaxHScroll(), contentWidth, getViewportWidth());
	}

	/**
	 * Whether the horizontal scrollbar should be drawn and hit-tested this frame. It overlays the
	 * bottom of the grid rather than reserving its own row, so it only exists at all while some row
	 * is actually wider than the viewport - true only in tab-grid mode, since search results and the
	 * by-storage view are always laid out dense at {@link #visibleCols}, and true for the All view
	 * only when at least one tab in it overflows.
	 */
	public boolean isHScrollbarVisible()
	{
		return getMaxHScroll() > 0;
	}

	/**
	 * Whether the vertical scrollbar should be drawn and hit-tested this frame. Unlike the
	 * horizontal bar it always has its own reserved column in the geometry - grid rects never move
	 * when it is hidden, it simply is not painted or hit-tested, so a short tab (or a search result,
	 * or the by-storage view) with nothing to scroll gets a clean right edge instead of an empty
	 * track. Recomputed from {@link #getMaxScroll()}, which already reflects the active tab/mode/
	 * search and the current row count, so a tab switch, a search, or a resize all pick it up for
	 * free on the next rebuild.
	 */
	public boolean isVScrollbarVisible()
	{
		return getMaxScroll() > 0;
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
				Rectangle r = geom().slotInRow(rowLocalY, col);
				r.x -= hScroll;
				return r;
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
					int col = Math.min(row.getSlots().size(), visibleCols - 1);
					Rectangle r = geom().slotInRow(rowLocalY, col);
					r.x -= hScroll;
					return r;
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
		if (resizeGripRect().contains(x, y))
		{
			return Hit.of(Hit.Type.RESIZE_GRIP, -1);
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

		// The horizontal scrollbar overlays the bottom of the grid rather than reserving its own row,
		// so it must be hit-tested before the grid/slots whenever it is showing, or its clicks would
		// fall through to whatever slot sits underneath it.
		if (isHScrollbarVisible())
		{
			if (hScrollLeftRect().contains(x, y))
			{
				return Hit.of(Hit.Type.HSCROLL_LEFT, -1);
			}
			if (hScrollRightRect().contains(x, y))
			{
				return Hit.of(Hit.Type.HSCROLL_RIGHT, -1);
			}
			if (hScrollThumbRect().contains(x, y))
			{
				return Hit.of(Hit.Type.HSCROLL_THUMB, -1);
			}
			if (hScrollTrackRect().contains(x, y))
			{
				return Hit.of(Hit.Type.HSCROLL_TRACK, -1);
			}
		}

		if (gridRect().contains(x, y))
		{
			for (int i = 0; i < slots.size(); i++)
			{
				if (isSlotVisible(i) && slotRect(i).contains(x, y))
				{
					BankSlot s = slots.get(i);
					return s.isEmpty() ? Hit.emptySlot(i, s) : Hit.slot(i, s);
				}
			}
			return Hit.of(Hit.Type.GRID_EMPTY, -1);
		}

		if (isVScrollbarVisible())
		{
			if (scrollUpRect().contains(x, y))
			{
				return Hit.of(Hit.Type.SCROLL_UP, -1);
			}
			if (scrollDownRect().contains(x, y))
			{
				return Hit.of(Hit.Type.SCROLL_DOWN, -1);
			}
			if (scrollThumbRect().contains(x, y))
			{
				return Hit.of(Hit.Type.SCROLL_THUMB, -1);
			}
			if (scrollTrackRect().contains(x, y))
			{
				return Hit.of(Hit.Type.SCROLL_TRACK, -1);
			}
		}

		if (searchButtonRect().contains(x, y))
		{
			return Hit.of(Hit.Type.SEARCH_BUTTON, -1);
		}
		if (modeButtonRect().contains(x, y))
		{
			return Hit.of(Hit.Type.MODE_BUTTON, -1);
		}
		if (addButtonRect().contains(x, y))
		{
			return Hit.of(Hit.Type.ADD_BUTTON, -1);
		}
		if (searchRect().contains(x, y))
		{
			return Hit.of(Hit.Type.SEARCH, -1);
		}

		return Hit.none();
	}

	/**
	 * The hovered slot, or null. Lets the overlay build lines/prices the pure tier cannot format
	 * (GE value needs {@code QuantityFormatter}, which lives in the RuneLite tier).
	 */
	public BankSlot slotAt(int x, int y)
	{
		Hit hit = hitTest(x, y);
		return hit.getType() == Hit.Type.SLOT ? hit.getSlot() : null;
	}

	/**
	 * Tooltip lines for the point, or an empty list. For a slot, line 0 is the item name, then one
	 * line per source (the overlay inserts a GE price line between them, since formatting a price
	 * needs {@code QuantityFormatter}). The resize grip and mode button get a single fixed line.
	 */
	public List<String> tooltipLines(int x, int y)
	{
		Hit hit = hitTest(x, y);

		if (hit.getType() == Hit.Type.SLOT && hit.getSlot() != null)
		{
			BankSlot slot = hit.getSlot();
			List<String> lines = new ArrayList<>();
			lines.add(slot.getName());
			for (SlotSource source : slot.getSources())
			{
				lines.add(source.getStorageName() + ": " + source.getQuantity());
			}
			return lines;
		}

		if (hit.getType() == Hit.Type.RESIZE_GRIP)
		{
			return Collections.singletonList("Drag to resize");
		}
		if (hit.getType() == Hit.Type.MODE_BUTTON)
		{
			return Collections.singletonList("Tabs / by storage");
		}
		if (hit.getType() == Hit.Type.ADD_BUTTON)
		{
			return Collections.singletonList("Add an item");
		}

		return Collections.emptyList();
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
		updateDropSlot(x, y);
	}

	public void updateDrag(int x, int y)
	{
		if (!dragging)
		{
			return;
		}
		dragPoint = new Point(x, y);
		updateDropSlot(x, y);
	}

	private void updateDropSlot(int x, int y)
	{
		// Grid cells are not drop targets while a search filter is active or in BY_STORAGE mode
		// (neither a searched result's nor a storage row's position is a real slot), so there is
		// nothing to highlight there; only the tab strip is a valid target then.
		if (isStripOnlyDrag())
		{
			dropSlotIndex = -1;
			return;
		}

		Hit hit = hitTest(x, y);
		dropSlotIndex = (hit.getType() == Hit.Type.SLOT || hit.getType() == Hit.Type.SLOT_EMPTY)
			? hit.getIndex() : -1;
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

		if (isStripOnlyDrag())
		{
			result = endStripOnlyDrag(hitTest(x, y), dragSlot.getCanonicalId());
		}
		else
		{
			final BankTab activeRef = activeTabRef();
			Hit hit = hitTest(x, y);
			// Card 27: grid drags move exactly the copy the drag started on, resolved by cell
			// (tabIndex, indexInTab), never by re-resolving the id - the whole point of a copy being
			// addressed by its slot, not its id.
			final int fromTab = dragSlot.getTabIndex();
			int fromSlot = dragSlot.getIndexInTab();

			if (hit.getType() == Hit.Type.SLOT_EMPTY && hit.getSlot() != null && hit.getSlot().getTabIndex() != -1)
			{
				BankSlot slot = hit.getSlot();
				int targetSlot;
				if (slot.isBeyondWidth())
				{
					// Widening re-indexes every id in the tab (index i moves to rowOf(i) * newCols +
					// colOf(i)), so a source slot in the same tab is stale the moment widenForDrop
					// returns. Remember the source's (row, col) at the old width and re-resolve it at
					// the new one; without this a drag from any row below the first moves whichever
					// id happens to land on the old flat index, or silently nothing at all.
					final BankTab srcTab = layout.getTab(fromTab);
					final int srcRow = srcTab == null ? -1 : srcTab.rowOf(fromSlot);
					final int srcCol = srcTab == null ? -1 : srcTab.colOf(fromSlot);
					targetSlot = widenForDrop(slot.getTabIndex(), slot.getGridRow(), slot.getGridCol());
					if (targetSlot >= 0 && srcTab != null && fromTab == slot.getTabIndex())
					{
						fromSlot = srcTab.indexAt(srcRow, srcCol);
					}
				}
				else
				{
					targetSlot = slot.getIndexInTab();
				}
				if (targetSlot >= 0 && layout.moveSlot(fromTab, fromSlot, slot.getTabIndex(), targetSlot))
				{
					result = new DropTarget(DropTarget.Type.SLOT, slot.getTabIndex(), targetSlot);
				}
				syncActiveTab(activeRef);
			}
			else if (hit.getType() == Hit.Type.SLOT && hit.getSlot() != null)
			{
				BankSlot slot = hit.getSlot();
				// "Not the same cell" rather than "not the same id": dropping a copy onto a different
				// cell holding the same id reaches moveSlot, which refuses it (§3.4).
				boolean sameCell = slot.getTabIndex() == fromTab && slot.getIndexInTab() == fromSlot;
				if (!sameCell && slot.getTabIndex() != -1)
				{
					if (layout.moveSlot(fromTab, fromSlot, slot.getTabIndex(), slot.getIndexInTab()))
					{
						result = new DropTarget(DropTarget.Type.SLOT, slot.getTabIndex(), slot.getIndexInTab());
					}
					syncActiveTab(activeRef);
				}
			}
			else if (hit.getType() == Hit.Type.TAB)
			{
				// Strip index 0 is the fixed All button, not a real tab; dropping an item there sends
				// it to the main tab, wherever the main tab currently sits in the strip.
				int tabIndex = hit.getIndex() == 0 ? layout.indexOfMainTab() : hit.getIndex() - 1;
				if (tabIndex >= 0 && tabIndex < getTabCount())
				{
					if (layout.moveSlotToTab(fromTab, fromSlot, tabIndex))
					{
						BankTab tab = layout.getTab(tabIndex);
						result = new DropTarget(DropTarget.Type.TAB, tabIndex, tab == null ? 0 : tab.maxOccupiedIndex());
					}
					syncActiveTab(activeRef);
				}
			}
			else if (hit.getType() == Hit.Type.TAB_PLUS)
			{
				int newIndex = layout.createTabFrom(fromTab, fromSlot);
				if (newIndex != -1)
				{
					syncActiveTab(activeRef);
					setActiveTab(newIndex);
					result = new DropTarget(DropTarget.Type.NEW_TAB, newIndex, 0);
				}
			}
			else if (hit.getType() == Hit.Type.GRID_EMPTY)
			{
				final int targetTab = activeTab == -1 ? layout.indexOfMainTab() : activeTab;
				if (layout.moveSlotToTab(fromTab, fromSlot, targetTab))
				{
					final BankTab tab = layout.getTab(targetTab);
					result = new DropTarget(DropTarget.Type.TAB, targetTab, tab == null ? 0 : tab.maxOccupiedIndex());
				}
				syncActiveTab(activeRef);
			}
		}

		if (result.getType() != DropTarget.Type.CANCEL)
		{
			invalidate();
		}

		clearDragState();
		return result;
	}

	/**
	 * Widens a tab so that it reaches the column a blank beyond-width cell was drawn at, and returns
	 * the slot index that cell becomes. Every id already in the tab is re-indexed by the widening, so
	 * each keeps the {@code (row, col)} it was drawn at and nothing appears to move.
	 *
	 * @return the flat slot index to drop onto, or -1 when the tab could not be widened
	 */
	private int widenForDrop(int tabIndex, int gridRow, int gridCol)
	{
		final BankTab tab = layout.getTab(tabIndex);
		if (tab == null || !layout.widenTab(tabIndex, gridCol + 1))
		{
			return -1;
		}
		return tab.indexAt(gridRow, gridCol);
	}

	/**
	 * True while the only legal drop target for an item drag is the tab strip: a search filter is
	 * active, or the view is in {@code BY_STORAGE} mode. Both build dense rows whose cells carry no
	 * real slot ({@code BY_STORAGE} cells are built with {@code tabIndex/indexInTab == -1}, and a
	 * search result's grid position bears no relation to where the item actually sits), so there is
	 * nothing for a grid drop to mean.
	 */
	private boolean isStripOnlyDrag()
	{
		return mode != ViewMode.TABS || !search.isEmpty();
	}

	/**
	 * Resolves a drop started from a search result or a {@code BY_STORAGE} row. Valid targets are the
	 * tab buttons in the strip (copies onto that tab, matching {@link BankLayout#copyItemToTab}) and
	 * the plus button (copies into a new tab, matching {@link BankLayout#createTabWith}); the All
	 * button, grid cells (neither a search result's nor a storage row's position is a real slot) and
	 * anything else cancel. Dropping onto a tab that already holds the id is a no-op.
	 *
	 * <p>Card 27 board decision: every strip-only drag (search results <b>and</b> storage-mode drags,
	 * both routed here via {@link #isStripOnlyDrag()}) now <b>copies</b> onto the target tab rather
	 * than moving - search results are found across every tab, so "move it out of wherever it was"
	 * was always the surprising reading, and this doubles as a fast bulk-filing gesture. Grid drags
	 * (below, in {@link #endDrag}) remain moves.
	 */
	private DropTarget endStripOnlyDrag(Hit hit, int itemId)
	{
		if (hit.getType() == Hit.Type.TAB && hit.getIndex() >= 1)
		{
			int tabIndex = hit.getIndex() - 1;
			BankTab target = layout.getTab(tabIndex);
			if (target == null || target.contains(itemId))
			{
				return DropTarget.cancel();
			}

			final BankTab activeRef = activeTabRef();
			int landedAt = layout.copyItemToTab(itemId, tabIndex);
			if (landedAt < 0)
			{
				return DropTarget.cancel();
			}
			syncActiveTab(activeRef);
			return new DropTarget(DropTarget.Type.TAB, tabIndex, landedAt);
		}

		if (hit.getType() == Hit.Type.TAB_PLUS)
		{
			final BankTab activeRef = activeTabRef();
			int newIndex = layout.createTabWith(itemId);
			if (newIndex == -1)
			{
				return DropTarget.cancel();
			}
			syncActiveTab(activeRef);
			setActiveTab(newIndex);
			return new DropTarget(DropTarget.Type.NEW_TAB, newIndex, 0);
		}

		// The All button (TAB index 0), grid cells (SLOT/SLOT_EMPTY) and everywhere else are not
		// valid drop targets while a search filter is active or storage mode is showing.
		return DropTarget.cancel();
	}

	/**
	 * The strip index the current item drag would actually drop onto, or -1. The overlay paints its
	 * tab highlight from this rather than from a bare hit test, so the highlight and
	 * {@link #endDrag} agree: while the drag is strip-only (a search filter is active, or the view is
	 * in {@code BY_STORAGE} mode) only real tab buttons and the plus button accept a drop, the All
	 * button does not, and dropping onto the tab the item already lives in is a no-op.
	 */
	public int dropTabStripIndex(int x, int y)
	{
		if (!dragging || dragSlot == null)
		{
			return -1;
		}

		Hit hit = hitTest(x, y);
		if (hit.getType() == Hit.Type.TAB_PLUS)
		{
			return hit.getIndex();
		}
		if (hit.getType() != Hit.Type.TAB)
		{
			return -1;
		}

		if (!isStripOnlyDrag())
		{
			// Card 27: a grid drag onto a tab that already holds the id is refused by moveSlotToTab,
			// so the highlight must refuse it too or the strip would promise a drop that snaps back.
			// Strip index 0 is the All button, which for a grid drag means the main tab.
			final int gridTarget = hit.getIndex() == 0 ? layout.indexOfMainTab() : hit.getIndex() - 1;
			final BankTab gridTab = layout.getTab(gridTarget);
			if (gridTab == null || (gridTarget != dragSlot.getTabIndex()
				&& gridTab.contains(dragSlot.getCanonicalId())))
			{
				return -1;
			}
			return hit.getIndex();
		}

		int tabIndex = hit.getIndex() - 1;
		BankTab tab = layout.getTab(tabIndex);
		if (tab == null || tab.contains(dragSlot.getCanonicalId()))
		{
			return -1;
		}
		return hit.getIndex();
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
		dropSlotIndex = -1;
	}

	/** Flat index of the grid cell under the cursor while dragging, or -1. */
	public int getDropSlotIndex()
	{
		return dropSlotIndex;
	}

	// =========================================================================================
	// Tab drag to reorder
	// =========================================================================================

	public boolean isTabDragging()
	{
		return tabDragging;
	}

	/** Layout tab index being dragged, or -1. */
	public int getTabDragFrom()
	{
		return tabDragFrom;
	}

	/** Proposed new layout tab index for the dragged tab, or -1. The renderer draws the marker here. */
	public int getTabDropIndex()
	{
		return tabDropIndex;
	}

	/**
	 * Arms a tab drag from a strip index. No-op for strip index 0 (All), the plus button, or an
	 * out-of-range index. Any real tab can be dragged, including the main tab: it stays main
	 * wherever it lands, since {@link BankTab#isMain()} identifies it, not its position.
	 */
	public void beginTabDrag(int stripIndex)
	{
		int tabIndex = stripIndex - 1;
		if (tabIndex < 0 || tabIndex >= getTabCount())
		{
			return;
		}
		tabDragging = true;
		tabDragFrom = tabIndex;
		tabDropIndex = -1;
	}

	public void updateTabDrag(int x, int y)
	{
		if (!tabDragging)
		{
			return;
		}
		Hit hit = hitTest(x, y);
		tabDropIndex = (hit.getType() == Hit.Type.TAB && hit.getIndex() >= 1)
			? clamp(hit.getIndex() - 1, 0, getTabCount() - 1) : -1;
	}

	/**
	 * Applies the reorder and clears the drag. Returns TAB_REORDER when the order changed,
	 * otherwise CANCEL.
	 */
	public DropTarget endTabDrag(int x, int y)
	{
		updateTabDrag(x, y);
		if (!tabDragging || tabDropIndex < 0 || tabDropIndex == tabDragFrom)
		{
			cancelTabDrag();
			return DropTarget.cancel();
		}

		final BankTab activeRef = activeTabRef();
		boolean changed = layout.moveTab(tabDragFrom, tabDropIndex);
		syncActiveTab(activeRef);
		DropTarget result = changed
			? new DropTarget(DropTarget.Type.TAB_REORDER, tabDropIndex, tabDragFrom) : DropTarget.cancel();

		cancelTabDrag();
		if (changed)
		{
			invalidate();
		}
		return result;
	}

	public void cancelTabDrag()
	{
		tabDragging = false;
		tabDragFrom = -1;
		tabDropIndex = -1;
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
		openMenuAt(x, y, buildMenuEntries(hitTest(x, y)));
	}

	/**
	 * The sizing, centring and clamping half of {@link #openMenu(int, int)}, factored out so the
	 * step-2 copy menu can reuse it at the same anchor. Also remembers the anchor in
	 * {@link #menuAnchor} so a later menu can reopen at the same point.
	 */
	private void openMenuAt(int x, int y, List<ContextMenuEntry> entries)
	{
		if (entries.isEmpty())
		{
			return;
		}

		// Sized and placed like the game's: as wide as its widest row (or its header, whichever is
		// wider) plus padding, and horizontally centred on the click. Clamped to the window rather
		// than the canvas, since the menu is drawn in our local space and only clicks inside our
		// published bounds reach it.
		int width = ContextMenu.TITLE.length() * BankGeometry.MENU_CHAR_W + BankGeometry.MENU_PADDING;
		for (ContextMenuEntry entry : entries)
		{
			width = Math.max(width, entry.text().length() * BankGeometry.MENU_CHAR_W + BankGeometry.MENU_PADDING);
		}
		int height = BankGeometry.menuHeight(entries.size());

		Dimension overlaySize = size();
		width = Math.min(width, overlaySize.width);
		int mx = Math.min(x - width / 2, overlaySize.width - width);
		int my = Math.min(y, overlaySize.height - height);
		mx = Math.max(0, mx);
		my = Math.max(0, my);

		menu = new ContextMenu(entries, new Rectangle(mx, my, width, height));
		menuOpen = true;
		menuAnchor = new Point(x, y);
	}

	/** Menu-building helper: the tabs a copy of this id could still go to, in strip order. */
	private List<Integer> copyTargetTabs(int itemId)
	{
		List<Integer> targets = new ArrayList<>();
		for (int t = 0; t < layout.getTabs().size(); t++)
		{
			if (layout.canCopyTo(itemId, t))
			{
				targets.add(t);
			}
		}
		return targets;
	}

	/**
	 * How many rows a menu may have before it would run past the bottom of the window. The menu is
	 * clamped into the window rather than the canvas (only clicks inside our published bounds reach
	 * it), so a menu taller than the window would have rows that can never be clicked. Never below 2,
	 * so a paged menu always has room for at least one target and its "More" row.
	 */
	int maxMenuEntries()
	{
		final int usable = size().height - BankGeometry.MENU_HEADER_H - BankGeometry.MENU_BODY_GAP
			- BankGeometry.MENU_BOTTOM_PAD;
		return Math.max(2, usable / BankGeometry.MENU_ENTRY_H);
	}

	/** Step-2 menu rows: one "Copy to &lt;tab&gt;" per {@link #copyTargetTabs}, then Cancel. */
	private List<ContextMenuEntry> buildCopyTargetEntries(int itemId)
	{
		return buildCopyTargetEntries(itemId, 0);
	}

	/**
	 * Step-2 menu rows, paged. With the tab cap at {@link BankLayout#MAX_TABS} a full target list is
	 * far taller than the window's minimum height, and a menu is clamped into the window, so the
	 * overflow would be unclickable. Instead the list is cut into pages of whatever
	 * {@link #maxMenuEntries()} allows and a "More" row walks to the next one, cycling back to the
	 * first at the end so no page is a dead end. A list that fits keeps exactly its old shape: the
	 * targets, then Cancel, with no "More" row at all.
	 */
	private List<ContextMenuEntry> buildCopyTargetEntries(int itemId, int page)
	{
		final List<Integer> targets = copyTargetTabs(itemId);
		final int max = maxMenuEntries();
		final boolean paged = targets.size() + 1 > max;
		// One row goes to Cancel, and on a paged menu one more goes to "More".
		final int perPage = paged ? Math.max(1, max - 2) : targets.size();
		final int pages = paged ? (targets.size() + perPage - 1) / perPage : 1;
		final int current = pages <= 0 ? 0 : Math.floorMod(page, pages);
		final int from = current * perPage;
		final int to = Math.min(targets.size(), from + perPage);

		List<ContextMenuEntry> entries = new ArrayList<>();
		for (int i = from; i < to; i++)
		{
			final int t = targets.get(i);
			entries.add(new ContextMenuEntry("Copy to", MenuAction.COPY_TO_TAB, t, itemId, tabName(t)));
		}
		if (paged)
		{
			final String label = "More (" + (current + 1) + "/" + pages + ")";
			entries.add(new ContextMenuEntry(label, MenuAction.COPY_TO_TAB_PAGE, itemId, current + 1, null));
		}
		entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
		return entries;
	}

	/** The tab's name for a menu row's orange target half; a sane fallback when the index is stale. */
	private String tabName(int tabIndex)
	{
		BankTab tab = layout.getTab(tabIndex);
		return tab == null || tab.getName() == null || tab.getName().isEmpty() ? "this tab" : tab.getName();
	}

	private List<ContextMenuEntry> buildMenuEntries(Hit hit)
	{
		List<ContextMenuEntry> entries = new ArrayList<>();

		if (hit.getType() == Hit.Type.SLOT && hit.getSlot() != null)
		{
			BankSlot slot = hit.getSlot();
			final String item = slot.getName();
			final int id = slot.getCanonicalId();
			final int tabIndex = slot.getTabIndex();
			final int slotIndex = slot.getIndexInTab();
			final boolean duplicated = layout.copyCount(id) > 1;
			final boolean canCopy = getTabCount() > 1 && !copyTargetTabs(id).isEmpty();
			if (slot.isPlaceholder())
			{
				if (duplicated)
				{
					entries.add(new ContextMenuEntry("Remove copy", MenuAction.REMOVE_COPY, tabIndex, slotIndex, item));
				}
				else
				{
					entries.add(new ContextMenuEntry("Release placeholder", MenuAction.RELEASE_PLACEHOLDER, tabIndex, slotIndex, item));
				}
				entries.add(new ContextMenuEntry("Never show placeholder", MenuAction.IGNORE_PLACEHOLDER, id, item));
				if (canCopy)
				{
					entries.add(new ContextMenuEntry("Copy to", MenuAction.COPY_TO_TAB_MENU, id, tabIndex, "another tab"));
				}
				entries.add(new ContextMenuEntry("Release all placeholders in", MenuAction.RELEASE_ALL_IN_TAB, tabIndex, tabName(tabIndex)));
				entries.add(new ContextMenuEntry("Release all placeholders", MenuAction.RELEASE_ALL, -1));
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
			else
			{
				BankTab ownerTab = layout.getTab(tabIndex);
				boolean ownerIsMain = ownerTab != null && ownerTab.isMain();
				if (duplicated)
				{
					entries.add(new ContextMenuEntry("Remove copy", MenuAction.REMOVE_COPY, tabIndex, slotIndex, item));
				}
				if (!ownerIsMain)
				{
					entries.add(new ContextMenuEntry("Set as tab icon", MenuAction.SET_TAB_ICON, tabIndex, id, item));
				}
				if (ownerTab != null && ownerTab.getIcon() > 0)
				{
					entries.add(new ContextMenuEntry("Clear tab icon", MenuAction.CLEAR_TAB_ICON, tabIndex, tabName(tabIndex)));
				}
				if (getTabCount() < BankLayout.MAX_TABS)
				{
					entries.add(new ContextMenuEntry("New tab from", MenuAction.NEW_TAB_FROM_ITEM, tabIndex, slotIndex, item));
				}
				if (canCopy)
				{
					entries.add(new ContextMenuEntry("Copy to", MenuAction.COPY_TO_TAB_MENU, id, tabIndex, "another tab"));
				}
				if (!ownerIsMain && !layout.getMainTab().contains(id))
				{
					entries.add(new ContextMenuEntry("Move to main tab", MenuAction.MOVE_TO_MAIN, tabIndex, slotIndex, item));
				}
				if (layout.isPlaceholderIgnored(id))
				{
					entries.add(new ContextMenuEntry("Show placeholder again", MenuAction.UNIGNORE_PLACEHOLDER, id, item));
				}
				else
				{
					entries.add(new ContextMenuEntry("Never show placeholder", MenuAction.IGNORE_PLACEHOLDER, id, item));
				}
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
		}
		else if (hit.getType() == Hit.Type.SLOT_EMPTY)
		{
			return Collections.emptyList();
		}
		else if (hit.getType() == Hit.Type.TAB)
		{
			if (hit.getIndex() == 0)
			{
				// The fixed All button at strip index 0 - not a real tab.
				entries.add(new ContextMenuEntry("Release all placeholders", MenuAction.RELEASE_ALL, -1));
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
			else
			{
				int tabIndex = hit.getIndex() - 1;
				BankTab tab = layout.getTab(tabIndex);
				final String name = tabName(tabIndex);
				entries.add(new ContextMenuEntry("Rename", MenuAction.RENAME_TAB, tabIndex, name));
				entries.add(new ContextMenuEntry("Release all placeholders in", MenuAction.RELEASE_ALL_IN_TAB, tabIndex, name));
				entries.add(new ContextMenuEntry("Collapse blank spaces in", MenuAction.COMPACT_TAB, tabIndex, name));
				if (tab != null && !tab.isMain())
				{
					entries.add(new ContextMenuEntry("Delete", MenuAction.DELETE_TAB, tabIndex, name));
				}
				entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
			}
		}
		else if (hit.getType() == Hit.Type.TITLE_BAR)
		{
			entries.add(new ContextMenuEntry("Close", MenuAction.CLOSE_VIEW, -1, "Bankless Bank"));
			entries.add(new ContextMenuEntry("Cancel", MenuAction.CANCEL, -1));
		}

		return entries;
	}

	public void closeMenu()
	{
		menuOpen = false;
		menu = null;
		menuAnchor = null;
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

	/**
	 * The tab index the "Rename tab" entry was activated on since this was last called, or -1. The
	 * controller polls this after every {@link #activateMenu(int, int)} the same way it polls
	 * {@link #consumeCloseRequest()}, and opens the chatbox text input for that tab.
	 */
	public int consumeRenameTabRequest()
	{
		int requested = renameTabRequest;
		renameTabRequest = -1;
		return requested;
	}

	/**
	 * Applies a rename the chatbox text input's {@code onDone} produced. Delegates to
	 * {@link BankLayout#renameTab(int, String)} (trim, cap at 20 chars, blank resets to the default
	 * name) and rebuilds so the new name shows up in the title and any All-view divider immediately.
	 *
	 * @return true when the name actually changed
	 */
	public boolean renameTab(int tabIndex, String newName)
	{
		boolean changed = layout.renameTab(tabIndex, newName);
		if (changed)
		{
			invalidate();
		}
		return changed;
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
				changed = layout.releasePlaceholderAt(entry.getArg(), entry.getArg2(), ownedIds);
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
				int newIndex = layout.createTabFrom(entry.getArg(), entry.getArg2());
				changed = newIndex != -1;
				syncActiveTab(activeRef);
				if (changed)
				{
					setActiveTab(newIndex);
				}
				break;
			}
			case SET_TAB_ICON:
			{
				int tab = entry.getArg();
				BankTab ownerTab = layout.getTab(tab);
				changed = ownerTab != null && !ownerTab.isMain() && layout.setTabIcon(tab, entry.getArg2());
				break;
			}
			case CLEAR_TAB_ICON:
				changed = layout.setTabIcon(entry.getArg(), -1);
				break;
			case IGNORE_PLACEHOLDER:
				changed = layout.addPlaceholderIgnore(entry.getArg());
				break;
			case UNIGNORE_PLACEHOLDER:
				changed = layout.removePlaceholderIgnore(entry.getArg());
				break;
			case MOVE_TO_MAIN:
				changed = layout.moveSlotToTab(entry.getArg(), entry.getArg2(), layout.indexOfMainTab());
				syncActiveTab(activeRef);
				break;
			case DELETE_TAB:
			{
				int tabIndex = entry.getArg();
				layout.deleteTab(tabIndex);
				changed = true;
				syncActiveTab(activeRef);
				break;
			}
			case COMPACT_TAB:
				// Packed down and re-laid-out at the window's current width, so "collapse" also means
				// "reflow to fit what I have open right now".
				changed = layout.compactTab(entry.getArg(), visibleCols);
				break;
			case RENAME_TAB:
				// Raised for the controller to consume - opening the chatbox text input is RuneLite
				// tier, and the actual rename comes back through renameTab(int, String) once the
				// player submits it.
				renameTabRequest = entry.getArg();
				changed = false;
				break;
			case CLOSE_VIEW:
				closeRequested = true;
				changed = false;
				break;
			case COPY_TO_TAB_MENU:
			{
				// Not a mutation: closes this menu and opens the target chooser at the same anchor.
				// Card 27 override (board decision): kept as its own two-step flow per §4.1 of the
				// spec, since a flat "Copy to <tab>" list in the first menu would overflow the
				// minimum window height (see the spec's geometry table).
				final Point anchor = menuAnchor;
				closeMenu();
				// buildCopyTargetEntries always appends a trailing Cancel row, so it is never itself
				// empty - the "no target left" guard has to ask copyTargetTabs, the list of actual
				// copy rows, or a target tab that vanished between menu-open and click (e.g. another
				// copy landing there first) would still reopen a menu with nothing but Cancel in it.
				if (anchor != null && !copyTargetTabs(entry.getArg()).isEmpty())
				{
					openMenuAt(anchor.x, anchor.y, buildCopyTargetEntries(entry.getArg()));
				}
				return false;
			}
			case COPY_TO_TAB_PAGE:
			{
				// Like COPY_TO_TAB_MENU: not a mutation, just the same chooser at the next page.
				final Point pageAnchor = menuAnchor;
				closeMenu();
				if (pageAnchor != null && !copyTargetTabs(entry.getArg()).isEmpty())
				{
					openMenuAt(pageAnchor.x, pageAnchor.y,
						buildCopyTargetEntries(entry.getArg(), entry.getArg2()));
				}
				return false;
			}
			case COPY_TO_TAB:
				changed = layout.copyItemToTab(entry.getArg2(), entry.getArg()) >= 0;
				break;
			case REMOVE_COPY:
				changed = layout.removeSlot(entry.getArg(), entry.getArg2());
				syncActiveTab(activeRef);
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
	// Search
	// =========================================================================================

	/**
	 * The search icon's click, in the pure tier (card 31). Returns true when the caller should open
	 * the chatbox search prompt, false when the click has already been dealt with by clearing the
	 * active filter.
	 *
	 * <p>The icon toggles: text entry lives in RuneLite's chatbox text input now (card 32), so the
	 * icon is the only quick way back to an unfiltered grid - there is no field of ours to backspace
	 * through.
	 */
	public boolean clickSearchButton()
	{
		if (!search.isEmpty())
		{
			clearSearch();
			setSearchFocused(false);
			return false;
		}
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
