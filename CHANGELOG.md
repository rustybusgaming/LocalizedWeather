# Changelog

All notable changes to Localized Weather will be documented in this file.

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
