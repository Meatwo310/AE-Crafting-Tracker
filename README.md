# AE Crafting Tracker — Forge 1.20.1

An unofficial Forge 1.20.1 port of Chatterjay's AE Crafting Tracker.

## Features

- Pattern Provider highlighting with active, stalled, and stuck states
- Up to three output-item icons above highlighted providers
- Best-effort matching against AE2 crafting requests
- Network Locator item with nine persistent ghost filters
- Sneak-right-click binding to an AE network
- Network-wide item, pattern, interface, bus, and storage scanning
- Distance labels and item icons for locator results
- Runtime highlight toggle in the locator screen and AE2 crafting-status screen
- Forge configuration screen and `/crafttracker` commands
- Reflection-based compatibility with AE2, ExtendedAE, AdvancedAE, Applied Mekanistics, and similar addons
- Optional operation without AE2, allowing lightweight runtime smoke tests

## Usage

1. Craft the Network Locator.
2. Sneak-right-click an AE network block to bind it.
3. Right-click in the air to open the filter screen.
4. Place ghost filter items in the 3×3 grid.
5. Use the Highlight button for runtime Pattern Provider tracking.

## Build

```bash
./gradlew build
```

Requires Java 17. The distributable jar is written to `build/libs/`.

## Compatibility notes

The port intentionally uses a reflection compatibility layer for AE2 and addon internals. Core startup and Minecraft runtime are tested in CI; exact behavior can vary with individual addon releases.

## License

GNU LGPL 3.0.
