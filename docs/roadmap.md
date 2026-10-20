# Roadmap

## M0: Design and spikes (done)

Design iterations (direction and every v1 surface approved, [0012](decisions/0012-visual-direction.md)) and
`docs/protocol.md`, the AXS protocol in our own words with its sources. The CompanionDeviceManager spike and
the longer ride tests carried over to the [loose ends](#loose-ends).

## M1: Android core (done)

KMP scaffold and convention plugins, design system, onboarding, add bike, bike and component screens,
history and charge detection, alerts and notifications, Glance widgets, Quick Settings tile, diagnostics
(including export), background capture, CI with ktfmt, Danger and Drop uploads.

## M2: Wear (done)

Wear app, tile and complications fed from the phone over the Data Layer
([0013](decisions/0013-wear.md)):

1. Sync: the phone publishes its bikes and runs refreshes the watch asks for; Diagnostics shows Wear.
2. The Wear app: bikes and bike screens.
3. The Wear tile.
4. Complications.
5. The Wear APK on Drop, as a Wear Build beside the phone's.
6. Updates on the watch, from Drop (iteration 9, A).

Checked on a real watch paired to a phone, including an update from Drop (2026-10-17). Later: automatic
updates on the charger (iteration 9, B).

## M2.5: Foldables and tablets (done)

Layouts for open foldables and tablets, before iOS so the iPhone foldable starts from them
([0014](decisions/0014-large-screens.md)):

1. Layouts from Material's window size classes in `:common:compose` (`ScreenLayout`), and screen
   renders at fold and tablet sizes beside the phone ones.
2. The bike screen as a dashboard, the bike rail on large windows.
3. The component sheet over the dashboard.
4. Settings by category, on every size, with the garage and App updates pages (iteration 13).
5. Tabletop refresh on a half-open fold.
6. Welcome, permissions, add bike and the empty bike screen split at the hinge.

Along the way: Settings → Appearance (System, Light or Dark). Checked on the Pixel 10 Pro Fold and a
tablet emulator (2026-10-18). Later: Settings opening beside the bike rail on large windows (the rail
would need to live outside the bike screen), keeping the open Settings category when the phone is
folded, and a "Raw data in diagnostics" link in the component sheet.

## M3: iOS (done)

An iPhone app sharing everything up to the screens ([0017](decisions/0017-ios.md)), from a Core
Bluetooth spike on the bike (2026-10-18; its findings are in 0017, and the spike is deleted):

1. The app shell: `:app:ios` and the Xcode project, the iOS graph, the shared screens on an iPhone.
   Built; runs on the Simulator, including iPads (the tablet rail, its sheet and Settings, by the
   iPad UI test).
2. Core Bluetooth: live scan and GATT reads, identifiers as addresses, the permission ask. Built,
   not yet run on an iPhone.
3. Background capture: the `FE51` scan with restoration, background hub reads (after the ride test).
   The scan and restoration are built; tuning waits for the ride test.
4. Notifications and the daily reminders. Built (`UNUserNotificationCenter`, a daily background app
   refresh), not yet run on an iPhone.
5. TestFlight from CI. The workflow is in (the `iOS app` job runs the UI test; `TestFlight` archives
   and uploads from main), waiting for the App Store Connect app and API key.
6. Liquid Glass buttons and actions, and the iOS-only screens: mockups, then the components.
7. WidgetKit widgets, then the Live Activity, then the Apple Watch app, each with its mockups first.
   Widgets are built (iteration 15): home screen, all bikes, Lock Screen and StandBy, rendered by
   the widget test; not yet seen on a home screen or an iPhone. The Live Activity is built
   (iteration 16): Lock Screen, Dynamic Island and StandBy, rendered by the widget test; not yet
   seen on an iPhone. The Apple Watch app is built (iteration 17): a page per bike, complications
   and the Smart Stack card, fed over WatchConnectivity; rendered by the watch render test, not
   yet run on a watch.

Checked without a device: the Simulator UI test (`scripts/ios-uitest`) and iOS screen renders. What still
needs an iPhone, a watch and the bike moved to M4's [on hardware](#on-hardware).

## M4: Store release

Cassette on Google Play (phone and Wear) and the App Store (iPhone and Apple Watch). Gathered from an audit
of the repo on 2026-10-19; nothing here is started. A decision record for the release (listings, signing,
versioning, what ships in Diagnostics) comes first, and 0008's "sideload only" changes with it.

### Decisions

- **Developer tools in store builds.** Diagnostics → Developer ("Load sample bike", "Demo refresh") shows in
  every build. Store review can flag demo content, but a reviewer without an AXS bike needs a way to see the
  app; the sample bike is that way today. Gate them, or keep them and explain them in the review notes.
  Diagnostics itself stays ([0005](decisions/0005-diagnostics.md)).
- **iPad.** The iOS app targets iPhone and iPad (XcodeGen's default; `TARGETED_DEVICE_FAMILY` isn't set).
  Keep it (iPad screenshots and an iPad review pass) or ship iPhone only at first.
- **Versioning.** Every app's version is `cassette.version` in gradle.properties (#56): Android release
  codes derive from it, Drop builds count commits (#57), iOS build numbers are the commit count. Still
  to pick: the first public version, whether releases are tagged, and cutting CHANGELOG's
  `[Unreleased]` into it.
- **Languages.** English only, with strings in Kotlin; fine for v1, but say so in both consoles.
- **Backups.** The phone app doesn't set `allowBackup` or `dataExtractionRules` (on by default), Wear sets
  `allowBackup="true"`. Decide what the database and settings do on a new phone.

### Both stores

- Listing text that says unofficial and not affiliated with SRAM in the description, not only in the app;
  SRAM and AXS in a listing can trigger impersonation or IP review. No SRAM marks in screenshots.
- Screenshots and listing art from the screen renders or devices: phone, tablet or iPad, Wear, Apple Watch.
- Privacy answers: no data collected, no tracking (no analytics, crash reporting or network client). The
  privacy policy and terms pages exist; add a support URL (App Store Connect requires one).
- Content rating questionnaires.
- Review notes: how to try the app without a bike, why it scans in the background.

### Google Play

- ~~The release build's label~~: "Cassette", with "(dev)" only on debug and internal builds (#56).
- Release signing: an upload key and Play App Signing, which signs the phone and Wear apps with one key,
  as the Data Layer needs ([0013](decisions/0013-wear.md)). Release builds take the upload key from
  the environment; `scripts/release-signing-wizard` makes it and sets the CI secrets. The key and
  secrets exist (2026-10-19; both bundles checked against its fingerprint). Still to do: create the
  listing in the Play Console.
- ~~Version codes~~: release's are `MMmmpp0F`, so the phone and Wear bundles never share one (#56, #57).
- AABs (`bundleRelease`) and a Play upload from CI (the Play/Fastlane tooling 0008 adopted), alongside the
  Drop job.
- Play Console: the app, the Wear form factor opt-in, internal testing track first.
- Rebuild the release manifests and check them: no `REQUEST_INSTALL_PACKAGES` or `INTERNET` (Drop's no-op),
  no debug-only components.

### App Store

- The App Store Connect app and API key, which turn on the TestFlight job; then a build installed from
  TestFlight.
- ~~Privacy manifests~~: one per target, no tracking and nothing collected; the app declares file
  timestamps (C617.1) and user defaults (CA92.1).
- iPhone landscape is allowed; check the shared screens in it (or lock to portrait).
- Review notes for `bluetooth-central` in the background and the time-sensitive notification entitlement.
- dSYMs for the Kotlin framework, so crashes from App Store builds can be read.

### On hardware

Before submitting, on an iPhone and an Apple Watch with the bike (deferred from M3, 2026-10-18):

- The Bluetooth prompt (the Simulator reports Bluetooth as allowed without asking), adding a bike, a
  refresh, live adverts and hub reads.
- Notifications posting, the snooze action and taps opening the bike; the reminders' refresh.
- The widgets on a home screen and the Lock Screen: their reloads after a background capture, the
  bike picker, taps, and the tinted, clear and StandBy looks.
- The Live Activity during a real refresh: the Dynamic Island drawn by the system, the ring's
  countdown, Stop and Add 60 s from the island, how long the scan keeps going with the phone locked,
  StandBy and its night mode.
- A ride test with the app (Diagnostics' ride tests and capture log, in place of the spike), the
  identifiers across days and a battery swap, and a hub read from a background discovery; then
  background capture's tuning in 0017.
- The Apple Watch app on a watch paired to the iPhone: the snapshot arriving, the complications on
  faces (tinted too) and in the Smart Stack, its rise when the bike wakes, and Refresh from the watch
  with the phone locked.
- The Play release build (R8, release key) on the Pixel and the watch, phone and Wear from Play's internal
  track.

## Loose ends

Not needed for M4; picked up when they fit.

- Background capture on Android: the CompanionDeviceManager spike
  ([0009](decisions/0009-background-capture.md), still proposed), the Batched 30 s scan profile and a ride
  longer than 30 minutes.
- Wear: automatic updates on the charger (M2, iteration 9 B).
- Large screens (M2.5): Settings beside the bike rail, keeping the open Settings category when the phone
  folds, and a "Raw data in diagnostics" link in the component sheet.
- iOS: the glass buttons' look during push and pop transitions, and on a physical iPhone (0017).
- Android-only wording (Drop, App updates) still lives in shared Settings code, hidden on iOS by a flag;
  move it behind `LocalAppPlatform` if it grows.
- `:poc` goes once its capture and diagnostics are ported.
