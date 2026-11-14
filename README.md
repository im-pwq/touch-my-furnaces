# Touch My Furnaces

A Fabric mod that lets you load and empty furnaces without ever opening their GUI.

## What it does

Hold **Shift** and **right-click** a furnace to move items in and out — no GUI opens.

- **Shift + right-click holding an item** — the held stack goes into the input slot (if it can be
  smelted there) or the fuel slot. The action bar tells you what was loaded.
- **Shift + right-click with an empty hand** — takes the output first, then the input, then the
  fuel. A lava bucket in the fuel slot is taken before the input.
- Works with furnaces, blast furnaces and smokers.
- Extracted items go into the **hotbar first**, then the main inventory.
- Action-bar feedback and a short pickup sound at the furnace.
- **Blacklist / whitelist** of item IDs (wildcards like `*_door` are supported), editable from the
  config screen (Mod Menu + YACL).

## How it works

The furnace logic runs on the **logical server side** — in single-player that is the integrated
server. Nothing is spoofed on the client: there is no faked sneak key and no simulated clicks. The
server simply performs a normal inventory transfer, which keeps it on good terms with anti-cheat
compared to click-automation mods. Still, some servers may consider any automated container
interaction cheating — check the rules before you use it online.

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
