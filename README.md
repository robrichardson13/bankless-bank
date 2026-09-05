# Bankless Bank

A RuneLite plugin that gives you a bank-style, in-game view of every item you own, without
walking to a bank. Made for Ultimate Ironmen.

- Looks and feels like the real bank: tabs, item grid, search, scrolling
- Shows items from your inventory, equipment, looting bag, POH, STASH units, death storage and more
- Arrange items and tabs however you like; layout is saved per character
- View only. No withdrawing or depositing

Item tracking comes from the [Dude, Where's My Stuff?](https://github.com/Thource/dude-wheres-my-stuff)
plugin. Install both; Bankless Bank syncs from it automatically.

## Development

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
./gradlew runClient
```

See `CLAUDE.md` and `docs/RESEARCH.md` for architecture and design notes.
