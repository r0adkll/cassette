# Architecture

([0007](decisions/0007-platform-and-architecture.md)) Kotlin Multiplatform with Compose Multiplatform,
Circuit + Metro, SQLDelight and DataStore Preferences, following Campfire's conventions and convention
plugins (an included `gradle/build-logic` build).

## Modules (proposed)

Split into `api`/`impl` only where there's a real seam.

```
:app                                   app shells, DI graph, Android entry points
:core                                  domain models, DI scopes, logging
:protocol                              AXS/Quarq decoding (pure Kotlin) + capture fixtures
:data:db                               SQLDelight schema, queries, migrations
:data:ble (api/impl)                   scanning, GATT reads, scan profiles
:features:bikes (api/impl/ui)          bike list, bike detail, add-bike
:features:components (ui)              component detail, history
:features:alerts (api/impl)            notification logic
:features:diagnostics (ui)             raw data, ride tests
:features:settings (ui)
:common:compose                        design system
:common:screens                        Circuit screen keys
:surfaces:widget                       Glance widgets
:surfaces:tile                         Quick Settings tile
:features:bikes:summary                bike status and rows as words and tones, shared with the watch
:surfaces:wear:sync                    Wear Data Layer contract (snapshot, refresh state)
:surfaces:wear:phone                   phone side of Wear sync
:app:wear                              Wear app, tile, complications (milestone 2)
```

## Status

Built so far: `gradle/build-logic` (plugins `cassette.multiplatform`,
`cassette.android.library`, `cassette.android.application`, `cassette.compose`, `cassette.di`, `cassette.ui`),
`:protocol` (AXS decoding, shared, fixture tests on JVM and Android), `:common:compose` (design system) and
`:app:android` (design gallery), `:core` (domain models, battery rules), `:data:db` (SQLDelight schema v1), `:common:screens` (Circuit
screens, Parcelize via `cassette.parcelize`; `DeepLinks`, the `cassette://bike/{id}[/component/{id}]` links that
notifications and widgets send to `MainActivity`) and `:features:bikes` (`api`/`impl`/`ui`: repository and bike
screen). `:app:android` holds the Metro graph (`AndroidAppGraph`) and Circuit navigation. `:data:ble` (scanner and
GATT reads; Android implementation ported from the POC) and `:features:capture` (`api`/`impl`: recorder,
battery refresher, capture controller) carry capture. `:features:settings` (`api`/`impl`/`ui`/`test`: DataStore Preferences settings and
the Settings screen) and `:features:diagnostics:ui` (capture health, live adverts, hub tables, ride tests, share). The capture log (`CaptureLog`, JSON lines)
lives in `:features:capture`; `:features:onboarding:ui` (first run: welcome, then one permissions screen; permissions go through
`PermissionController`, which `MainActivity` provides); `:features:alerts` (`api`/`impl`: decision logic in common code, Android notifier, snooze receiver
and daily reminder worker; schema v3 adds `bike_alert`); `:surfaces:widget` holds the Glance widgets and `:surfaces:tile` the Quick Settings tile (Android-only
modules); the Android `DiagnosticsSharer` and `PhoneBattery` live in `:app:android`. Background capture is the PendingIntent scan for
now; CompanionDeviceManager waits on the spike's device tests (#2). `:poc` is the proof of concept, kept until its features are ported.

Wear ([0013](decisions/0013-wear.md)): `:surfaces:wear:sync` is the Data Layer contract, and `:surfaces:wear:phone` publishes the
snapshot and runs refreshes a watch asks for (`SharedRefresh` in `:features:capture`, one refresh at a time
for the tile and the watch).

## Open

- Background capture: PendingIntent scan vs CompanionDeviceManager ([0009](decisions/0009-background-capture.md)).
