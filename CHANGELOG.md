# Changelog

All notable changes to Localized Weather will be documented in this file.

## [1.2.1] - 2026-06-07

### Added
- Hail weather zones with custom falling hail particles, storm clouds, ambient audio, and public API support

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
