# Research notes (2026-09-05)

Findings from the initial scoping session. Verified against local sources:

- `~/Code/robrichardson/dude-wheres-my-stuff` (DWMS) at v2.11.5, `main` == `origin/main`
- `~/Code/robrichardson/runelite` at 1.12.39-SNAPSHOT (2026-09-03)
- `~/Code/robrichardson/alch-blocker` (prior plugin, used as the dev-loop template)
- RuneLite plugin-hub README and example-plugin `build.gradle` (fetched 2026-09-05)

## 1. Getting DWMS's item data without forking

Two viable, hub-compliant routes. Both mean DWMS stays the tracker and Bankless Bank is a view.

### A. PluginMessage API (preferred, live)

DWMS merged a cross-plugin API in PR #435 (shipped in 2.11.5). Source:
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
Storage keys live in each `*StorageType` enum's `configKey` field. The format is internal to DWMS and
could change, so treat this as a one-time import path, not the primary sync.

Decision: use A for sync, keep B in reserve for an "import once without DWMS running" feature.

## 2. Rendering a bank-like UI in game

Options considered:

1. **Custom `Overlay` drawn with `Graphics2D`** (the approach of core `InventoryViewerOverlay`,
   `runelite-client/.../plugins/inventoryviewer/`). Item sprites come from
   `ItemManager.getImage(id, qty, stackable)` (async `AsyncBufferedImage`). Bank chrome (borders,
   tab sprites, scrollbar) can be drawn from `SpriteManager` sprites or hand-drawn. Mouse input via
   `MouseManager`/`MouseListener`, keys via `KeyManager`. Fully under plugin control, and it is
   the pattern the hub already accepts for "fake inventory" style views.
2. **Injecting widgets into the game's interface** (`Widget.createChild`, opening the real bank
   interface offline). Fragile across game updates and likely to raise questions at hub review
   (imitating a Jagex interface). Not needed since there is no withdraw/deposit.

Decision: option 1. Mimic the bank's look (dark brown panel, 8-wide item grid, tab strip, search
bar, scroll) with our own drawing.

## 3. Build / tooling facts

- Plugin hub requires Java 11 (`options.release.set(11)`), BSD-2 license, `runeLiteVersion = 'latest.release'`.
- Hub plugins cannot compile-depend on other hub plugins; `PluginMessage` is the sanctioned bridge.
- Local JDKs: Temurin 11 at `/Library/Java/JavaVirtualMachines/temurin-11.jdk`; default `java` is 17.
  Build with `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home ./gradlew build`.
- Gradle wrapper 7.4 (copied from alch-blocker).
- Legacy `net.runelite.api.widgets.ComponentID`/`InterfaceID` were removed in 2026; use
  `net.runelite.api.gameval.InterfaceID`, `InventoryID`, `ItemID`, `SpriteID`, `VarClientID`.

## 4. Persistence for tabs and arrangement

Store per RS profile in our own config group (`banklessbank`) via
`configManager.setRSProfileConfiguration`, mirroring how DWMS and Bank Tags do it. Layout is
keyed by canonical item id so it survives DWMS re-syncs. Items that vanish from the synced set
keep their slot as a greyed placeholder (like real bank placeholders) until the user removes them.
