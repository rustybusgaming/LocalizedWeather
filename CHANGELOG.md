# Changelog

All notable changes to Localized Weather will be documented in this file.

## [Unreleased]

### Added
- Forge is now a published release artifact and runs the whole mod, not just the simulation: zone weather, wind and storm cells sync to clients, and the rain gradients, sky darkening, fog, storm clouds, hail and rain wall all draw. Its jar is `localweather-<mod version>+<minecraft version>-forge.jar`
- CI builds the NeoForge and Forge modules on every push and pull request; they are separate Gradle builds, so they run as their own matrix rather than riding `-Pmc`

### Changed
- The Forge jar carries a `-forge` classifier and a `+<minecraft version>` in its name, matching the NeoForge one, so a release's assets are told apart by name
- Documented that the Quilt jar wants Fabric API rather than Quilted Fabric API, whose newest build is for Minecraft 1.21; re-checked Quilt's hashed mappings, which still stop at 1.21.11, so 26.x remains Quilt-less

## [1.4.0] - 2026-09-14

The multi-version release. One repository now builds **Minecraft 1.21.9,
1.21.10, 1.21.11, 26.1.x and 26.2** — pick a target with `-Pmc=<target>`, each
one a file in `versions/`. Minecraft 26.1 dropped obfuscation and retired Yarn
mappings, so the 26.x line compiles against Mojang's own names while the 1.21.x
line stays on Yarn. The two keep separate source directories for that reason and
share everything that touches no Minecraft API.

### Added
- Moving single-cell thunderstorms — a thundery zone now spawns a travelling storm core that drifts along the wind, wanders slightly off-heading, and grows and dissipates over its own life span
- Movable rain wall — a leaning, ground-flaring precipitation curtain hangs under each storm core and travels with it
- Movable rain bands — shallower arcs of precipitation trail the core, taper off at their ends, and rotate slowly around the cell
- Distant storms keep their rain wall and rain bands: a cell past the fog horizon is scaled onto it rather than culled, so its apparent size is unchanged and a thunderstorm several zones away is still drawn
- Storm cells drive rain where their core passes, so the rain arrives with the wall and leaves with it
- `LocalWeatherAPI.getStormCells`, `getStormCellAt` and `isInStormCell` for querying moving cells
- Support for Minecraft 1.21.9, 1.21.10, 1.21.11, 26.1.x and 26.2 from a single repository
- Version targeting: one properties file per Minecraft line in `versions/`, selected with `-Pmc=<target>`, plus a `printTarget` task
- CI build and release matrices covering every supported target
- Mod Menu integration on Fabric and Quilt: the mod's entry now carries Website, Source and Issues links, and a description that matches what the mod actually does
- NeoForge is now a published release artifact, running the zone simulation, the full client presentation and the server-side mixins from the same code the Fabric build runs. Its jar is `localweather-<mod version>+<minecraft version>-neoforge.jar`
- Forge module in `forge/` for 26.1.x, running the shared simulation server-side

### Changed
- The 26.x line is compiled against Mojang's names — Minecraft ships unobfuscated as of 26.1, so there is no intermediary namespace and Loom no longer has a remap step there
- Rewrote the 26.x renderers for its submit-node pipeline: geometry is handed to `submitCustomGeometry` on `LevelRenderEvents.COLLECT_SUBMITS` instead of being written to a `MultiBufferSource` during `WorldRenderEvents.AFTER_ENTITIES`
- Renderers read the camera from the frame's own render state, which is stable across 26.1 and 26.2 (26.2 moved the camera off `GameRenderer`)
- Retargeted every 26.x mixin at its new name: `advanceWeatherCycle`, `setClear`/`setRain`/`setThunder`, `getPrecipitationAt`, `setupFog`, `extractRenderState`
- 26.x requires Java 25 and Fabric Loader 0.19.5+, with mixin compatibility level `JAVA_25`; 1.21.x still runs on Java 21
- Jars are now named `localweather-<mod version>+<minecraft version>.jar`
- The 26.x weather simulation is loader-agnostic and shared rather than duplicated — Minecraft 26.x uses Mojang's names on Fabric, NeoForge and Forge alike, so only the glue differs. `WeatherSync` is the seam: each loader supplies networking and calls `WeatherZoneManager.tick(server)` from its own tick event
- The client half of the 26.x line moved to `src/v26/clientcommon`, shared with NeoForge. The payloads moved to `src/v26/common` — they are vanilla `CustomPacketPayload` types, so the wire format is one piece of code on every loader — and the renderers take the `LevelRenderState`, `SubmitNodeCollector` and `PoseStack` that a 26.x submit-collect callback carries on either loader, instead of a loader-specific render context
- Quilt is built for the 1.21.x line only. Quilt resolves mods through an intermediate namespace and publishes none for 26.x — its hashed mappings 404 for 26.1.2 — so a 26.x Quilt jar could only declare a namespace that does not exist
- Vanilla's cloud layer is hidden once the storm deck has taken over the sky, so the two are not stacked; the swap uses hysteresis and only happens when the deck is already dense enough to hide them
- Storm clouds hang as their own deck below vanilla's cloud layer instead of sharing its altitude, where the two interleaved into a single flat plate — both use 12-block cells and 4-block thickness, so they were drawing into each other
- The deck is three layers at different heights, each with its own noise pattern, drift speed and opacity, so the sky has depth rather than reading as a ceiling
- Cell height and thickness vary per cell, and cells near the coverage threshold fade out, giving the deck a lumpy body and a ragged fringe instead of uniform boxes ending in a wall

### Fixed
- **1.21.9 and 1.21.10 could not start a client at all.** Both carried 1.21.11's signatures for `FogMixin` (`applyStartEndModifier` takes an entity and a block position on those versions, a `Camera` on 1.21.11) and `SkyColorMixin` (`updateRenderState` takes a `Vec3d`, not a `Camera`). An injected method's descriptor has to match its target exactly, so mixin application failed outright and the game never reached the title screen
- **The renderers crashed on the first frame of weather they drew**, on 1.21.10 and 1.21.11 as well as 26.x. All three wrote position and colour into `translucentMovingBlock`, a textured block layer whose vertex format also wants UV0, UV2 and Normal; `BufferBuilder` throws `Missing elements in vertex` rather than defaulting them. Every line now draws on the debug filled-box layer, which is POSITION_COLOR
- Localized rain is physically real, not just visual. Vanilla weather is suppressed, so `isRainingAt` could never be true and nothing in the world reacted to a storm; it is now answered from the zone at that position, which restores mobs not burning in daylight under rain, cauldrons filling, farmland hydrating and campfires going out — per zone rather than world-wide
- Thunderstorms strike lightning again. Vanilla drives lightning from its global thunder state, which the mod suppresses, so storms struck nothing at all
- Zone weather changes reach the client for the whole area it caches. Changes were broadcast only to players within 1 zone while the client is sent and keeps a 5x5 grid, so a storm two zones out stayed stale until the player crossed a zone boundary — audible, but with no clouds drawn
- Storm clouds and hail particles render again — their renderers lost their registrations in 1.3.0 and had not drawn anything since

### Notes
- **Forge is not a release artifact.** It is server-side only, with no client sync, so it is built but not published
- On 1.21.9 the storm clouds, hail particles and rain wall are absent: Fabric API for that version exposes no world-render hook. Everything else works there

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
