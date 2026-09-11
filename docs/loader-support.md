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

| Target | Covers | Fabric API | Java |
| ------ | ------ | ---------- | ---- |
| `26.1.2` | 26.1, 26.1.1, 26.1.2 | 0.155.3+26.1.2 | 25 |
| `26.2` | 26.2 | 0.159.0+26.2 | 25 |

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

A Quilt-flavoured jar is still produced (the Fabric jar minus `fabric.mod.json`)
and native Quilt metadata is still packaged. **It is untested on 26.x.** Quilt
Loader tracks the 26.x game versions, but `quilt.mod.json` still declares
`intermediate_mappings: net.fabricmc:intermediary`, and there is no intermediary
namespace for an unobfuscated game. Treat Quilt on 26.x as unverified until
someone runs it.

## NeoForge

The isolated workspace in [`neoforge/`](../neoforge) still targets 1.21.11 and has
not been moved to 26.x. It validates the loader entrypoint and metadata only; the
event, networking, client, rendering and mixin integrations are still not ported,
so it is not a release artifact.
