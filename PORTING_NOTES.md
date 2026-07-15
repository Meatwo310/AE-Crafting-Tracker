# Forge 1.20.1 port notes

Version 0.1.0 restores the core 1.21.1 feature set on Forge 1.20.1.

The implementation avoids hard-linking to one AE2 build. Pattern Providers, crafting requests, grid nodes, parts, inventories, and addon hosts are discovered through a defensive reflection layer. Unsupported methods degrade to loaded-chunk scanning rather than preventing startup.

EMI-specific drag callbacks are not linked directly; the locator ghost slots support normal carried-stack placement and clearing. Addon inventories and patterns are still scanned at runtime.
