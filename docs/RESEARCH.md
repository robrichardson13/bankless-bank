# Research notes (2026-09-05)

Findings from the initial scoping session. Verified against local sources:

- DWMS (`Thource/dude-wheres-my-stuff` on GitHub) at v2.11.5, `main` == `origin/main`
- RuneLite (`runelite/runelite` on GitHub) at 1.12.39-SNAPSHOT (2026-09-03)
- alch-blocker (`robrichardson/alch-blocker` on GitHub; prior plugin, used as the dev-loop template)
- RuneLite plugin-hub README and example-plugin `build.gradle` (fetched 2026-09-05)

## 1. Getting DWMS's item data without forking

Two viable, hub-compliant routes. Both mean DWMS stays the tracker and Bankless Bank is a view.

### A. PluginMessage API (preferred, live)

DWMS merged a cross-plugin API in PR #435 (shipped in 2.11.5). Source (both in
`src/main/java/dev/thource/runelite/dudewheresmystuff/` of `Thource/dude-wheres-my-stuff`):
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
`carryable/CarryableStorageType.java` in the same DWMS package).
Serialisation is in `SaveFieldFormatter.java` / `SaveFieldLoader.java` in the same package. The format is internal to DWMS and
could change, so treat this as a one-time import path, not the primary sync.

Decision: see section 5. Both A and B are one-time import paths. A is preferred (DWMS running);
B covers DWMS installed but disabled.

### Hub precedent for consuming DWMS

The DWMS API was contributed (PR #435) by the author of **Loadout Lab**, which is on the plugin
hub (`plugin-hub/plugins/loadout-lab` -> `AKAddons/runelite-loadout-lab`). Loadout Lab works
standalone and offers DWMS as an optional data source under a "Connections" config section.
That is the accepted shape: the plugin must be useful on its own, and DWMS is an enhancement.
No hub-listed plugin hard-requires another hub plugin, and the hub review wiki says nothing
either way, so a pure companion plugin is a review risk we avoid. We go further than Loadout
Lab: DWMS is only a bootstrap, never a runtime dependency.

## 2. Rendering a bank-like UI in game

Options considered:

1. **Custom `Overlay` drawn with `Graphics2D`** (the approach of core `InventoryViewerOverlay` in
   `runelite/runelite`, `runelite-client/src/main/java/net/runelite/client/plugins/inventoryviewer/InventoryViewerOverlay.java`). Item sprites come from
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
- Gradle wrapper 7.4 (copied from `robrichardson/alch-blocker`'s `gradle/`).
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

**We own all item tracking. DWMS is a one-time bootstrap.**

Event-driven tracking only sees changes that happen after the plugin is installed. STASH units,
POH storage and old death piles were filled long ago, so a fresh tracker would show them empty
until each is revisited. DWMS already holds that state, so we import it once as the starting
snapshot and maintain it with our own trackers from then on. After import there is no runtime
dependency on DWMS.

### Porting the DWMS tracking layer

DWMS is BSD-2 (same as us). Copy with its copyright notice retained in each ported file. Line
counts (excluding `*TabPanel` classes) as of v2.11.5:

| DWMS package | Lines | Port? | Notes |
|--------------|-------|-------|-------|
| core (`Storage`, `ItemStorage`, `StorageManager`, `StorageManagerManager`, `ItemContainerWatcher`, `ItemStack`, `Var`, `SaveField*`) | 1,848 | yes | Strip `storagePanel` references and the Google Sheets `CellData` import in `ItemStack`. |
| `carryable` | 1,904 | yes | Looting bag, rune pouch, herb sack, seed box, quiver, etc. |
| `death` | 4,403 | yes | Deathpiles, graves, Death's Office, expiry timers. Info boxes / world map points are optional. |
| `playerownedhouse` | 2,887 | yes | Costume room, menagerie, spice rack, cape hanger. |
| `world` | 2,339 | yes | Leprechaun, fossil storage, log storage, potion storage, nests, etc. |
| `stash` | 1,825 | yes | All STASH units. |
| `sailing` | 687 | yes | Boat holds. |
| `coins` | 483 | no (v1) | Coffers and GE coins are not items in a bank view. Revisit. |
| `minigames` | 1,143 | no | Points, not items. |
| panels, export, Google API deps | 3,714 | no | Replaced by our overlay. |

Port into `io.robrichardson.banklessbank.tracking`, keeping DWMS's manager / storage type
structure since it is proven and makes future upstream fixes easy to diff across. Persist with
the same `ConfigManager` per-RS-profile pattern under our own group `banklessbank`.

Maintenance trade: when Jagex adds a storage or changes a message, DWMS will patch it and we port
the fix. Keep ported files structurally close to upstream so `diff` against
`Thource/dude-wheres-my-stuff` stays useful.

### Bootstrap import from DWMS

- Triggered automatically on first login for a profile with no saved data, and manually from an
  "Import from Dude, Where's My Stuff?" button in the sidebar panel.
- Path A (section 1A) when DWMS is running: post `storages-request`, consume `storages-response`.
  `category` maps to our manager config key and `name` to the storage display name, because the
  classes are ported one-to-one.
- Path B (section 1B) when DWMS is installed but disabled: read its RS-profile config directly.
- Default mode fills only storages we have no data for. An explicit "overwrite" option replaces
  everything. Imports never run on a timer.

### Launching the view

Three entry points, all in v1:
- A small always-visible HUD button drawn as its own movable `Overlay` (RuneLite overlays can be
  repositioned in overlay-edit mode), styled like a bank booth icon. Primary entry point.
- A `NavigationButton` in the RuneLite sidebar, which also hosts tracking status and the import button.
- A configurable hotkey.
No dependence on being near a bank in game.

### Other decisions

**Publishing.** Target the plugin hub. Name: Bankless Bank. Keep hub constraints from section 3.

**Placeholders.** On by default, global toggle, per-slot release via right-click, per-tab
"release all". See section 4.

## 6. RuneLite cloud sync and export/import (2026-09-06)

`ConfigManager` writes two scopes, each its own `.properties` file: the **active profile**
(gated on that profile's own sync toggle) and the internal `$rsprofile` profile, which
`ConfigManager` forces `sync = true` on unconditionally (`ConfigManager.java:366`, `:548`). So
any RS-profile-scoped key syncs for everyone signed into a RuneLite account, with no toggle to
forget. Our layout, placeholder ignore list, and every tracked storage key ride `$rsprofile`, so
they already sync automatically once signed in. Only the six `view*` keys and the
`@ConfigItem` settings live in the active-profile scope, because that is where every RuneLite
plugin keeps UI preferences; they sync only if that profile's own sync toggle is on. Server
limits (last open-source `ConfigService`, before commit `055f5c2d2`): 256 KiB per value, 8
levels of JSON nesting, no `:` or leading `$`/`_` in a key. Our layout JSON is ~6 KB and 5 levels
deep, comfortably inside both; `LayoutStoreTest#layoutStaysWithinSyncLimits` guards against a
future field silently breaking this. A per-key server rejection produces no client-side signal
at all (`ConfigPatchResult.getFailures()` is only logged on a non-200 HTTP status), which is the
general mechanism behind "some plugin data doesn't sync" for other plugins.

**Export/import** (`bootstrap/DataExporter`, wired into `BanklessBankPanel`) exists for the
signed-out case: every `banklessbank` key in both scopes, plus export date and character name,
as one JSON document (schema-versioned). Import always replaces the *currently logged-in*
character's data, never the one recorded in the file — that's what makes moving between
machines and accounts work. See `BanklessBankPanel` for the file-chooser flow and
`BankViewController.reloadFromConfig()` / `BanklessBankPlugin.reloadAfterImport()` for how the
in-memory state is re-read afterwards without a stale layout or storage `lastSaveString`
clobbering what was just imported.
