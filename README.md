# Localized Weather

A Fabric mod that replaces Minecraft's global weather system with **per-zone localized weather**. Rain, snow, hail, and thunderstorms happen independently across the world, with smooth transitions at zone boundaries and Minecraft-style storm clouds.

## Loader Support

- **Fabric** — primary supported loader.
- **Quilt** — supported with native Quilt metadata and Fabric API compatibility. Install Fabric API or Quilted Fabric API in the Quilt instance.
- **NeoForge** — an isolated 1.21.11 NeoForge workspace now lives in [neoforge/README.md](neoforge/README.md). It validates the native loader entrypoint and metadata, but it is not a release artifact until the Fabric event, networking, client, and mixin integrations are ported.

## Features

- **Localized weather zones** — 256×256 block zones each have their own weather state
- **Biome-aware rules** — deserts stay dry, snowy biomes get snow, etc.
- **Hailstorms** — occasional icy hail squalls with custom falling hail particles
- **Smooth transitions** — rain/fog/sky color blend seamlessly across zone boundaries
- **Storm clouds** — blocky, Minecraft-style 3D cloud layers appear over storm zones, visible from a distance
- **Moving single-cell thunderstorms** — thundery zones spawn a travelling storm core that drifts along the wind
- **Rain wall and rain bands** — a leaning precipitation curtain hangs under each storm core, with trailing rain bands arcing behind it; both move with the storm and stay drawn when it is far away
- **Directional darkening** — sky, fog, and clouds darken toward approaching storms
- **Vanilla cloud rendering** — localized rain gradients drive Minecraft's own blocky clouds without a cloud renderer dependency, keeping the mod compatible with renderer mods such as VulkanMod

## Requirements

- Minecraft 1.21.9+
- Fabric Loader 0.19.2+ or Quilt Loader 0.19.2+
- Fabric API, or Quilted Fabric API when using Quilt

## Optional Dependencies

- [Mod Menu](https://modrinth.com/mod/modmenu) — in-game mod configuration

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) or Quilt Loader, plus [Fabric API](https://modrinth.com/mod/fabric-api)
2. Drop the mod jar into your `mods` folder
3. Launch the game

## How It Works

Weather happens automatically — no commands needed. Each 256×256 block zone rolls its own weather independently:

- **Clear skies** last 10 minutes to 2.5 hours before a chance of weather
- **Rain, hail, and storms** last 10–20 minutes before clearing
- **Biome rules** kick in automatically — deserts stay dry, cold biomes get snow instead of rain
- **Transitions** blend smoothly over 20 seconds at zone boundaries
- **Storm clouds** appear as blocky 3D cloud layers over rainy/stormy zones, visible from far away
- **Thunderstorm cells** travel across the world trailing a rain wall and rain bands you can watch approach from the horizon

Just install and play — the weather will do its thing.

## Credits

Idea by **Mr. Random** on Discord.

## License

[MIT](LICENSE)
