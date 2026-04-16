# Localized Weather

A Fabric mod that replaces Minecraft's global weather system with **per-zone localized weather**. Rain, snow, and thunderstorms happen independently across the world, with smooth transitions at zone boundaries and Minecraft-style storm clouds.

## Features

- **Localized weather zones** — 256×256 block zones each have their own weather state
- **Biome-aware rules** — deserts stay dry, snowy biomes get snow, etc.
- **Smooth transitions** — rain/fog/sky color blend seamlessly across zone boundaries
- **Storm clouds** — blocky, Minecraft-style 3D cloud layers appear over storm zones, visible from a distance
- **Directional darkening** — sky, fog, and clouds darken toward approaching storms
- **Better Clouds compatible** — works alongside the Better Clouds mod

## Requirements

- Minecraft 1.21.9+
- Fabric Loader 0.19.2+
- Fabric API
- [Better Clouds](https://modrinth.com/mod/better-clouds)
- [YACL](https://modrinth.com/mod/yacl) (required by Better Clouds)

## Optional Dependencies

- [Mod Menu](https://modrinth.com/mod/modmenu) — in-game mod configuration

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Install [Better Clouds](https://modrinth.com/mod/better-clouds) and [YACL](https://modrinth.com/mod/yacl)
3. Drop the mod jar into your `mods` folder
4. Launch the game

## How It Works

Weather happens automatically — no commands needed. Each 256×256 block zone rolls its own weather independently:

- **Clear skies** last 10 minutes to 2.5 hours before a chance of weather
- **Rain and storms** last 10–20 minutes before clearing
- **Biome rules** kick in automatically — deserts stay dry, cold biomes get snow instead of rain
- **Transitions** blend smoothly over 20 seconds at zone boundaries
- **Storm clouds** appear as blocky 3D cloud layers over rainy/stormy zones, visible from far away

Just install and play — the weather will do its thing.

## Credits

Idea by **Mr. Random** on Discord.

## License

[MIT](LICENSE)
