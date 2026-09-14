# Loader & Version Support

## Minecraft versions

Minecraft moved to calendar versioning (`26.1`, `26.2`, …) and, from 26.1, **ships
unobfuscated**. Two things follow from that, and they shape this whole branch:

- There are no Yarn mappings or intermediary namespace for 26.x — Yarn's last
  build is for 1.21.11. Loom compiles straight against Mojang's own names.
- Because nothing is remapped, Loom has no `remapJar` step; the `jar` task
  already produces the shippable artifact.

The mod targets Mojang names, so it builds for 1.21.11 **only** from the older
tag/branch. The `1.3.x` line is the last release for 1.21.x.

### Supported targets

| Target | Line | Covers | Fabric API | Java |
| ------ | ---- | ------ | ---------- | ---- |
| `1.21.9` | `v1_21_9` | 1.21.9 | 0.134.1+1.21.9 | 21 |
| `1.21.10` | `v1_21_10` | 1.21.10 | 0.138.4+1.21.10 | 21 |
| `1.21.11` | `v1_21` | 1.21.11 | 0.141.6+1.21.11 | 21 |
| `26.1.2` | `v26` | 26.1, 26.1.1, 26.1.2 | 0.155.3+26.1.2 | 25 |
| `26.2` | `v26` | 26.2 | 0.159.0+26.2 | 25 |

### Why each 1.21.x point release needs its own line

Minecraft's rendering API broke between every one of them, so the line cannot
be shared even within 1.21:

- **1.21.10** renamed the layer accessor: `RenderLayer.getTranslucentMovingBlock()`
  where 1.21.11 has `RenderLayers.translucentMovingBlock()`.
- **1.21.11** replaced two mixin target signatures the older releases still use:
  `AtmosphericFogModifier.applyStartEndModifier` takes a `Camera` where 1.21.9
  and 1.21.10 take an entity plus a block position, and
  `SkyRendering.updateRenderState` takes a `Camera` where they take a `Vec3d`.
  An injected method's descriptor must match its target exactly, so these cannot
  be shared even though the bodies are identical.
- **1.21.9** has no world-render event in Fabric API at all. Its
  `fabric-rendering-v1` (16.0.1) ships no `WorldRenderEvents` in any package —
  it reappears as `…rendering.v1.world.WorldRenderEvents` only from the
  16.2.x builds. So on 1.21.9 there is nothing public to hang custom world
  geometry on, and that target ships **without the storm clouds, hail particles
  and rain wall**. Zone weather, the rain and fog gradients, sky darkening,
  precipitation type and directional thunder are all mixin-driven and work
  normally there.

### Why the 1.21 and 26 lines are separate

The two lines cannot share Minecraft-facing code, and not because of the API
changes — because of naming:

- **1.21.x must be mapped.** Fabric API's 1.21.x builds ship their access
  wideners in the `intermediary` namespace, so an unmapped build is rejected
  outright. Mojang mappings are refused too (`Cannot use Mojang mappings in a
  non-obfuscated environment`), which leaves Yarn as the only usable set.
- **26.x cannot be mapped.** No intermediary namespace exists for it, and the
  new Loom plugin has no mappings step at all.

So every Minecraft symbol is spelled differently between the lines —
`MinecraftClient`/`Minecraft`, `ServerWorld`/`ServerLevel`,
`Identifier`/`ResourceLocation` — before any real API change is considered.
Marking that up inline would put two versions of nearly every line in every
file, so each line gets its own directory instead:

```
src/shared/java     classes that touch no Minecraft API (WeatherZone,
                    StormCell, WindState) — genuinely common
src/v1_21/...       the 1.21.x line, Yarn names
src/v26/...         the 26.x line, Mojang names
```

`build.gradle` points the `main` and `client` source sets at `src/shared` plus
the active line.

### Two Loom plugins

`fabric-loom` and `net.fabricmc.fabric-loom` are different plugins that happen
to share an artifact and version. The first is the mapped, obfuscated-era one
1.21.x needs; the second is the unobfuscated-era one 26.x needs, and each
refuses the other's world. The build declares both with `apply false` and
applies whichever the target calls for.

They also differ on the Gradle daemon JVM: the classic plugin insists the
daemon itself runs Java 25 for a 26.x game, while the new one is happy with a
Java 21 daemon and a Java 25 toolchain. CI installs both JDKs for that reason.

Each target is one file in [`versions/`](../versions). Build one with:

```bash
./gradlew build            # default_target from gradle.properties
./gradlew build -Pmc=26.2  # any file name in versions/
./gradlew printTarget      # show what the current target resolves to
```

Adding a new Minecraft line is a new properties file plus an entry in the CI
matrix in `.github/workflows/`. No source changes are needed while the game's
API holds still.

### Toolchain

26.x needs **Java 25** and **Gradle 9.5+** (Loom 1.17 requires it; the wrapper is
pinned to 9.7.1 — use `gradlew`, not a system Gradle). The Foojay toolchain
resolver is applied in `settings.gradle`, so Gradle downloads a matching JDK
itself rather than failing with "Cannot find a Java installation ... matching:
{languageVersion=25}".

### Keeping one source tree across versions

26.2 moved the camera off `GameRenderer`, so the renderers read the frame's
camera position from `LevelRenderContext.levelState().cameraRenderState.pos`,
which is identical on both lines — and is the position the frame is actually
drawn from. Prefer that kind of common API over a version-conditional branch.

### Vertex formats in custom world geometry

The three renderers emit position and colour only. `translucentMovingBlock` is a
textured block layer whose vertex format also wants UV0, UV2 and Normal, and
`BufferBuilder` throws `Missing elements in vertex` rather than defaulting them —
on 1.21.10 and 1.21.11 as well as on 26.x. Every line therefore draws on the
debug filled-box layer, which is a POSITION_COLOR / QUADS snippet with
translucent blending and culling left on: it matches this geometry exactly,
without inventing texture, lightmap or normal data.

## Fabric

The primary supported loader. The jar uses Fabric Loader entrypoints, Fabric API
events, Fabric networking, and Fabric level render callbacks.

Note that 26.x replaced immediate-mode world rendering with a submit-node
pipeline: `WorldRenderEvents`/`WorldRenderContext` became
`LevelRenderEvents`/`LevelRenderContext`, and geometry is handed to
`submitNodeCollector().submitCustomGeometry(...)` rather than written into a
`MultiBufferSource` during the event.

## Quilt

Quilt is shipped **for the 1.21.x line only**, as the Fabric jar minus
`fabric.mod.json` plus native `quilt.mod.json` metadata.

It is not shipped for 26.x. Quilt resolves mods through an intermediate
namespace, and it publishes none for that line — `meta.quiltmc.org` serves
hashed mappings for 1.21.11 and returns 404 for 26.1.2. A 26.x Quilt jar could
only declare a namespace that does not exist for that game, so the build skips
the Quilt jar when the target is unmapped and `quilt.mod.json` lives in
`src/v1_21/`.

## NeoForge

The module in [`neoforge/`](../neoforge) targets **26.1.x** and runs both halves
of the mod: the simulation server-side and the full client presentation.

Because 26.x is compiled against Mojang's names on every loader, almost none of
it is duplicated. The module compiles `src/shared/java`, `src/v26/common/java`
and `src/v26/clientcommon/java` directly out of the Fabric tree and adds only
its own glue in `src/neoforge/main/java` and `src/neoforge/client/java`.

Three things keep that tree loader-free:

- **`WeatherSync`** — the simulation pushes zone, wind and storm-cell updates
  into it; each loader supplies an implementation and calls
  `WeatherZoneManager.tick(server)` from its own tick event.
- **`WeatherPayloads`** — the three clientbound payloads are vanilla
  `CustomPacketPayload` records with vanilla stream codecs, so the wire format
  is the same code on both loaders. Fabric registers them through
  `PayloadTypeRegistry` and sends with `ServerPlayNetworking`; NeoForge
  registers through `RegisterPayloadHandlersEvent` and sends with
  `PacketDistributor`.
- **The renderers take vanilla types.** A 26.x submit-collect callback carries a
  `LevelRenderState`, a `SubmitNodeCollector` and a `PoseStack` on either
  loader, so the renderers take those three directly rather than a
  loader-specific context. Fabric passes them from
  `LevelRenderEvents.COLLECT_SUBMITS`, NeoForge from
  `SubmitCustomGeometryEvent`.

The four client mixins are shared verbatim — they touch no loader API, so each
platform just declares the same config.

NeoForge ships as a release artifact alongside the Fabric jars, built by its own
job in the release workflow — it is a separate Gradle build with its own jar and
no Fabric API dependency, so it cannot ride the `-Pmc` matrix.

The three server-side mixins are shared too, so localized weather is physically
real on NeoForge rather than only drawn: vanilla's global weather is
suppressed, `isRainingAt` is answered from the zone, and `/weather` applies to
the player's zone. See [`neoforge/README.md`](../neoforge/README.md).

`@Mod(value = MOD_ID, dist = Dist.CLIENT)` keeps the client entry point, and
everything it reaches, off a dedicated server. Payload handlers are registered
on both distributions — the server needs the types to send them — but are
written as lambda bodies so the client-only classes they name link on first
delivery, which never happens server-side.

## Forge

The module in [`forge/`](../forge) targets **26.1.x** and sits at the same level
as the NeoForge one: it compiles `src/shared/java` and `src/v26/common/java`
straight out of the Fabric tree, adds its glue in `src/forge/main/java`, and
runs the zone simulation, storm cells and lightning server-side. Client sync is
not ported, so it runs with `WeatherSync.NONE` and is not a release artifact.

Two things differ from NeoForge in more than spelling:

- **Tick events lost their phase field.** Forge 26.x split `TickEvent` into
  per-phase record events, each carrying its own static `EventBus`, so the glue
  subscribes to `TickEvent.ServerTickEvent.Post.BUS` rather than annotating a
  handler and testing `event.phase`. The server is `event.server()`.
- **Dev runs need the mod's resources beside its classes.** FML's classpath
  locator builds a dev-mode mod file from the one directory holding
  `META-INF/mods.toml`; under Gradle's default layout that is
  `build/resources/main`, which contains no classes, so the run reports the
  declared mod as missing. `forge/build.gradle` points the source set's
  resources output at the classes directory to put both halves where the
  locator looks. The packaged jar is unaffected.

See [`forge/README.md`](../forge/README.md).
