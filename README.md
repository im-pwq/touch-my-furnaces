# Touch My Furnaces

A client-side Fabric mod that lets you load and empty furnaces without ever opening their GUI.

> ⚠️ **Disclaimer — read first.** This mod may be flagged by anti-cheat. It automates container
> interactions and briefly spoofs the sneak key so the server opens the furnace. Use it at your own
> risk, and check the rules of any server before using it.

## What it does

Everything happens **client-side** — the server only ever sees a normal player interacting with a
furnace.

- **Shift + right-click a furnace holding an item** — the held stack is quick-loaded into the input
  slot (or the fuel slot, for fuel). No GUI opens.
- **Shift + right-click a furnace with an empty hand** — the output (or the input / fuel / bucket)
  is taken out. No GUI opens.
- Works with furnaces, blast furnaces and smokers.
- Extracted items go into the **hotbar first**, then the main inventory.
- Action-bar feedback and a short pickup sound (played on the in-game **UI** volume slider).
- **Blacklist / whitelist** of item IDs, editable from the config screen (Mod Menu + YACL).

## Vibe-coded

This mod is **entirely vibe-coded**. It works (probably), but it has no test suite and no
guarantees. Read the source before trusting it with anything that matters.

## Requirements

- Minecraft **26.3**
- Fabric Loader **>= 0.20.1**
- Fabric API
- [YACL](https://modrinth.com/mod/yacl) (`yet_another_config_lib_v3`)
- [Mod Menu](https://modrinth.com/mod/modmenu) — optional, only for the config screen

## Building

```sh
./gradlew build
```

The jar lands in `build/libs/`.

## License

CC0-1.0.
