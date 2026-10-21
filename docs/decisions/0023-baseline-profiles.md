# 0023: Baseline and startup profiles

Date: 2026-10-21 · Status: accepted

## Decision

The Android phone app ships a baseline profile and a startup profile, generated on an emulator and
committed, as Campfire does.

- **The journeys.** `:app:baselineprofile` (a `com.android.test` module with the androidx baseline
  profile plugin) runs three against the release build without R8 (`nonMinifiedRelease`): a cold
  start to the bike screen, a fresh install's first run (welcome, permissions, add bike), and the bike
  screen's component detail and Settings. An emulator has no bike, so they use Diagnostics → Load
  sample bike, the way the iOS UI test does. The cold start is also the startup profile.
- **Local, on a managed emulator.** `./gradlew :app:android:generateBaselineProfile` boots a Gradle
  managed device (Pixel 6, API 34, Google APIs) for the run, so it never touches a shared emulator or
  a phone with real bikes on it. There's no CI job yet: regenerate after changes to startup or to the
  screens the journeys visit. A stale rule costs nothing; a missing one is code left to the JIT.
- **Merged into main.** The profiles land in `app/android/src/main/generated/baselineProfiles/`, so
  release and the internal builds on Drop ship the same ones (internal's run falls back to
  release's). `dexLayoutOptimization` has R8 put the startup profile's classes first in the primary
  dex; the split into two dex files costs about 260 KB of APK (6.37 MB to 6.64 MB on internal). `androidx.profileinstaller` installs the profile where Play doesn't: Drop builds and sideloads.
  The two files are about 5 MB of text; `.gitattributes` marks them generated, so GitHub folds them
  in a pull request (Danger still calls one big).
- **The plugin's build types.** It adds `nonMinified` and `benchmark` build types for release and
  internal, signed with the debug key when their build type has none. They take the version code of
  the build type they extend, and the minified `benchmark` ones never upload a mapping to
  Crashlytics. They install under the id of the build type they extend, so they run on the managed
  emulator, not a phone with the POC or a Drop build on it.
- **Measuring.** `StartupBenchmark` times cold starts on `benchmarkRelease` without the profiles and
  with them. On the emulator the two are only comparable with each other.
- **The phone app only.** The Wear app can follow the same way if its startup needs it.

## Why

Cassette's code, Compose and Circuit run interpreted and JIT-compiled until ART compiles what it saw
used, in a background job while the phone is idle and charging, and that starts over with every
update. A baseline profile lets ART compile the startup path and the first screens at install.
People open Cassette for a few seconds to glance at their batteries, so the cold start is most of
the time they spend in it.

## Alternatives

- **A weekly CI job** (Campfire regenerates on emulator.wtf): emulator minutes and a token for
  profiles that change only when the journeys' screens do. Worth adding if regenerating by hand
  gets forgotten.
- **Only the libraries' profiles.** Compose ships its own, but they don't cover our screens,
  presenters or the Metro graph.
