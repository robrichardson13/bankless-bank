# Bankless Bank

A RuneLite plugin that gives you a bank-style, in-game view of every item you own, without
walking to a bank. Made for Ultimate Ironmen.

- Looks and feels like the real bank: tabs, item grid, search, scrolling
- Tracks your inventory, equipment, looting bag, POH, STASH units, death storage and world storages
- If you already use [Dude, Where's My Stuff?](https://github.com/Thource/dude-wheres-my-stuff),
  import its data once so nothing needs rediscovering
- Arrange items and tabs however you like; layout is saved per character
- Open it from an always-visible HUD button, the sidebar, or a hotkey
- View only. No withdrawing or depositing

## Development

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
./gradlew runClient
```

See `CLAUDE.md` and `docs/RESEARCH.md` for architecture and design notes.

The item tracking layer is derived from Dude, Where's My Stuff? by Thource (BSD 2-Clause).
