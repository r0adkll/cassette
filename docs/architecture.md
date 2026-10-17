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
:surfaces:wear                         Wear app, tile, complication (milestone 2)
```

## Status

Built so far: `gradle/build-logic` (plugins `cassette.root` (ktfmt), `cassette.multiplatform`,
`cassette.android.library`, `cassette.android.application`, `cassette.compose`, `cassette.di`, `cassette.ui`),
`:protocol` (AXS decoding, shared, fixture tests on JVM and Android), `:common:compose` (design system) and
`:app:android` (design gallery), `:core` (domain models, battery rules), `:data:db` (SQLDelight schema v1), `:common:screens` (Circuit
screens, Parcelize via `cassette.parcelize`) and `:features:bikes` (`api`/`impl`/`ui`: repository and bike
screen). `:app:android` holds the Metro graph (`AndroidAppGraph`) and Circuit navigation. `:poc` is the proof of concept, kept until its features are ported.

## Open

- Background capture: PendingIntent scan vs CompanionDeviceManager ([0009](decisions/0009-background-capture.md)).
