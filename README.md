# AE Crafting Tracker — Forge 1.20.1

[**中文版**](README_ZH.md)

An unofficial Minecraft Forge 1.20.1 port of Chatterjay's AE Crafting Tracker.
The Forge implementation is maintained in this repository; the original NeoForge
implementation is available from the [upstream project](https://github.com/Chatterjay/AE-Crafting-Tracker).

## Features

### Pattern Provider highlighting

- Tracks AE2 Pattern Providers near players who enable highlighting.
- Uses configurable green, yellow, and red overlays for active, stalled, and stuck states.
- Shows up to three matching item or fluid output icons above each provider.
- Can be toggled with `/crafttracker`, from the Network Locator screen, or from AE2's crafting-status screen.

### Network Locator

- Stores nine persistent ghost filters on the item.
- Binds to an AE2 network when sneak-used on an in-world AE grid host.
- Scans the bound grid's block inventories, Pattern Providers, storage buses, import/export buses, interfaces, and level emitters.
- Highlights matching positions while the player remains in the bound dimension.
- Supports manual ghost-slot interaction and optional EMI drag-and-drop.

## Requirements

- Minecraft 1.20.1
- Minecraft Forge 47.4.21 or newer
- Applied Energistics 2 15.4.8 or newer in the 15.x series
- GuideME 20.1.x (required by current AE2 15.4.x releases)
- EMI 1.1.24 or newer (optional, client-side)

## Build

This project is based on NeoForged's Forge 1.20.1 ModDevGradle Legacy MDK and requires Java 17.

```bash
./gradlew build
```

The distributable jar is written to `build/libs/`.

## Configuration

Forge creates `config/crafting_tracker-common.toml`. It controls the default highlight state, scan interval and radius, status thresholds, colors, and opacity.

## Compatibility policy

AE2 is a required, compile-time dependency. This port intentionally has no reflection-based compatibility layer and no optional startup mode without AE2. Addon-specific integrations should be implemented against stable, declared APIs and dependencies rather than guessed at runtime.

## License

GNU LGPL 3.0.

### Asset credit

The Network Locator texture is based on AE2's `network_tool` texture, copyright © Applied Energistics 2.
