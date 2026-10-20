# Cassette

Unofficial battery monitor for SRAM AXS bike components. Kotlin Multiplatform (Android first, iOS later),
Compose Multiplatform, Circuit + Metro, SQLDelight. Product, design and architecture decisions live in
`docs/` (start with `docs/product.md`, `docs/architecture.md` and `docs/decisions/`). Read the relevant
decision record before changing behaviour it covers; add a new record for new decisions.

The KMP scaffold is in place (`gradle/build-logic` convention plugins, `:protocol`, `:common:compose`,
`:app:android`). The proof of concept lives on as `:poc` (installs as `com.r0adkll.cassette`; the new app's
debug build is `com.r0adkll.cassette.debug`) so its capture and diagnostics keep working until features are
ported. Don't build new features on the POC.

## Build

- `./gradlew :app:android:assembleDebug`: the app. `assembleInternal` (here and in `:app:wear`) is the
  R8-optimised build CI uploads to Drop (same app id and key as debug); check R8 changes on a device with it.
- `./gradlew :common:compose:jvmTest`: renders the gallery to `common/compose/build/gallery/*.png` in every
  status state, light and dark. Feature UI modules render their screens to `build/screens/` the same way
  (e.g. `:features:bikes:ui:jvmTest`). Use them to check UI without a device; CI uploads both.
- iOS widgets (decision 0017): `IOS_SIMULATOR=<udid> scripts/ios-uitest` also runs the widget test,
  which renders every widget and state to `app/ios/Cassette/build/uitest-screens/w-*.png`. The app
  writes their snapshot to the App Group: `xcrun simctl get_app_container <udid>
  com.r0adkll.cassette.debug group.com.r0adkll.cassette.debug` shows `widgets.json`.
- iOS Live Activity: Diagnostics → Demo refresh runs a refresh the sample bike answers without
  Bluetooth, so a Simulator shows the real activity; the phone UI test ends with it (`a01`–`a05`:
  the island, expanded, the Lock Screen to Done). `ImageRenderer` can't draw timer views, so the
  `w-activity-*` renders draw the ring statically. Its starts and ends are in the capture log
  (`Library/Application Support/Diagnostics/capture-log.jsonl` in the app's data container, a folder
  kept out of backups).
- Apple Watch (decision 0017): `Watch/` (app) and `WatchWidgets/` (complications) share `Snapshot/`
  with the widgets (`Shared/` is the app's and the widgets', for the Live Activity). The iPhone app embeds the watch app, so every Xcode build of it needs Xcode's
  watchOS platform (`xcodebuild -downloadPlatform watchOS`). The `CassetteRenders` scheme runs only
  the widget and watch renders (`w-watch-*.png`, at a 46 mm watch's size) and doesn't need it.
  ImageRenderer draws no `ScrollView`, so screens it renders leave scrolling to their caller.
  End to end on Simulators: create a watch Simulator, `xcrun simctl pair <watch> <iphone>`, run
  `scripts/ios-uitest` on the iPhone (it ends on the sample bike), build the `CassetteWatch` scheme
  for the watch, `simctl install` and launch both apps; the phone's `widgets.json` then shows up
  in the watch's App Group (`get_app_container <watch> com.r0adkll.cassette.debug.watchkitapp
  group.com.r0adkll.cassette.debug`).
- Widgets: debug builds have `WidgetPreviewActivity`, which hosts every widget at fixed sizes without a
  launcher. Once: `adb shell appwidget grantbind --package com.r0adkll.cassette.debug`; then
  `adb shell am start -n com.r0adkll.cassette.debug/com.r0adkll.cassette.app.debug.WidgetPreviewActivity`.
  Glance caps a Row/Column at 10 children, and Responsive size mode only reports the size bucket.
- Tile: `adb shell cmd statusbar add-tile com.r0adkll.cassette.debug/com.r0adkll.cassette.surfaces.tile.BikeTileService`
  (then `click-tile`, and `remove-tile` when done); read its state from
  `adb shell dumpsys activity service com.android.systemui/.SystemUIService`.
- Wear (decision 0013): `./gradlew :app:wear:assembleDebug`, installed with adb on a watch or a Wear OS
  emulator. Drop's builds install from the Wear Build page (its adb command) and then update themselves; a
  local build has the debug key, so it can't take a Drop update (uninstall it first). It shares the phone app's id and key, which the Data Layer needs. Without a paired phone, the
  debug build loads sample data: `adb shell am broadcast -n
  com.r0adkll.cassette.debug/com.r0adkll.cassette.wear.debug.SampleDataReceiver --es state good` (or `low`,
  `critical`, `stale`, `one`, `empty`, `scanning`, `notready`, `clear`). The Wear tile: `adb shell am broadcast
  -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component
  com.r0adkll.cassette.debug/com.r0adkll.cassette.wear.tile.BikeTileService`, then `-a
  com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 0`.
- iOS (decision 0017): `scripts/xcodegen` (not a bare `xcodegen generate`: it sets the version from
  `cassette.version`), then build the `Cassette`
  scheme in Xcode or with `xcodebuild` (its build phase runs `:app:ios:embedAndSignAppleFrameworkForXcode`).
  Debug installs as `com.r0adkll.cassette.debug`. Bluetooth needs an iPhone; the Simulator shows the UI.
  `scripts/ios-run` does all of it on the Simulator (`--device` for a connected iPhone).
  `IOS_SIMULATOR=<udid> scripts/ios-uitest` runs the XCUITest walk-through (first run to the sample
  bike) from a fresh install and saves a screenshot per step to `app/ios/Cassette/build/uitest-screens/`.
  Simulators are shared between sessions: create your own (`xcrun simctl create`) and pass its UDID.
  An iPad Simulator runs the tablet walk-through instead (landscape, the bike rail). In tests, match
  buttons by their exact label and assert on something only the target screen shows: prefix matches
  fooled us ("Back" matched "background", "Front derailleur" matched Diagnostics text). Screenshots
  use `XCUIScreen.main` (the app's own capture is offset in landscape).
  `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` checks the Kotlin half (and its Metro graph).
  Each target has a `PrivacyInfo.xcprivacy`; after adding a dependency or a file, defaults or clock
  API, `scripts/ios-privacy-scan` lists the required-reason APIs each Release binary uses.
- Android on an emulator: `ANDROID_SERIAL=<serial> scripts/android-walk "Get started" "=Back" shot:bike`
  taps by text through adb (`=` for an exact match), scrolls and takes screenshots. Emulators are
  shared too: start your own on an unusual port (`emulator -avd <name> -port 5580`).
- Drive app UI only through XCUITest or adb, never by clicking or typing on the desktop: other apps
  (and other sessions' Simulators) are on the same screen.
- `scripts/ktfmt` checks formatting in under a second (`--format` fixes it), build-logic included. It is
  the only formatter: CI and the pre-push hook (`./gradlew bootstrap`) use it, and there is no Gradle
  ktfmt task. In the IDE, use the ktfmt plugin set to Google style.
- Icons: Lucide only, generated by `scripts/generate-lucide-icons.py` (add the name to `ICONS`, re-run).

## Conventions

- **Modules:** `api` modules depend only on `:core` or other `api` modules; `impl` modules contribute the
  bindings. Split into `api`/`impl` only where there's a real seam.
- **Circuit + Metro** (as in Campfire): presenters take the screen and `Navigator` as plain constructor
  parameters; use `fun interface` instead of function typealiases; Android entry points (receivers, tile
  service, widgets, `CompanionDeviceService`, Wear listener) reach the graph through component accessors.
- **Database:** every `.sq` change ships a `.sqm` migration and a new schema snapshot
  (`./gradlew :data:db:generateCommonMainCassetteDatabaseSchema`, run on its own: it trips Gradle's
  task-ordering check alongside other tasks); CI runs `verifySqlDelightMigration`.
- **Changelog:** behaviour changes get a CHANGELOG.md entry under `[Unreleased]` (Keep a Changelog).
- **Notifications:** keep a ledger of notification ids and channels in this file; give each PendingIntent
  its own request code (ledger below).
- **Formatting:** ktfmt, Google style (2-space indents). Run `scripts/ktfmt --format` before committing (run it
  twice if the check still fails; ktfmt occasionally needs a second pass).
- **Design system:** screens use `:common:compose` (`CassetteTheme`, components, `LucideIcons`); Material 3
  is the fallback layer only. Visual rules are in `docs/decisions/0012-visual-direction.md`.
- **Top bars float:** a screen's back, close, settings and add-bike buttons go in a `FloatingTopBar` of
  `TopBarButton`s laid over its scroll column; the column keeps `TopBarButtonSize` free at the top (text
  beside the buttons stays there and scrolls), and the bar pads by the system bars as the column does
  (decision 0017). `TopBarButton` is glass on iOS, the solid circle on Android.
- **iOS differences:** wording and rows that differ go through `LocalAppPlatform`; add an iOS render to
  the screen's `jvmTest` so iOS text is checked without a Simulator. Native UIKit views laid over Compose
  (`UIKitView`, `placedAsOverlay`) get no touches in CMP 1.12, so Compose owns the tap, and they ignore
  Compose's transitions, so only the active screen shows them (`LocalTopBarShown`).
- **Decision records:** number them from main's newest record just before opening the PR; parallel
  branches have taken the same number.
- **Third-party assets:** fonts (OFL) and Lucide (ISC) licences live in `thirdparty/licenses/`.
- **Commits** keep their `Co-Authored-By` attribution lines.

## Diagnostics stay

The protocol is reverse engineered and validated on one bike. Keep the diagnostic layers (raw advert and
GATT bytes, nearby SRAM-looking devices, hub table dumps, ride tests, scan profiles) in every build. When
polishing UI, move them into the Diagnostics area; never delete them. New features should log to the same
diagnostic surfaces.

## SRAM material

- No SRAM artwork, logos, fonts or code in the repo, even if extracted for reference.
- The app is unofficial and must say so; don't imitate SRAM's branding beyond using red as our hero colour.
- Protocol knowledge is documented in our own words with its source (captures, public projects).

## Notification ledger

Channels from decision 0011 (`AndroidNotifier` in `:features:alerts:impl`):

| Id | Channel | Purpose |
|---|---|---|
| 1,000,000 + component id | `battery_alerts` (default) | A component dropped to low or charge now |
| 2,000,000 + bike id | `pre_ride` (high, heads-up) | The bike woke and something is low; at most every 12 h |
| 3,000,000 + bike id | `reminders` (low) | Bike not seen for 14, then 30 days |
| (not yet) | `capture` (min) | Only while a foreground read runs, if one is ever needed |

On iOS (`IosNotifier`), identifiers are `battery-<component id>`, `pre-ride-<bike id>` (category
`pre-ride`, with the `snooze` action) and `not-seen-<bike id>`.

## PendingIntent request codes

| Code | Owner | Purpose |
|---|---|---|
| 100 | `data/ble` `ScanResultReceiver` | Background scan results |
| notification id | `AndroidNotifier` | Each notification's tap (opens the app) |
| 2,500,000 + bike id | `AndroidNotifier` → `SnoozeReceiver` | "Snooze until tomorrow" on the pre-ride nudge |
| 4,000,000 + bike id | `:app:wear` `BikeComplicationService` | A complication's tap (opens the bike on the watch) |
