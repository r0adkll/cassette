# Roadmap

## M0: Design and spikes

- ~~Design iterations~~: done, direction and every v1 surface approved
  ([0012](decisions/0012-visual-direction.md)).
- CompanionDeviceManager spike on the POC ([0009](decisions/0009-background-capture.md)).
- Ride tests: Batched 30 s profile, and one ride longer than 30 minutes.
- `docs/protocol.md`: the AXS protocol as we understand it, in our own words, with sources.

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

## M3: iOS

An iPhone app sharing everything up to the screens ([0016](decisions/0016-ios.md)), from a Core
Bluetooth spike on the bike (branch `spike/ios-corebluetooth`, 2026-10-18):

1. The app shell: `:app:ios` and the Xcode project, the iOS graph, the shared screens on an iPhone.
2. Core Bluetooth: live scan and GATT reads, identifiers as addresses, the permission ask.
3. Background capture: the `FE51` scan with restoration, background hub reads (after the ride test).
4. Notifications and the daily reminders.
5. TestFlight from CI.
6. Liquid Glass buttons and actions, and the iOS-only screens: mockups, then the components.
7. WidgetKit widgets, then the Live Activity, then the Apple Watch app, each with its mockups first.

Waiting on the bike: the spike's ride test, identifiers across days and a battery swap, and a hub read
from a background discovery.
