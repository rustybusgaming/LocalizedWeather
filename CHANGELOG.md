# Changelog

All notable changes to Localized Weather will be documented in this file.

## [1.4.0] - Unreleased

Minecraft 26.1 dropped obfuscation and retired Yarn mappings, so this release
moves the whole mod onto Mojang's own names and targets the 26.x line.
**1.3.x remains the last release for Minecraft 1.21.x.**

### Added
- Moving single-cell thunderstorms — a thundery zone now spawns a travelling storm core that drifts along the wind, wanders slightly off-heading, and grows and dissipates over its own life span
- Movable rain wall — a leaning, ground-flaring precipitation curtain hangs under each storm core and travels with it
- Movable rain bands — shallower arcs of precipitation trail the core, taper off at their ends, and rotate slowly around the cell
- Distant storms keep their rain wall and rain bands: a cell past the fog horizon is scaled onto it rather than culled, so its apparent size is unchanged and a thunderstorm several zones away is still drawn
- Storm cells drive rain where their core passes, so the rain arrives with the wall and leaves with it
- `LocalWeatherAPI.getStormCells`, `getStormCellAt` and `isInStormCell` for querying moving cells
- Support for Minecraft 26.1, 26.1.1, 26.1.2 and 26.2 from a single source tree
- Version targeting: one properties file per Minecraft line in `versions/`, selected with `-Pmc=<target>`, plus a `printTarget` task
- CI build and release matrices covering every supported target

### Changed
- Ported from Yarn to Mojang mappings — Minecraft ships unobfuscated as of 26.1, so there is no intermediary namespace and Loom no longer has a remap step
- Rewrote all three renderers for the 26.x submit-node pipeline: geometry is handed to `submitCustomGeometry` on `LevelRenderEvents.COLLECT_SUBMITS` instead of being written to a `MultiBufferSource` during `WorldRenderEvents.AFTER_ENTITIES`
- Renderers read the camera from `LevelRenderContext.levelState().cameraRenderState.pos`, which is stable across 26.1 and 26.2 (26.2 moved the camera off `GameRenderer`)
- Retargeted every mixin at its 26.x name: `advanceWeatherCycle`, `setClear`/`setRain`/`setThunder`, `getPrecipitationAt`, `setupFog`, `extractRenderState`
- Requires Java 25 and Fabric Loader 0.19.5+; mixin compatibility level raised to `JAVA_25`
- Jars are now named `localweather-<mod version>+<minecraft version>.jar`

### Changed
- Vanilla's cloud layer is hidden once the storm deck has taken over the sky, so the two are not stacked; the swap uses hysteresis and only happens when the deck is already dense enough to hide them
- Storm clouds hang as their own deck below vanilla's cloud layer instead of sharing its altitude, where the two interleaved into a single flat plate — both use 12-block cells and 4-block thickness, so they were drawing into each other
- The deck is three layers at different heights, each with its own noise pattern, drift speed and opacity, so the sky has depth rather than reading as a ceiling
- Cell height and thickness vary per cell, and cells near the coverage threshold fade out, giving the deck a lumpy body and a ragged fringe instead of uniform boxes ending in a wall

### Fixed
- Localized rain is physically real, not just visual. Vanilla weather is suppressed, so `isRainingAt` could never be true and nothing in the world reacted to a storm; it is now answered from the zone at that position, which restores mobs not burning in daylight under rain, cauldrons filling, farmland hydrating and campfires going out — per zone rather than world-wide
- Thunderstorms strike lightning again. Vanilla drives lightning from its global thunder state, which the mod suppresses, so storms struck nothing at all
- Zone weather changes reach the client for the whole area it caches. Changes were broadcast only to players within 1 zone while the client is sent and keeps a 5x5 grid, so a storm two zones out stayed stale until the player crossed a zone boundary — audible, but with no clouds drawn
- Renderers draw on a POSITION_COLOR layer instead of the textured `translucentMovingBlock` layer — they emit position and colour only, and 26.x's `BufferBuilder` throws `Missing elements in vertex: UV0, UV2` rather than defaulting them, crashing the client on the first frame of weather
- Storm clouds and hail particles render again — their renderers lost their registrations in 1.3.0 and had not drawn anything since 1.3.0

### Notes
- The Quilt jar is still produced but is **untested** on 26.x: `quilt.mod.json` still declares an intermediary mapping namespace that no longer exists for an unobfuscated game
- The `neoforge/` workspace still targets 1.21.11 and has not been moved to 26.x

## [1.3.0] - 2026-08-04

### Added
- Native Quilt metadata packaged alongside Fabric metadata in the universal jar
- Zone-wide weather selection from a 5x5 surface-biome sample grid

### Changed
- Restored vanilla cloud rendering compatibility and removed the Better Clouds/YACL requirement
- Improved zone synchronization efficiency and weather transition blending

## [1.2.1] - 2026-06-07

### Added
- Hail weather zones with custom falling hail particles, storm clouds, ambient audio, and public API support
- Quilt loader compatibility guidance for running the Fabric build on Quilt

### Notes
- NeoForge support requires a dedicated loader port; the current jar remains Fabric/Quilt-compatible only

## [1.1.2] - 2026-04-16

### Added
- Directional thunder sounds — thunder plays from the direction of nearby storm zones with proximity-based volume
- Weather zone drift — global wind direction slowly rotates, weather fronts propagate from upwind neighbors
- Wind-direction cloud drift — storm clouds now move with the wind instead of fixed X-axis
- Public Weather API (`LocalWeatherAPI`) for other mods to query weather at any position or zone

### Changed
- Storm cloud renderer uses server-synced wind direction for drift

## [1.0.0] - 2026-04-16

### Added
- Per-zone localized weather system (256×256 block zones)
- Automatic weather cycling with random durations
- Biome-aware weather rules (deserts stay dry, cold biomes get snow)
- Smooth 20-second transitions between weather states
- Bilinear blending of rain/fog/sky across zone boundaries
- Directional sky/fog/cloud darkening toward approaching storms
- Minecraft-style blocky 3D storm clouds over weather zones
- Cloud drift animation matching vanilla cloud movement
- Per-cell distance fading for clean cloud horizon
- Better Clouds mod compatibility
- Fabric API 1.21.9+ support (tested on 1.21.11)
- GitHub Actions CI/CD with Modrinth and CurseForge publishing
