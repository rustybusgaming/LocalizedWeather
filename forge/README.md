# Forge Module

A Forge build of Localized Weather for Minecraft 26.1.x, built separately from
the Fabric jar:

```bash
../gradlew -p forge build
../gradlew -p forge runServer   # dedicated-server smoke test
../gradlew -p forge runClient
```

## What it shares with Fabric and NeoForge

Minecraft 26.x is compiled against Mojang's own names on every loader, so the
weather simulation, the client presentation and the mixins are genuinely common
rather than duplicated. This module compiles these directories straight out of
the Fabric tree:

| Directory | Contents |
| --------- | -------- |
| `src/shared/java` | `WeatherZone`, `StormCell`, `WindState` — no Minecraft API at all |
| `src/v26/common/java` | the zone simulation, storm cells, biome rules, the payloads, the server mixins and the public API |
| `src/v26/clientcommon/java` | the client caches, the three renderers, the sounds and the client mixins |
| `src/forge/main/java` | this platform's server glue |
| `src/forge/client/java` | this platform's client glue |

The one seam is `WeatherSync`: the simulation hands zone, wind and storm-cell
updates to it, and each loader backs it with its own networking.

## What works

Everything the NeoForge module does. Server-side: zones cycle weather, biome
rules apply, wind rotates, storm cells spawn and drift, lightning strikes inside
thunder zones, vanilla weather is suppressed, `isRainingAt` is answered from the
zone and `/weather` applies to the player's zone. Client-side: zone weather,
wind and storm cells sync, and the rain gradients, sky darkening, fog, storm
clouds, hail and rain wall all draw.

## Four Forge 26.x details worth knowing

**The tick event lost its phase field.** Forge 26.x split `TickEvent` into
per-phase record events, each with its own static bus, so a handler subscribes
to `TickEvent.LevelTickEvent.Post.BUS` instead of taking a `TickEvent` and
checking `event.phase == Phase.END`. Listeners go on that bus directly:
`MinecraftForge.EVENT_BUS.register(this)` throws `IllegalArgumentException:
Only a single listener found in class …` for a class holding one listener, and
the bus API is type-checked at compile time where an annotated handler would
only fail at load.

**There is no level-render event.** Fabric has
`LevelRenderEvents.COLLECT_SUBMITS` and NeoForge has
`SubmitCustomGeometryEvent`; Forge has neither, so the submit phase is reached
by mixin — `LevelRenderer.submitBlockDestroyAnimation`, which is a named method
called unconditionally one statement before the custom-geometry pass and takes
exactly the `PoseStack`, `SubmitNodeCollector` and `LevelRenderState` the
renderers need. See `src/forge/client/java/.../mixin/LevelRenderMixin.java`.

**Mixin configs come from the jar manifest.** Forge discovers them from the
`MixinConfigs` manifest attribute. The `[[mixins]]` block that NeoForge reads
out of `mods.toml` is ignored here, silently, so a config declared only there
never loads and every mixin in it is quietly inert. Forge also bundles stock
Mixin 0.8.7, whose highest compatibility level is `JAVA_21` — `JAVA_25` fails
with `MixinInitialisationError`, and Java 25 class files load fine at
`JAVA_21` anyway.

**Dev runs need resources beside the classes.** FML builds a dev-mode mod from
the single directory that contains `META-INF/mods.toml`. With Gradle's default
layout that is `build/resources/main`, which holds no classes, so the run finds
the metadata, fails to find the `@Mod` class and refuses to load the mod. The
build therefore points the source set's resources output at the classes
directory — which is also where the dev-only `META-INF/MANIFEST.MF` carrying
the mixin configs is generated. This affects dev runs only; the packaged jar
has both halves either way.
