# Changelog

[**中文版**](CHANGELOG_ZH.md)

## Unreleased

### Changed

- Replaced the previous ForgeGradle project with NeoForged's Forge 1.20.1 ModDevGradle Legacy MDK structure.
- Ported the upstream NeoForge implementation into the standard `src/main` source set.
- Made AE2 a declared, required dependency and pinned the Forge AE2 artifact unambiguously.
- Replaced NeoForge payloads, registries, events, capabilities, item data components, and rendering calls with Forge 1.20.1 APIs.
- Replaced addon reflection and guessed compatibility with direct AE2 APIs.
- Updated build and release automation for the rewritten Forge artifact.

### Fixed

- Preserved all nine Network Locator filter positions, including empty gaps, in item NBT.
- Corrected the Minecraft 1.20.1 recipe directory and result schema.
- Added AE2's GuideME dependency to development and runtime smoke-test environments.
- Prevented infinite runtime highlighting from overflowing the remaining-tick packet field.
