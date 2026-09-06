# Bankless Bank

![image](https://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/rank/plugin/bankless-bank)
![image](https://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/bankless-bank)

##### A plugin for [RuneLite](https://runelite.net/)

A bank-style, in-game view of every item you own, without walking to a bank. Built for Ultimate
Ironmen (UIM), who can't use a real bank at all — their items are scattered across inventory,
equipment, the looting bag, the player-owned house, STASH units, and death storage. Bankless
Bank is a *viewer* only: it draws its own bank-style window, but there is no withdrawing or
depositing.

## Preview

<!-- screenshot: main bank overlay window, open over the game, showing a populated tab grid -->

<!-- gif: dragging an item between slots, onto a tab button, and resizing the window from the corner -->

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

Coins and minigame points aren't tracked — they aren't items you'd see in a real bank.

## Features

- **Tabs, your way** — up to 20 tabs plus the "all" view, each with its own chosen icon; the tab
  strip wraps onto extra rows as you add more, rather than scrolling out of reach
- **Free-floating slots** — drag any item anywhere in the grid and it stays exactly where you put
  it, gaps included, just like the real bank
- **Duplicate into multiple tabs** — the same item can be filed into several tabs at once, not
  just one
- **Placeholders** — greyed-out slots for items you no longer own, on by default, releasable one
  at a time or for a whole tab, plus a per-item "never show placeholder" ignore list
- **Grand Exchange values** — shown for the tab you're viewing in the title bar, and per item in
  tooltips
- **Search through the chatbox** — types into the game's own search prompt, so plugins that grab
  keys (like WASD camera) never get in the way
- **Storage mode** — list items grouped by where they physically are (looting bag, POH, a
  specific STASH unit, and so on) instead of by tab
- **Resizable window** — drag the bottom-right corner from 4 to 16 columns and 3 to 20 rows; each
  tab keeps its own layout width independent of the window size
- **Manual add** — search for and drop in an item you don't own yet, ready as a placeholder for
  when you pick it up
- **Export / import** — save every bit of Bankless Bank data to a file and load it back in,
  useful if you're not signed into a RuneLite account
- **One-time import from Dude, Where's My Stuff?** — bootstrap your STASH units, POH, and old
  death piles from an existing DWMS install in one click; DWMS isn't needed afterwards
- **Syncs with your RuneLite account** — your layout and tracked storages follow you between
  computers automatically once signed in

## How to use

Open the view three ways, all from anywhere in the game:

- An always-visible HUD button on the game canvas
- The Bankless Bank button in the RuneLite sidebar
- A configurable hotkey

Once open, it behaves like the real bank: a title bar you can drag to reposition, a tab strip,
an item grid, search, and a scrollbar. Drag items around the grid, drag them onto a tab button
to file them, or onto **+** to start a new tab. Right-click an item for placeholder options or
to set it as a tab's icon; right-click a tab for rename, "release all placeholders", "collapse
blank spaces", or delete.

<!-- screenshot: tab strip wrapped onto two rows with several tabs -->

<!-- screenshot: right-click context menu on an item, styled like the game's "Choose Option" menu -->

If you've already got items sitting in STASH units, your POH, or an old death pile from before
installing Bankless Bank, importing from [Dude, Where's My Stuff?](https://github.com/Thource/dude-wheres-my-stuff)
picks all of that up in one go instead of making you revisit every storage. This runs
automatically on first login if you have DWMS data and none of your own yet, or manually from
the sidebar panel.

<!-- screenshot: sidebar panel showing DWMS import status and buttons -->

## FAQ

**Can I withdraw or deposit items from this window?**
No. Bankless Bank only shows you what you own and where it is — it never moves anything. Use it
to check your gear at a glance, not as a bank replacement.

**Do I need Dude, Where's My Stuff? installed?**
No. It's only used once, to bring across items that were sitting in storages (STASH units, POH,
old death piles) before you installed Bankless Bank. After that import, Bankless Bank tracks
everything itself and DWMS can be removed.

**Will my layout follow me to another computer?**
Yes, automatically, as long as you're signed into a RuneLite account — your tabs, placeholders,
and every tracked storage sync with your account, no setup needed. If you're not signed in, use
**Export data…** / **Import data…** in the sidebar instead.

**What happens to a slot when I stop owning that item?**
It becomes a greyed-out placeholder, just like a real bank, so your layout doesn't reshuffle
every time you use something up. Placeholders can be turned off globally, released individually,
released for a whole tab, or hidden permanently per item via the ignore list.

**Can the same item live in more than one tab?**
Yes — an item can be copied into several tabs, so you can group it under more than one theme
without picking just one home for it.

## Issues/Suggestions

Found a bug? Have a suggestion?

- [Open a new ticket on GitHub](https://github.com/robrichardson13/bankless-bank/issues/new)

## Support

<a href="https://www.buymeacoffee.com/robrichardson" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>
