# Changelog

All notable changes to Cassette are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Kotlin Multiplatform scaffold (Android, iOS, JVM) with Cassette's convention plugins and ktfmt.
- `:protocol`: AXS advert and hub-table decoding as shared code.
- Design system: Cassette colours and status tones, Barlow and JetBrains Mono, Lucide icons, the status
  block, component rows, bars, buttons and the history chart, shown in a gallery app.
- Domain models (`:core`) and the SQLDelight database (`:data:db`): bikes, components, battery history
  with year-long retention and daily down-sampling, and alert state.
- `scripts/ktfmt`: the ktfmt CLI for fast format checks (CI and the pre-push hook), covering build-logic.
- Capture: the background scan (PendingIntent, re-armed after reboot or update) and the 60-second
  Refresh record known components, read the hub's battery table (every component, firmware, shift
  counts) and the power meter's Battery Level into the database. Components join from the hub table
  and are marked removed after 30 days unlisted. Ported from the POC into `:data:ble` and
  `:features:capture`.
- The bike screen: status block, component rows with reported or estimated values, and stale and unknown
  states, backed by the database (Circuit + Metro). A developer action loads the validation bike's
  readings until capture is ported.
- CI on GitHub Actions (formatting, tests, Android build, iOS compile, design-gallery renders), Danger
  checks and Renovate.
