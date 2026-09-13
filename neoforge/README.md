# NeoForge Module

A NeoForge build of Localized Weather for Minecraft 26.1.x, built separately
from the Fabric jar:

```bash
../gradlew -p neoforge build
```

## What it shares with Fabric

Minecraft 26.x is compiled against Mojang's own names on both loaders, so the
weather simulation is genuinely common rather than duplicated. This module
compiles these directories straight out of the Fabric tree:

| Directory | Contents |
| --------- | -------- |
| `src/shared/java` | `WeatherZone`, `StormCell`, `WindState` — no Minecraft API at all |
| `src/v26/common/java` | the zone simulation, storm cells, biome rules and the public API |
| `src/neoforge/main/java` | this platform's glue |

The one seam is `WeatherSync`: the simulation hands zone, wind and storm-cell
updates to it, Fabric backs it with its payloads, and each loader calls
`WeatherZoneManager.tick(server)` from its own server tick event.

## What works

Server-side simulation: zones cycle weather, biome rules apply, wind rotates,
storm cells spawn and drift, and lightning strikes inside thunder zones.

## What does not

**Nothing is sent to clients yet.** NeoForge registers no payloads here, so the
module runs with `WeatherSync.NONE` and clients see no localized weather —
no rain gradients, no storm clouds, no rain wall. Porting that means
registering the three payloads through `RegisterPayloadHandlersEvent` and
porting the client handlers and renderers.

The Fabric build also suppresses vanilla weather and answers `isRainingAt` from
the zone through mixins. Those are not set up on this platform, so vanilla
weather still runs alongside the zone simulation here.
