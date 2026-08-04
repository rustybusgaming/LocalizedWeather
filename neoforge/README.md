# NeoForge Port

This is an isolated NeoForge 1.21.11 workspace for Localized Weather. Build it with:

```powershell
..\gradlew.bat -p neoforge build
```

It currently validates the NeoForge toolchain, metadata, and loader entrypoint only. The Fabric server tick, payload networking, client weather blending, audio, rendering, and mixin integrations still require NeoForge-specific adapters before this becomes a release artifact.