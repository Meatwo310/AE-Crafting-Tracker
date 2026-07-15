# AE Crafting Tracker — Forge 1.20.1 port

A forced backport of AE Crafting Tracker for Minecraft 1.20.1, Forge 47.4+, and Applied Energistics 2 15.x.

## Features

- Highlights active AE2 Pattern Providers in nearby loaded chunks.
- Uses green outlines while crafting and red outlines when a provider reports a locked state.
- Adds a Network Locator item that can be bound to a block position by sneak-right-clicking.
- Uses reflection for AE2 provider state access, avoiding direct coupling to 1.21-only internals and addon implementation classes.

## Compatibility notes

The original project targeted NeoForge 1.21.1 and directly integrated several addon internals. This 1.20.1 port deliberately uses Forge 47 APIs and a reduced, stable feature set. Addon-specific adapters, fluid/chemical icon rendering, multi-output billboards, the locator GUI, and EMI integration were removed to keep the forced backport buildable and testable.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.20 or newer in the 47.x line
- Applied Energistics 2 15.x

## Automation

- `Build`: compiles on pushes and pull requests, uploads the jar, and starts a headless Minecraft Forge client through `mc-runtime-test`.
- `Release`: creates a GitHub Release from a pushed `v*` tag or a manually supplied tag. Version bumping is intentionally not automated.

## License

GNU LGPL 3.0
