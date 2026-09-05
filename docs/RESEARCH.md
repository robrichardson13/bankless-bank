# Research notes (2026-09-05)

Findings from the initial scoping session. Verified against local sources:

- `/Users/robrichardson/Code/robrichardson/dude-wheres-my-stuff` (DWMS) at v2.11.5, `main` == `origin/main`
- `/Users/robrichardson/Code/robrichardson/runelite` at 1.12.39-SNAPSHOT (2026-09-03)
- `/Users/robrichardson/Code/robrichardson/alch-blocker` (prior plugin, used as the dev-loop template)
- RuneLite plugin-hub README and example-plugin `build.gradle` (fetched 2026-09-05)

## 1. Getting DWMS's item data without forking

Two viable, hub-compliant routes. Both mean DWMS stays the tracker and Bankless Bank is a view.

### A. PluginMessage API (preferred, live)

DWMS merged a cross-plugin API in PR #435 (shipped in 2.11.5). Source (both in
`/Users/robrichardson/Code/robrichardson/dude-wheres-my-stuff/src/main/java/dev/thource/runelite/dudewheresmystuff/`):
`DudeWheresMyStuffPlugin.onPluginMessage` and `StorageManagerManager.getPluginMessageStorages`.

Request (post on the RuneLite `EventBus`):

```
namespace: "dudewheresmystuff"
name:      "storages-request"
data:      { "source": "<your plugin display name>" }   // required, non-empty
```

Response (posted on the client thread):

```
namespace: "dudewheresmystuff"
name:      "storages-response"
data: {
  "source":   "Dude, Where's My Stuff?",
  "target":   "<the requester's source>",   // filter on this
  "version":  1,
  "storages": [
    {
      "category":    "carryable" | "coins" | "death" | "stash" | "minigames" | "world" | "poh" | "sailing",
      "name":        "<storage display name, e.g. 'Looting bag'>",
      "lastUpdated": <epoch ms, -1 if unknown>,
      "items":       [ { "id": <canonical item id>, "quantity": <long> }, ... ]
    }, ...
  ]
}
```

Only non-empty, enabled storages are included. Item ids are canonicalised (noted → unnoted,
placeholders resolved) via `ItemManager.canonicalize`. Inventory and equipment are included
(`carryable` category, storages "Inventory" / "Equipment").

Caveat: there is no "changed" push event. Re-request on a timer, on `ItemContainerChanged`, or
when the view is opened. Requests are cheap (DWMS builds the list from in-memory state).

### B. Reading DWMS config directly (fallback, offline)

DWMS persists via `ConfigManager` under group `dudewheresmystuff`, scoped to the RS profile:

```
key:   "<managerKey>.<storageKey>"      e.g. "carryable.lootingbag"
value: "<lastUpdated>;<id>x<qty>,<id>x<qty>,..."   (lastUpdated omitted for automatic storages)
```

Read with `configManager.getConfiguration("dudewheresmystuff", configManager.getRSProfileKey(), key)`.
Storage keys live in each `*StorageType` enum's `configKey` field (e.g.
`/Users/robrichardson/Code/robrichardson/dude-wheres-my-stuff/src/main/java/dev/thource/runelite/dudewheresmystuff/carryable/CarryableStorageType.java`).
Serialisation is in `SaveFieldFormatter.java` / `SaveFieldLoader.java` in the same package. The format is internal to DWMS and
could change, so treat this as a one-time import path, not the primary sync.

Decision: see section 5. A is the DWMS sync path; B is kept only as a one-time import.

### Hub precedent for consuming DWMS

The DWMS API was contributed (PR #435) by the author of **Loadout Lab**, which is on the plugin
hub (`plugin-hub/plugins/loadout-lab` -> `AKAddons/runelite-loadout-lab`). Loadout Lab works
standalone and offers DWMS as an optional data source under a "Connections" config section.
That is the accepted shape: the plugin must be useful on its own, and DWMS is an enhancement.
No hub-listed plugin hard-requires another hub plugin, and the hub review wiki says nothing
either way, so a pure companion plugin is a review risk we avoid.

## 2. Rendering a bank-like UI in game

Options considered:

1. **Custom `Overlay` drawn with `Graphics2D`** (the approach of core `InventoryViewerOverlay` at
   `/Users/robrichardson/Code/robrichardson/runelite/runelite-client/src/main/java/net/runelite/client/plugins/inventoryviewer/InventoryViewerOverlay.java`). Item sprites come from
   `ItemManager.getImage(id, qty, stackable)` (async `AsyncBufferedImage`). Bank chrome (borders,
   tab sprites, scrollbar) can be drawn from `SpriteManager` sprites or hand-drawn. Mouse input via
   `MouseManager`/`MouseListener`, keys via `KeyManager`. Fully under plugin control, and it is
   the pattern the hub already accepts for "fake inventory" style views.
2. **Injecting widgets into the game's interface** (`Widget.createChild`, opening the real bank
   interface offline). Fragile across game updates and likely to raise questions at hub review
   (imitating a Jagex interface). Not needed since there is no withdraw/deposit.

Decision: option 1. Mimic the bank's look (dark brown panel, 8-wide item grid, tab strip, search
bar, scroll) with our own drawing.

Checked the RuneLite "Rejected or Rolled-Back Features" wiki (2026-09-05): nothing forbids a
bank-style item viewer. Relevant rules to respect: do not make the inventory pane click-through
or remove its background; do not resize the spellbook; no crowdsourced player data; no HTTP
exposure of player info; no autotyping. Our overlay must swallow its own clicks and never pass
them to the game.

## 3. Build / tooling facts

- Plugin hub requires Java 11 (`options.release.set(11)`), BSD-2 license, `runeLiteVersion = 'latest.release'`.
- Hub plugins cannot compile-depend on other hub plugins; `PluginMessage` is the sanctioned bridge.
- Local JDKs: Temurin 11 at `/Library/Java/JavaVirtualMachines/temurin-11.jdk`; default `java` is 17.
  Build with `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home ./gradlew build`.
- Gradle wrapper 7.4 (copied from `/Users/robrichardson/Code/robrichardson/alch-blocker/gradle/`).
- Legacy `net.runelite.api.widgets.ComponentID`/`InterfaceID` were removed in 2026; use
  `net.runelite.api.gameval.InterfaceID`, `InventoryID`, `ItemID`, `SpriteID`, `VarClientID`.

## 4. Persistence for tabs and arrangement

Store per RS profile in our own config group (`banklessbank`) via
`configManager.setRSProfileConfiguration`, mirroring how DWMS and Bank Tags do it. Layout is
keyed by canonical item id so it survives DWMS re-syncs. Items that vanish from the tracked set
keep their slot as a greyed placeholder (like real bank placeholders). Placeholders can be turned
off globally in config; when on, right-clicking one offers "Release placeholder", and a tab's
context menu offers "Release all placeholders".

## 5. Decisions from scoping (2026-09-05)

**Data sources are tiered so the plugin is useful without DWMS.**

| Tier | Source | Mechanism | Ships in |
|------|--------|-----------|----------|
| 1 | Inventory, equipment, and every carryable container the client exposes as an `ItemContainer` (looting bag, seed box, herb sack, tackle box, forestry kit, huntsman's kit, chugging barrel) plus varbit-backed ones (rune pouch, quiver, bolt pouch) | Native: `ItemContainerChanged`, `VarbitChanged`, `net.runelite.api.gameval.InventoryID` | v1 |
| 2 | POH storage, STASH units, death piles/graves/Death's Office, world storages (leprechaun, fossil storage, log storage, etc.), boat holds | Optional DWMS sync via `PluginMessage` (section 1A), toggled under a "Connections" config section, Loadout Lab style | v1 |
| 3 | Native tracking for tier 2 storages | Port from DWMS's `*StorageManager` classes (chat message, menu click, widget and varbit driven) as durability demands | later |

Rationale: tier 1 covers what a UIM actually carries and costs little. Tier 2 in DWMS is ~40
storages of message/menu parsing (its whole codebase); reimplementing that up front would delay
the UI, which is the point of the plugin. DWMS stays optional, never required. When both native
and DWMS report the same storage, native wins.

**Launching the view.** Three entry points, all in v1:
- A small always-visible HUD button drawn as its own movable `Overlay` (RuneLite overlays can be
  repositioned in overlay-edit mode), styled like a bank booth icon. Primary entry point.
- A `NavigationButton` in the RuneLite sidebar, which also hosts sync status and a "Sync now" button.
- A configurable hotkey.
No dependence on being near a bank in game.

**Sync cadence.** Tier 1 updates on events. Tier 2 requests DWMS on login, when the view opens,
and on a "Sync now" click. No polling loop.

**Publishing.** Target the plugin hub. Name: Bankless Bank. Keep hub constraints from section 3.

**Placeholders.** On by default, global toggle, per-slot release via right-click, per-tab
"release all". See section 4.
