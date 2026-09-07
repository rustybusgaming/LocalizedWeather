<div align="center">

<img src="src/main/resources/assets/localweather/icon.png" alt="Localized Weather" width="128">

# Localized Weather

**Weather stops being a switch and starts being a place.**

Rain, snow, hail and thunderstorms happen independently across the world — you can stand in sunshine and watch a storm roll in over the hills.

[![Build](https://github.com/rustybusgaming/LocalizedWeather/actions/workflows/build.yml/badge.svg)](https://github.com/rustybusgaming/LocalizedWeather/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1%20%E2%80%93%2026.2-brightgreen)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

</div>

---

## What it does

Vanilla Minecraft has one weather state for the entire world. Localized Weather replaces it with a grid of **256×256 block zones**, each rolling its own weather on its own schedule — then blends the seams so you never see a hard edge.

The result is weather with geography. Storms have a place they *are*, a direction they came from, and a direction they are going.

## Features

|     | Feature | What you actually see |
| :-: | ------- | --------------------- |
| 🗺️ | **Localized zones** | Every 256×256 block zone runs its own weather independently |
| ⛈️ | **Moving thunderstorm cells** | Single-cell storms drift along the wind, grow, and dissipate on their own life span |
| 🌧️ | **Rain wall & rain bands** | A leaning curtain of rain hangs under each storm core, with trailing bands arcing behind it — and it stays drawn when the storm is far away |
| 🌾 | **Biome-aware rules** | Biomes without precipitation stay dry, cold biomes turn rain into snow, and each zone is decided from a 5×5 surface sample so one desert patch doesn't dry out a whole zone |
| 🧊 | **Hailstorms** | Occasional icy squalls with custom falling hail particles |
| 🌬️ | **Wind-driven fronts** | A global wind direction slowly rotates; weather propagates from upwind neighbours |
| 🎚️ | **Seamless transitions** | Rain, fog and sky colour blend bilinearly across zone boundaries over 20 seconds |
| ☁️ | **Storm clouds** | Blocky, Minecraft-style cloud layers over stormy zones, visible from far off |
| 🌑 | **Directional darkening** | Sky, fog and clouds darken *toward* the approaching storm, not uniformly |
| 🔊 | **Directional thunder** | Thunder plays from the bearing of the storm, with proximity-based volume |
| 🧩 | **Renderer-friendly** | Drives Minecraft's own cloud renderer instead of replacing it, so mods like VulkanMod still work |

## How it works

Weather happens automatically. There is nothing to configure and no commands to learn.

| Phase | Duration | Notes |
| ----- | -------- | ----- |
| Clear skies | 10 min – 2.5 h | Then a chance of weather rolls |
| Rain / hail / storm | 10 – 20 min | Before the zone clears again |
| Zone transitions | 20 s | Blended across the boundary, never a hard cut |
| Wind shift | every 2.5 – 10 min | Slowly rotates; fronts follow it |

A zone that turns thundery spawns a **storm cell** — a travelling core 70–130 blocks across that lives for 4–10 minutes, moves at roughly 3–6 blocks per second, and carries its rain wall and rain bands with it. When the core passes over you, the rain arrives with the wall and leaves once it has gone by.

Storms further away than the fog horizon are not culled. Their geometry is projected onto the horizon at unchanged apparent size, so a thunderstorm several zones out is still visible as a rain wall on the skyline.

## Requirements

- Minecraft **26.1 – 26.2** (see the version table below)
- **Fabric Loader 0.19.5+**
- **Fabric API**
- Java 25

> Playing on 1.21.x? Use the **1.3.x** releases. Minecraft 26.1 dropped
> obfuscation and retired Yarn mappings, so 26.x builds against Mojang's own
> names — see [docs/loader-support.md](docs/loader-support.md).

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Drop the jar matching your Minecraft version into your `mods` folder
3. Launch the game — the weather takes it from there

**Optional:** [Mod Menu](https://modrinth.com/mod/modmenu) for in-game mod info.

## Versions & loaders

| Minecraft | Build target | Fabric API | Java |
| --------- | ------------ | ---------- | ---- |
| 26.1, 26.1.1, 26.1.2 | `-Pmc=26.1.2` (default) | 0.155.3+26.1.2 | 25 |
| 26.2 | `-Pmc=26.2` | 0.159.0+26.2 | 25 |
| 1.21.9 – 1.21.11 | 1.3.x releases | — | 21 |

One source tree builds every 26.x target; each target is a file in [`versions/`](versions).

| Loader | Status |
| ------ | ------ |
| **Fabric** | ✅ Primary supported loader |
| **Quilt** | ⚠️ A Quilt jar is still produced, but it is untested on 26.x — see [docs/loader-support.md](docs/loader-support.md) |
| **NeoForge** | 🚧 Isolated workspace in [`neoforge/`](neoforge/README.md), still on 1.21.11 and not a release artifact |

See [docs/loader-support.md](docs/loader-support.md) for the full breakdown.

## For mod developers

`LocalWeatherAPI` lets other mods query localized weather at any position.

```java
import net.fentbusgaming.localweather.api.LocalWeatherAPI;

// What is the weather right here?
WeatherZone.WeatherType weather = LocalWeatherAPI.getWeatherAt(world, pos);

if (LocalWeatherAPI.isThunderingAt(world, pos)) {
    // lightning-rod logic, mob spawning, crop growth...
}

// Is this position under a moving storm core?
if (LocalWeatherAPI.isInStormCell(world, pos)) {
    // heavy rain, reduced visibility...
}

// Where is the weather coming from?
double windX = LocalWeatherAPI.getWindDirectionX();
double windZ = LocalWeatherAPI.getWindDirectionZ();
```

Also available: `getWeatherInZone`, `getTargetWeatherInZone`, `getTransitionProgress`, `isRainingAt`, `isHailingAt`, `getStormCells`, `getStormCellAt`, `toZoneCoords` and `getZoneSizeBlocks`.

## Building from source

```bash
git clone https://github.com/rustybusgaming/LocalizedWeather.git
cd LocalizedWeather
./gradlew build            # default target (26.1.2)
./gradlew build -Pmc=26.2  # any target in versions/
./gradlew printTarget      # show the resolved target
```

Needs **JDK 25** — Minecraft 26.x is compiled for it. Jars land in `build/libs/`
as `localweather-<mod version>+<minecraft version>.jar`.

## Credits

Idea by **Mr. Random** on Discord.

## License

[MIT](LICENSE)
