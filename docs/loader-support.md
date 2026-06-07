# Loader Support

Localized Weather is currently built as a Fabric mod.

## Fabric

Fabric is the primary supported loader. The jar uses Fabric Loader entrypoints, Fabric API events, Fabric networking, and Fabric client rendering callbacks.

## Quilt

Quilt is supported through Quilt Loader's Fabric mod compatibility. Use the same Fabric jar in a Quilt instance with Fabric API available.

Do not add a `quilt.mod.json` unless the code is moved to Quilt Loader entrypoints. The current classes implement Fabric entrypoints, and Quilt can load them through Fabric compatibility.

## NeoForge

NeoForge is not supported by the current jar. A correct NeoForge build needs:

- a NeoForge Gradle/module setup
- `META-INF/neoforge.mods.toml`
- an `@Mod` entrypoint
- replacements for Fabric tick events, client render events, and networking
- either a shared common module or duplicated loader-specific platform glue

Adding only `neoforge.mods.toml` would be misleading because launchers would detect a NeoForge mod that cannot actually initialize.
