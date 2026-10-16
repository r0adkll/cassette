# 0007: Platform and architecture

Date: 2026-10-16 · Status: accepted

## Decision

- **Kotlin Multiplatform from the start**, following Campfire, with Compose Multiplatform.
- **Circuit + Metro** with Campfire's conventions: presenters take the screen and `Navigator` as constructor
  parameters; use `fun interface` instead of function typealiases; Android entry points (receivers, tile
  service, widgets, Wear listener) reach the graph through component accessors.
- **Storage:** SQLDelight for bikes, components, battery samples and alert state, with a `.sqm` migration
  for every `.sq` change, verified in CI. DataStore Preferences for settings.
- **Modules:** the layout in [architecture.md](../architecture.md). Split into `api`/`impl` only where there
  is a real seam.

- **Targets:** Android, iOS (arm64 + simulator) and JVM. Android ships first; iOS compiles in CI from day
  one and becomes an app in milestone 3; JVM is for fast tests only, with no desktop app.
- **Shared code:** everything up to the screens is shared (`commonMain`): protocol decoding, domain and
  battery logic, SQLDelight, repositories, Circuit presenters, the design system and screens. BLE sits
  behind a common interface with hand-written platform implementations (the POC's Android code first, Core
  Bluetooth later). Widgets, the Quick Settings tile, notifications and Wear are Android-only.
