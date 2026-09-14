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

The module in [`neoforge/`](../neoforge) targets **26.1.x** and now runs the
weather simulation for real, rather than only validating an entry point.

Because 26.x is compiled against Mojang's names on both loaders, the simulation
is shared rather than duplicated: the module compiles `src/shared/java` and
`src/v26/common/java` directly out of the Fabric tree and adds only its own
glue in `src/neoforge/main/java`. `WeatherSync` is the one seam — the
simulation pushes updates into it, and each loader supplies an implementation
and calls `WeatherZoneManager.tick(server)` from its own tick event.

Server-side weather works. Client sync does not: no payloads are registered on
this platform, so it runs with `WeatherSync.NONE` and clients see nothing. It
is still not a release artifact. See [`neoforge/README.md`](../neoforge/README.md).

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
