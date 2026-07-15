# AE Crafting Tracker — Forge 1.20.1 port

A forced backport of AE Crafting Tracker for Minecraft 1.20.1, Forge 47.4+, and Applied Energistics 2 15.x.

## Features

- Highlights busy AE2 pattern providers and changes outline color when a provider remains busy or locked.
- Adds a Network Locator item with nine ghost filter slots.
- Binds the locator to an AE2 network by sneak-right-clicking an AE2 block.
- Scans AE2 network-owned block entities for matching inventories and pattern outputs.
- Supports dragging EMI ingredients into locator filter slots.

## Compatibility notes

The original project targeted NeoForge 1.21.1 and directly integrated several addon internals. This 1.20.1 port deliberately uses only stable AE2 and Forge APIs. Addon-specific pattern-provider adapters, fluid/chemical icon rendering, and multi-output billboard icons were removed to keep the forced backport buildable and testable.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.20 or newer in the 47.x line
- Applied Energistics 2 15.x
- EMI is optional

## Automation

- `Build`: compiles on pushes and pull requests, uploads the jar, and starts a headless Minecraft Forge client through `mc-runtime-test`.
- `Release`: creates a GitHub Release from a pushed `v*` tag or a manually supplied tag. Version bumping is intentionally not automated.

## License

GNU LGPL 3.0
