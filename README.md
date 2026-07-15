# AE Crafting Tracker — Forge 1.20.1 rough port

This branch is an unofficial, personal-use-oriented port of Chatterjay's AE Crafting Tracker from NeoForge 1.21.1 to Minecraft 1.20.1 with Forge.

## What works

- Loads on Minecraft 1.20.1 / Forge 47.4.10+
- Client-side scanning of loaded block entities near the player
- Reflection-based detection of AE2 Pattern Providers, including providers reachable through AE2 container objects
- Color-coded provider boxes:
  - Green: provider reports busy
  - Yellow: provider has remained busy for at least 5 seconds
  - Red: provider has remained busy for at least 15 seconds
- The mod can start without AE2, which keeps CI runtime smoke tests lightweight

## Deliberate limitations

This is a forced compatibility port, not a feature-complete backport. The original 1.21.1 implementation remains under `src/main` for reference but is excluded from the 1.20.1 build.

The following original features are not currently ported:

- Network Locator item and GUI
- Output-item billboards
- Exact AE2 crafting-request matching
- ExtendedAE / AdvancedAE / Mekanism-specific integrations
- User-facing configuration screen

Reflection is used so the project does not compile directly against one exact AE2 1.20.1 build. This improves startup compatibility but means future AE2 internals may require adjustments.

## Build

```bash
./gradlew build
```

The distributable jar is written to `build/libs/`. Java 17 is required.

## Automation

- `Build` compiles the mod, uploads the jar, and runs `headlesshq/mc-runtime-test` against Forge 1.20.1.
- `Release` builds and attaches jars to a GitHub Release when a `v*` tag is pushed or when manually dispatched with a tag name.
- Version bumping is intentionally manual.

## License

GNU LGPL 3.0. The original project and attribution remain under the same license.
