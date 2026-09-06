# Bankless Bank

A RuneLite plugin that gives you a bank-style, in-game view of every item you own, without
walking to a bank. Built for Ultimate Ironmen (UIM), who can't use a real bank at all — their
items are scattered across inventory, equipment, the looting bag, the player-owned house, STASH
units, and death storage. Bankless Bank is a *viewer* only: it draws its own bank-style window,
but there is no withdrawing or depositing.

TODO: screenshot of the bank view.

## What it tracks

- Inventory, equipment, and the looting bag
- Carryable containers: rune pouch, herb sack, seed box, bolt pouch, quiver, tackle box,
  chugging barrel, plank sack, bottomless bucket, master scroll book, gnomish firelighter,
  huntsman's kit, forestry kit, bow string spool
- Death storage: deathpiles, graves, and Death's Office, including expiry timers
- Player-owned house: costume room, menagerie, spice rack, cape hanger
- World storages: leprechaun, fossil storage, log storage, potion storage, bird nests, compost
  bins, blast furnace, and other fixed drop points
- STASH units
- Sailing: active and lost boat holds

Coins and minigame points are not tracked; they aren't items you'd see in a bank.

## Opening the view

Three ways to open it, any of which work from anywhere in the game:

- An always-visible HUD button on the game canvas (can be repositioned or hidden)
- The Bankless Bank button in the RuneLite sidebar
- A configurable hotkey

Once open, it behaves like the real bank interface: a title bar you can drag to reposition,
tabs (main plus up to nine custom tabs, each with a chosen icon), a resizable item grid
(drag the bottom-right corner; four to sixteen columns, three to twenty rows), search, and a
scrollbar.

Resizing the window never rearranges anything. Each tab has its own width — the width its items
are laid out at — and the window is only a viewport onto that. Make the window wider than a tab
and you get blank columns to the right of it (drop an item on one and the tab grows to include
that column, with everything already placed staying exactly where it was); make it narrower and
the grid scrolls sideways. A horizontal scrollbar only appears, overlaying the bottom of the grid,
while the active tab is actually wider than the window; scroll it with shift + mouse wheel or by
dragging its bar. If you would rather a tab did rewrap to the window you have open, that is what
"collapse blank spaces" is for. The title bar shows how many items you own and, optionally, the Grand Exchange
value of whichever tab you're looking at. A second display mode lists items grouped by the
storage they're actually in (looting bag, POH, a specific STASH unit, and so on) instead of by
tab.

### Arranging items

Slots are free-floating: drag an item anywhere in the grid and it stays there, gaps and all.
Dropping onto an empty slot moves; dropping onto an occupied one swaps. Drag an item onto a tab
button to send it to the end of that tab, onto the ∞ button to send it back to the main tab, or
onto **+** to start a new tab from it. Search results can be dragged onto tab buttons too,
which is the quick way to file a lot of items at once.

The **+** button on the bottom bar adds an item you don't own yet: it opens the game's own item
search in the chatbox, and whatever you pick is appended to the end of the tab you're looking at
(the main tab if you're on ∞) as a placeholder, ready to be dragged around like anything else.
Picking something already in the layout just jumps to the tab holding it. This needs placeholders
switched on to be of any use — with them off, the slot is blanked again as soon as your items are
next synced.

Tabs themselves are draggable, main included — it is the tab you can't delete, not the tab that
has to come first. The ∞ (all) button always stays at the left. Each tab remembers its own
scroll position, and search is scoped to the tab you're on unless you're on ∞.

Right-click an item for "set as tab icon", "new tab from this item", "move to main tab", and
the placeholder options. Right-click a tab button for "rename tab" (type a name and hit enter;
it shows up in that tab's heading and, on ∞, next to its icon in the group divider — leave it
blank to go back to "Main"/"Tab N"), "release all placeholders in this tab", "collapse blank
spaces" (packs the tab's items down over any gaps and re-lays them out at the window's current
width, so it doubles as "rewrap this tab to fit"), and "delete tab".

### Placeholders

A placeholder is a greyed-out slot left behind for an item you no longer own. They're on by
default, just like a real bank, and can be turned off globally in settings, released one at a
time, or released a whole tab at once. "Never show placeholder" adds an item to an ignore list
instead: it keeps its slot but is drawn as empty whenever you don't own it, and reappears
normally the moment you do. The sidebar shows how many items are on that list and can clear it.

## Importing from Dude, Where's My Stuff?

If you've already got items sitting in STASH units, your POH, or an old death pile from before
you installed Bankless Bank, a fresh tracker would show those as empty until you revisit each
one. If you use [Dude, Where's My Stuff?](https://github.com/Thource/dude-wheres-my-stuff)
(DWMS), Bankless Bank can import its existing snapshot once so nothing needs rediscovering.

This happens automatically on first login if you have DWMS data and no Bankless Bank data of
your own yet, or you can trigger it manually from the sidebar panel, which shows whether DWMS
is installed and the result of the last import. Two buttons are offered:

- **Fill gaps** (default) — only imports storages Bankless Bank doesn't already have data for
- **Overwrite** — replaces everything with DWMS's data

DWMS is only ever used as a one-time source. After an import, Bankless Bank tracks everything
itself and DWMS does not need to stay installed or enabled.

## Syncing between machines, and export/import

Your bank layout, ignored placeholders, and every tracked storage sync automatically through
RuneLite's own cloud sync whenever you're signed into a RuneLite account — no setup needed, and
no per-profile toggle involved, because that data is stored per RuneScape account. Window
position, window size, view mode and the plugin settings live in the active RuneLite profile
instead, so those travel only if that profile's own sync toggle is on. The sidebar shows whether
cloud sync is currently active.

If you're not signed in (or want to move data to a different account), use **Export data…** and
**Import data…** in the sidebar. Export saves everything above, plus your view settings, to a
JSON file via a save dialog. Import reads that file back and replaces all current Bankless Bank
data for the character you're logged in as — a confirmation dialog is shown first, since this is
one-way and can't be undone. Import needs you to be logged in to the character you want to
import into; it always targets your current character, not the one recorded in the file.

## Where things are

RuneLite gives every plugin two separate places to live, and Bankless Bank uses both for
different things:

- **The sidebar icon** (the Bankless Bank icon in RuneLite's left-hand sidebar, opened like any
  other plugin panel) — DWMS import, the placeholder ignore list, cloud-sync status, and the
  **Export data…** / **Import data…** buttons described above.
- **The plugin's config panel** (the wrench icon next to Bankless Bank in RuneLite's plugin list,
  under Configuration) — the settings listed below: hotkey, HUD button, placeholders, GE value,
  empty storages, and death-storage timing.

Export/Import live in the sidebar panel, not the config panel — if you opened the wrench looking
for them, that's the config panel, not the sidebar.

## Configuration

- **Toggle bank view** — hotkey that opens and closes the view
- **Show HUD button** — draw the always-visible launch button on the game canvas
- **Placeholders** — keep a greyed-out slot for items you no longer own
- **Show GE value** — show the Grand Exchange value of the visible tab in the title bar
- **Show empty storages** — list storages with no tracked items in by-storage mode
- **Cross-client tracking** — base deathpile/grave expiry on account play time instead of
  client-local time, so time played on another client (e.g. mobile) counts; requires visiting
  the Character Summary tab so the plugin can read your play time
- **Contingency (minutes)** — minutes shaved off new deathpile/grave timers as a safety margin

## Development

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
./gradlew runClient
```

See `CLAUDE.md` and `docs/RESEARCH.md` for architecture and design notes.

## Credits and license

The item tracking layer (`tracking/`) is ported from
[Dude, Where's My Stuff?](https://github.com/Thource/dude-wheres-my-stuff) by Thource, used
under its BSD 2-Clause license. Bankless Bank is likewise BSD 2-Clause.
