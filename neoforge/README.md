# NeoForge Module

A NeoForge build of Localized Weather for Minecraft 26.1.x, built separately
from the Fabric jar:

```bash
../gradlew -p neoforge build
../gradlew -p neoforge runServer
../gradlew -p neoforge runClient
```

## What it shares with Fabric

Minecraft 26.x is compiled against Mojang's own names on every loader, so the
mod is genuinely common rather than duplicated. This module compiles these
directories straight out of the Fabric tree:

| Directory | Contents |
| --------- | -------- |
| `src/shared/java` | `WeatherZone`, `StormCell`, `WindState` — no Minecraft API at all |
| `src/v26/common/java` | the zone simulation, storm cells, biome rules, the payloads and the public API |
| `src/v26/clientcommon/java` | the client zone cache, the three renderers, the sounds and the client mixins |
| `src/neoforge/main/java` | this platform's common glue |
| `src/neoforge/client/java` | this platform's client glue |

Two seams keep the shared code loader-free:

- **`WeatherSync`** — the simulation hands zone, wind and storm-cell updates to
  it; each loader supplies an implementation and calls
  `WeatherZoneManager.tick(server)` from its own server tick event.
- **`WeatherPayloads`** — the three clientbound payloads are vanilla
  `CustomPacketPayload` records with vanilla stream codecs, so the wire format
  is identical on both loaders and only registration and sending differ.

The renderers take the three things a 26.x submit-collect callback carries —
`LevelRenderState`, `SubmitNodeCollector`, `PoseStack` — rather than a
loader-specific context object. Fabric passes them from
`LevelRenderEvents.COLLECT_SUBMITS`, NeoForge from `SubmitCustomGeometryEvent`.

## What works

Everything the Fabric build shows a client, except where noted below:

- Server-side simulation — zones cycle weather, biome rules apply, wind rotates,
  storm cells spawn and drift, lightning strikes inside thunder zones
- Client sync — the three payloads are registered through
  `RegisterPayloadHandlersEvent` and land on the client thread
- Storm clouds, hail particles, the rain wall and rain bands
- Sky, fog and cloud darkening, and vanilla cloud suppression, through the same
  four client mixins the Fabric build uses
- Directional thunder and rain ambience

## What does not

The server-side mixins are not ported. The Fabric build also suppresses vanilla
weather and answers `isRainingAt` from the zone (`ServerWorldMixin`,
`RainAtMixin`, `WeatherCommandMixin`); those are not set up here, so vanilla
weather still runs alongside the zone simulation and `/weather` behaves as it
does in vanilla. Localized weather is drawn but is not yet physically real on
this platform.

## A NeoForge 26.x detail worth knowing

`@Mod(value = MOD_ID, dist = Dist.CLIENT)` on the client entry point keeps that
class, and everything it reaches, off a dedicated server. The payload handlers
are registered on both distributions — the server has to know the types to send
them — but they are written as lambda bodies so the client-only classes they
name are linked on first delivery, which never happens server-side.
