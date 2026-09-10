# Changelog

All notable changes to Localized Weather will be documented in this file.

## [1.4.0] - 2026-09-10

### Added
- Moving single-cell thunderstorms — a thundery zone now spawns a travelling storm core that drifts along the wind, wanders slightly off-heading, and grows and dissipates over its own life span
- Movable rain wall — a leaning, ground-flaring precipitation curtain hangs under each storm core and travels with it
- Movable rain bands — shallower arcs of precipitation trail the core, taper off at their ends, and rotate slowly around the cell
- Distant storms keep their rain wall and rain bands: a cell past the fog horizon is scaled onto it rather than culled, so its apparent size is unchanged and a thunderstorm several zones away is still drawn
- Storm cells drive rain where their core passes, so the rain arrives with the wall and leaves with it
- `LocalWeatherAPI.getStormCells`, `getStormCellAt` and `isInStormCell` for querying moving cells

### Fixed
- Storm clouds and hail particles render again — their renderers lost their registrations in 1.3.0 and had not drawn anything since

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
