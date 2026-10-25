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
- Release: `scripts/release` (decision 0019) cuts `cassette.version` from main: baseline profiles,
  CHANGELOG's `[Unreleased]` rolled into the version, each store's notes (Claude drafts them in the
  background; `--no-claude` to write them yourself), the GitHub release and its tag, then the next
  version. `--dry-run` changes nothing, `--skip-baseline` keeps the committed profiles, and
  `scripts/release bump` only moves the version on. Gradle's output goes to `build/release/*.log`.
- The Release workflow (started by the GitHub release, or by hand) builds the Play bundles with the
  upload key (`scripts/release-signing-wizard`). Locally: `set -a; source ~/.cassette/release.env; set +a`, then
  `./gradlew :app:android:bundleRelease :app:wear:bundleRelease`; `scripts/check-release-manifests`
  fails on network or install permissions and debug components in the release manifests.
- App Store: `scripts/app-store-wizard` walks the first submission in 16 stages (the App Store Connect
  app, the API key and CI's TestFlight secrets, internal testing, the fastlane listing and privacy
  answers, `scripts/release`, App Review); `scripts/app-store-wizard <stage>` resumes. Its values and
  the key live in `~/.cassette/` (`app-store.env`, the `.p8`, fastlane's `app-store-connect.json`).
  CI's API key signs only bundle IDs that are registered already: a new target's ID is registered
  from a Mac first (`xcodebuild -allowProvisioningUpdates` with Xcode's account and no key, as stage
  3 does), or the TestFlight job fails with "Authentication failed".
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
  A complication's settings (decision 0027) open without a face: `adb shell am start -n
  com.r0adkll.cassette.debug/com.r0adkll.cassette.wear.complication.ComplicationConfigActivity --ei
  android.support.wearable.complications.EXTRA_CONFIG_COMPLICATION_ID 42`; the pick lands in
  `shared_prefs/complications.xml` (`run-as`). The emulator's default face has no complication slots on.
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
  CI's iOS app job builds with Xcode 26: Swift calls to APIs newer than its SDK (the iPhone Duo's,
  iOS 27.1) go behind `#if compiler(>=6.4)` as well as `#available`, or it fails to compile. The
  TestFlight job, whose builds ship, uses Xcode 27.1 on GitHub's `xcode-27` image (decision 0019).
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
- Baseline and startup profiles (decision 0023): `./gradlew :app:android:generateBaselineProfile` runs
  `:app:baselineprofile`'s journeys (the sample bike, from Diagnostics) on its own Gradle managed
  emulator and rewrites `app/android/src/main/generated/baselineProfiles/`; commit both files. The
  journeys find text and content descriptions, so renaming one they tap breaks them. `./gradlew
  :app:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest` compares cold starts with and without.
- Crash reports (decision 0020): Firebase configs aren't committed (`scripts/firebase-wizard`
  writes `app/android/google-services.json` and `app/ios/Cassette/Firebase/GoogleService-Info.plist`).
  Without them everything builds and nothing reports; debug builds never report either way.
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
  An entry for one platform's apps starts with `[Android]` (phone and Wear) or `[iOS]` (iPhone, iPad
  and Apple Watch); untagged entries apply to both. `scripts/release` builds each store's notes from them.
- **Notifications:** keep a ledger of notification ids and channels in this file; give each PendingIntent
  its own request code (ledger below).
- **Formatting:** ktfmt, Google style (2-space indents). Run `scripts/ktfmt --format` before committing (run it
  twice if the check still fails; ktfmt occasionally needs a second pass).
- **Design system:** screens use `:common:compose` (`CassetteTheme`, components, `LucideIcons`); Material 3
  is the fallback layer only. Visual rules are in `docs/decisions/0012-visual-direction.md`.
- **Top bars float:** a screen's back, close, settings and add-bike buttons go in a `FloatingTopBar` of
  `TopBarButton`s laid over its scroll column; the column keeps `TopBarButtonSize` free at the top (text
  beside the buttons stays there and scrolls), and the bar pads by the system bars as the column does
  (decision 0017). `TopBarButton` is glass on iOS, the solid circle on Android. Screens don't call
  it directly: back and close go through `TopBarNavigation`, and actions (settings, add bike)
  through `TopBarActions`, which put them in the iPhone Duo's vertical bar there (Swift's
  `VerticalBarHost`) and float them as `TopBarButton`s everywhere else. A column whose bar holds
  only back or close keeps its room with `TopBarSpacer`, which gives it up on the Duo.
- **iOS differences:** wording and rows that differ go through `LocalAppPlatform`; add an iOS render to
  the screen's `jvmTest` so iOS text is checked without a Simulator. Native UIKit views laid over Compose
  (`UIKitView`, `placedAsOverlay`) get no touches in CMP 1.12, so Compose owns the tap, and they ignore
  Compose's transitions, so only the active screen shows them (`LocalTopBarShown`).
- **Decision records:** number them from main's newest record just before opening the PR; parallel
  branches have taken the same number.
- **Third-party assets:** fonts (OFL) and Lucide (ISC) licences live in `thirdparty/licenses/`; the
  Licences screen shows them from there (`BUNDLED` in `LicenceCatalog.kt`: add an entry with the file).
  Library licences need nothing: the AboutLibraries plugin collects them at build time for each app.
  A dependency that spans several Maven groups, or whose POM name reads badly, gets a `ProjectRule`.
- **Tests run twice:** `commonTest` runs as `jvmTest` and as `testAndroidHostTest`, where android.jar
  is stubs that return defaults (`isReturnDefaultValues`). A library whose Android code calls the
  platform (AboutLibraries parses with `org.json`) silently gets nulls there: add the real artifact to
  that module's `androidHostTest` dependencies. Run CI's command (`./gradlew jvmTest
  testAndroidHostTest`), not just `jvmTest`, before pushing.
- **Gradle wiring:** a generated directory handed to another task (Compose resources' `customDirectory`)
  must come from the producing task (`taskProvider.map { … }`); `flatMap` onto a plain build-dir
  provider drops the dependency, and it only works while someone runs the task by hand.
- **Commits** keep their `Co-Authored-By` attribution lines.

## Every screen, every size

Decisions 0014, 0016, 0021, 0022, 0024 and 0025. A screen isn't done until it works as a phone column,
a phone on its side, an open foldable and a tablet; "it caps at phone width and centres" is not support.

- **Layouts follow the window, not the device:** read `LocalScreenLayout` (`Column`, `Landscape`,
  `Dashboard`, `DashboardWithRail`), which comes from the window's size class: Material's on Android,
  and on iOS a set of its own (`SizeClasses.Apple`: large from 1100 pt, so every iPad on its side
  gets the rail; 0025). Size-class breakpoints live in `SizeClasses` only; never branch on our own
  dp figures, the device or the orientation. Split screen (compact both ways) is a `Column`,
  and so is a short window of medium width, too narrow to split (`isShortMediumWindow()`: a small
  phone or the closed iPhone Duo on its side), where the bike screen takes the dashboard (0024).
- **Wider than a phone, split in two** with `SplitPanes`: what the screen says and its one action on the
  left, what it lists on the right; each pane scrolls on its own. It splits at an open fold's hinge, so
  nothing sits on the fold, and gets a divider on a phone on its side. Pane content keeps the phone's
  width (480 dp) against the split, so a tablet reads as one page. Titled screens in Settings use
  `TitledScreen` / `TitledLazyScreen` on a phone and `TitledPanes` wider; follow them elsewhere. A
  screen that can show in a list's detail pane (Diagnostics in Settings') follows `LocalInDetailPane`:
  `DetailPaneWidth` from the pane's start, `DetailPaneGutter`, `DetailPaneTitle`.
- **Insets per pane:** each pane pads only by its own side's bars (`startPane()` / `endPane()`, which
  `SplitPanes` applies). Modifier insets (`safeDrawingPadding`) respect that; `WindowInsets…asPaddingValues()`
  doesn't, so a `LazyColumn` in a pane pads its sides with `windowInsetsPadding` and only its top and
  bottom with `contentPadding`.
- **Short windows:** a phone on its side is about 412 dp tall. A fixed footer squeezes the middle to a
  sliver: put the action at the foot of the left pane, and in split screen let footers scroll
  (`FooterColumn`); `ScrollColumn` keeps a centred or footed layout at least its pane's height.
- **Long lists are lazy:** a list that can run past a screen or two (libraries, history, tables) is a
  `LazyColumn`; `PanelItem` keeps one panel's look across its items.
- **Check every size before calling UI done:** the screen's `jvmTest` renders each `RenderSize`
  (phone, `Fold` 852 × 883, `Tablet` 1280 × 800, `PhoneLandscape` 892 × 412, and `PassportLandscape`
  678 × 466 and `IpadLandscape` 1180 × 820, rendered with `SizeClasses.Apple`, where the module has
  them), light and dark; look at them. On an emulator, rotate with `adb shell settings put system accelerometer_rotation 0` and
  `adb shell settings put system user_rotation 1` (`0` to go back).
- **New layouts are designed first** (decision 0006): mockups at those sizes with real data, approved,
  then built. Applying an existing pattern (the split above) to a new screen needs no new round.

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
