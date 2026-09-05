# Build status (2026-09-05)

Working state for resuming the build. Delete this file once the plugin is feature complete.

## Done

- `tracking/`: DWMS v2.11.5 tracking layer ported (core + carryable, death, poh, world, stash,
  sailing). All Swing/info box/map point code stripped. `StorageManager#updateStorages` calls
  `BanklessBankPlugin#storagesChanged()`, which sets a dirty flag the view polls.
  `ItemStorage#importItems` added for the bootstrap. Compiles with `./gradlew compileJava`.
- `BanklessBankPlugin`: manager wiring, login state machine, per-profile load/save, event
  fan-out, `save()` and `reload()` (mirrors `DudeWheresMyStuffPlugin`).
- `BanklessBankConfig`: hotkey, HUD button toggle, placeholders, show empty storages,
  cross-client death timers, contingency minutes.
- `bootstrap/DwmsImporter`: copies DWMS RS-profile config keys into our group (keeps death
  pile UUIDs/expiry), then if DWMS is running refreshes non-death storages from the
  `storages-request` PluginMessage. `Mode.FILL_GAPS` vs `Mode.OVERWRITE`. Not yet wired into
  the plugin (needs `@Subscribe onPluginMessage` -> `importer.onPluginMessage`, and
  `importer.onGameTick()` from `onGameTick`, plus the first-login auto import).
- `model/BankLayout`, `BankTab`, `LayoutStore`: tabs (main + up to 9), insert-style moves,
  placeholders, release per item / per tab, JSON persisted per RS profile under key `layout`.

## Next (in order)

1. `ui/BankOverlay` (DYNAMIC overlay, self-positioned, title bar drag, 8-col grid 48x36 slots,
   tab strip [All][Main][custom...][+], search box, scrollbar, placeholders drawn at 35% alpha,
   own right-click menu, drag/drop between slots and tabs, "by storage" grouped mode,
   tooltip with per-storage breakdown). Rebuild the view model on the client thread when
   `plugin.isStoragesDirty()`; sync `BankLayout` with owned canonical ids and save on change.
2. `ui/BankInputListener`: `MouseListener`/`MouseWheelListener` registered at position 0 so
   the overlay consumes its own clicks; `KeyListener` for search typing and Esc;
   `HotkeyListener` for the config keybind.
3. `ui/HudButtonOverlay`: small snappable overlay (default BOTTOM_LEFT) that toggles the view.
4. `ui/BanklessBankPanel` sidebar + `NavigationButton`: DWMS status, import buttons, last result.
5. Wire importer + first-login auto import (only when `!hasOwnData && !hasImportedBefore`).
6. Tests: `BankLayout` unit tests, `DwmsImporter.copyConfig/applyLiveStorages` with mocked
   `ConfigManager`, then behaviour tests per CLAUDE.md.
7. `./gradlew runClient` to check rendering; update README/CLAUDE.md; remove this file.

## Decisions made while building

- Import uses config copy first (metadata preserved), live PluginMessage second. Refines
  RESEARCH.md section 5, which framed A and B as alternatives.
- `coins` and `minigames` DWMS categories are ignored by the importer (no managers ported).
- Overlay position is managed by the plugin (title bar drag) rather than RuneLite's alt-drag;
  the HUD button uses RuneLite's normal snap/drag so its position persists for free.
