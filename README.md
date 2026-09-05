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
tabs (main plus up to nine custom tabs, each with a chosen icon), an 8-column item grid,
search, and a scrollbar. Right-click an item for options like moving it to a new tab or
releasing its placeholder; right-click a tab for "release all". A second display mode lists
items grouped by the storage they're actually in (looting bag, POH, a specific STASH unit,
and so on) instead of by tab.

Placeholders — a greyed-out slot left behind for an item you no longer own — are on by
default, just like a real bank, and can be turned off globally in settings or released
individually.

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

## Configuration

- **Toggle bank view** — hotkey that opens and closes the view
- **Show HUD button** — draw the always-visible launch button on the game canvas
- **Placeholders** — keep a greyed-out slot for items you no longer own
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
