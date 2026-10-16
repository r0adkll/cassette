# Roadmap

## M0: Design and spikes (now)

- Design iteration 1: bike overview, component detail and widgets in four directions, light and dark
  ([design-process.md](design-process.md)); narrow to one direction, then the remaining surfaces.
- CompanionDeviceManager spike on the POC ([0009](decisions/0009-background-capture.md)).
- Ride tests: Batched 30 s profile, and one ride longer than 30 minutes.
- `docs/protocol.md`: the AXS protocol as we understand it, in our own words, with sources.

## M1: Android core

KMP scaffold and convention plugins, design system, onboarding, add bike, bike and component screens,
history and charge detection, alerts and notifications, Glance widgets, Quick Settings tile, diagnostics
(including export), background capture, CI with ktfmt, Danger and Drop uploads.

## M2: Wear

Wear app, tile and complications fed from the phone over the Data Layer.

## M3: iOS

iOS app with Core Bluetooth, if still wanted.
