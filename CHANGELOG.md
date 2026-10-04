# Changelog

All notable changes to Localized Weather will be documented in this file.

## [1.4.1] - 2026-10-04

### Added
- Support for **Minecraft 26.3**, built with `-Pmc=26.3` and published alongside the other targets
- **A server-side config file**, `config/localweather.properties`, written with the defaults on first start. It tunes how long zones stay dry and wet, lightning (including off) and its rarity, how many travelling storm cells a world may hold (including none), and whether vanilla's global weather stays suppressed. `LocalWeatherConfig` lives in `src/shared/java` and touches no Minecraft or loader API, so one file serves every loader and every supported version with no per-loader config plumbing. Zone size and the sync intervals are deliberately not configurable — the client caches and renderers are built around them, so changing one would desync every connected player
- **A Shaders section in the README and `docs/loader-support.md`**, answering what a shader pack does and does not pick up: zone weather drives the level's `rainLevel`, so a pack's wetness and puddles follow your zone for free; the sky-tint and fog mixins get overridden by the pack's own sky and fog; and the three overlays sit on a debug render type and are untested under a pack. Forge has no shader option at all, since Oculus stopped at Minecraft 1.20.1
- **NeoForge for Minecraft 1.21.1**, built with `./gradlew -p neoforge build -Pmc=1.21.1` and published as `localweather-<mod version>+1.21.1-neoforge.jar`. It shares only `src/shared/java` with the 26.x target — 1.21.1 is compiled against the obfuscated-era API NeoForge remaps for it, so it has its own source tree, its own `neoforge.mods.toml` and its own mixin configs
- Forge is now a published release artifact and runs the whole mod, not just the simulation: zone weather, wind and storm cells sync to clients, and the rain gradients, sky darkening, fog, storm clouds, hail and rain wall all draw. Its jar is `localweather-<mod version>+<minecraft version>-forge.jar`
- **Zone weather is saved with the world.** Every zone and the wind are written to `localweather_zones.dat` beside `level.dat` from inside vanilla's own world save, so autosave, `/save-all` and shutdown all write it, on every loader, from one mixin. Minecraft's clock does not run while a server is off and neither does the weather: a restored zone resumes with exactly the time it had left. Zones come back dormant and wake through the same catch-up path as a zone you walked back into. The file is plain versioned text rather than NBT, because NBT's API has changed between every version this mod supports and the store lives in `src/shared` and touches none of it. It is written to a temporary file and moved into place, so a crash mid-save keeps the previous one; a missing, unreadable or foreign file means fresh weather, as before, and a bad line costs only that line
- **`/localweather`**, open to every player: the weather where you stand and roughly how long it has left, a transition in progress, which way the wind is blowing from and whether it is turning, the nearest storm cell's distance and bearing, and how many zones the dimension holds. It is registered alongside vanilla's `/weather`, so the same hook serves Fabric, Quilt, NeoForge and Forge
- **`/reload` re-reads `config/localweather.properties`**, so durations, lightning and storm cells can be changed without a restart. `/reload` is already operator-only on every version, so the config needs no permission handling of its own
- **Storm cells and hail now rain on the server too.** The client already drew rain under a passing storm core and in hail zones, but the server called neither of them rain, so fires kept burning and cauldrons stayed dry. `isRainingAt` now agrees with what is drawn, with vanilla's own exposure and biome rules on top, and a storm core strikes lightning even where the zone beneath it is not thundery
- **A test suite**, the repository's first. `src/shared` touches no Minecraft API, so its tests are plain JUnit 5: they run on every target without starting the game, and CI runs them on each one as part of `build`. It covers zone retention — survival, catch-up, expiry, transitions, eviction — the zone store's round trip and its handling of damaged files, the `/localweather` wording, and the config's parsing rules. Reintroducing the old zone-deleting behaviour fails seven of the nine retention tests
- CI builds the NeoForge and Forge modules on every push and pull request; they are separate Gradle builds, so they run as their own matrix rather than riding `-Pmc`

### Fixed
- **Walking away from a storm deleted it.** A zone was thrown away the moment no player was within range — about 512 blocks — and rebuilt from scratch, seventy per cent of the time clear, when anyone came back. So a thunderstorm you rode out of was simply gone when you turned around. A zone nobody is near now goes dormant instead: it is kept, stops being ticked, and is caught up on the time it missed when a player returns, so a storm that should still be raining still is and one that should have blown over has. Idle zones cost nothing per tick, and memory is capped at 4096 remembered zones per world — roughly sixteen thousand blocks square — with the longest-empty dropped first. Dormant zones are invisible to `isRainingAt`, the API and storm-cell spawning, so unwatched areas behave exactly as before; only the memory is new. Checked in game on 26.3: a hail zone left for 600 ticks was the same zone on return, dormant while away, with exactly 600 ticks taken off
- **A second world opened in the same game inherited the first one's weather.** Zones, storm cells and the wind are held in statics keyed by dimension, and nothing cleared them when a world closed, so quitting one singleplayer world and opening another carried the first world's overworld zones into the second. The simulation now notices a new server instance and starts over from that world's own save
- **A world could stay raining everywhere, for good, on the server.** Suppressing vanilla's weather cancels its weather cycle, and that cycle is also what lets vanilla's own rain run out. So a world that was raining when the mod was installed, or one given `/weather rain` or `thunder` (vanilla applies those globally as well as to the zone), kept vanilla's global rain switched on permanently: `isRainingAt` said yes in every clear zone, so fires went out and cauldrons filled under a clear sky, and after `/weather thunder` vanilla's own lightning could strike anywhere. Vanilla's weather is now held at clear while it is suppressed
- **`/gamerule doWeatherCycle false` did nothing to zone weather.** Zones kept turning over with the rule off, so a build server or minigame map that froze vanilla's weather still had rain coming and going. Zones now hold their weather while the rule is off (`advance_weather` from 1.21.11), and a dormant zone's clock is held too, so the time the rule was off is not spent when a player comes back. `/weather` still works, and a transition already under way still finishes
- **Weather could be missing after joining or going through a portal**, roughly one time in twenty, until the player walked into another zone. The client cleared its zone cache when it saw a new level, but only on its next tick, and the first zone updates for that level could arrive in the same frame as the level itself and be cleared with it. Unchanged zones are not re-sent, so the sky stayed clear over a storm. The update handler now notices the new level itself
- **In singleplayer, standing in a snow zone made the server think it was snowing everywhere.** The client's snow override on `Biome.getPrecipitationAt` also ran on the integrated server, which shares the class, so every server-side precipitation check answered snow: rain stopped putting out fires in other zones, and cauldrons in the rain filled with powder snow. The override now applies only on the client thread
- **Rain could be heard deep underground.** The rain ambience played around the player at full volume however far below the surface they were. It now fades out over the first twenty blocks below the surface, and a roof overhead barely changes it
- **The NeoForge 1.21.1 target did not build.** `neoforge/build.gradle` switched the Minecraft version, NeoForge version and Java level for `-Pmc=1.21.1` but left its source and resource directories pointing at the 26.x tree, so the target compiled 26.x code against 1.21.1 and failed outright. CI did not catch it because the module was only ever built on its default target; both targets are now in the build matrix, and both are published

### Changed
- The 26.x client mixins that follow Mojang's render API now live in a variant directory per API era, selected by `client_variant` in the target's properties file. 26.3 changed `SkyRenderState.skyColor` from a packed ARGB `int` to a `Vector3fc`, and rewrote `CloudRenderer.render` against the new render-pass API, overloading the name — so both copies now spell out the mixin target descriptor instead of matching on the bare name. Everything else in the 26.x line stays shared
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
