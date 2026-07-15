# Forge 1.20.1 rough-port notes

This branch intentionally favors a buildable, loadable Forge 1.20.1 baseline over feature parity with the NeoForge 1.21.1 implementation.

## Included

- Forge 47.4.10 / Minecraft 1.20.1 / Java 17 build
- Client-side loaded-block-entity scanning
- Reflection-based AE2 Pattern Provider and busy-state detection
- Green/yellow/red highlight boxes based on continuous busy duration
- GitHub Actions build, artifact upload, mc-runtime-test smoke test, and GitHub Release workflow

## Deferred

- Network Locator item and screen
- Output item billboards
- Exact crafting CPU request matching
- ExtendedAE, AdvancedAE, Mekanism and EMI integrations
- Configuration UI

The original 1.21.1 source remains under `src/main` but is excluded from the Forge 1.20.1 source set.