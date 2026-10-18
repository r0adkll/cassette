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

## M2.5: Foldables and tablets

Layouts for open foldables and tablets, before iOS so the iPhone foldable starts from them
([0014](decisions/0014-large-screens.md)):

1. Window sizes in `:common:compose` (narrow, medium, expanded) and screen renders at fold and tablet
   sizes beside the phone ones.
2. The bike screen as a dashboard, the bike rail on expanded windows.
3. The component sheet over the dashboard.
4. Settings by category, on every size.
5. Tabletop refresh on a half-open fold.
6. Welcome and add bike split at the hinge.

Check on the Pixel 10 Pro Fold (its dp size first) and a tablet emulator.

## M3: iOS

iOS app with Core Bluetooth, if still wanted.
