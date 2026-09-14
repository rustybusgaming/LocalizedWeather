# Forge Module

A Forge build of Localized Weather for Minecraft 26.1.x, built separately from
the Fabric jar:

```bash
../gradlew -p forge build
../gradlew -p forge runServer   # dedicated-server smoke test
```

## What it shares with Fabric and NeoForge

Minecraft 26.x is compiled against Mojang's own names on every loader, so the
weather simulation is genuinely common rather than duplicated. This module
compiles these directories straight out of the Fabric tree:

| Directory | Contents |
| --------- | -------- |
| `src/shared/java` | `WeatherZone`, `StormCell`, `WindState` — no Minecraft API at all |
| `src/v26/common/java` | the zone simulation, storm cells, biome rules and the public API |
| `src/forge/main/java` | this platform's glue |

The one seam is `WeatherSync`: the simulation hands zone, wind and storm-cell
updates to it, Fabric backs it with its payloads, and each loader calls
`WeatherZoneManager.tick(server)` from its own server tick event.

## What works

Server-side simulation: zones cycle weather, biome rules apply, wind rotates,
storm cells spawn and drift, and lightning strikes inside thunder zones.

## What does not

**Nothing is sent to clients yet.** Forge registers no payloads here, so the
module runs with `WeatherSync.NONE` and clients see no localized weather —
no rain gradients, no storm clouds, no rain wall.

The Fabric build also suppresses vanilla weather and answers `isRainingAt` from
the zone through mixins. Those are not set up on this platform, so vanilla
weather still runs alongside the zone simulation here.

## Two Forge 26.x details worth knowing

**The tick event lost its phase field.** Forge 26.x split `TickEvent` into
per-phase record events, each with its own static bus, so a handler subscribes
to `TickEvent.ServerTickEvent.Post.BUS` instead of taking a `TickEvent` and
checking `event.phase == Phase.END`. The server comes from `event.server()`.
Listening on the bus directly also means the compiler checks the event type,
where an annotated handler would only fail at load.

**Dev runs need resources beside the classes.** FML builds a dev-mode mod from
the single directory that contains `META-INF/mods.toml`. With Gradle's default
layout that is `build/resources/main`, which holds no classes, so the run finds
the metadata, fails to find the `@Mod` class and refuses to load the mod. The
build therefore points the source set's resources output at the classes
directory. This affects the dev run only — the packaged jar has both halves
either way.
