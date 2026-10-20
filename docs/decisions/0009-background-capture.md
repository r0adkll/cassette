# 0009: Background capture

Date: 2026-10-16 · Status: proposed

## Decision (pending the spike)

Target design: **CompanionDeviceManager presence is primary, the PendingIntent scan is a narrow fallback.**

- Each bike's hub (rear derailleur) is associated, without pairing, during add-bike.
- On "appeared": while bound (or in a short `connectedDevice` foreground service), read the hub tables and
  scan in-process for the shifters, front derailleur and power meter; stop on "disappeared" plus a grace period.
- The PendingIntent scan only runs for bikes whose association isn't working, without the power meter filter.
- No always-on foreground service.

A spike on the POC (`spike/cdm` branch) runs now, alongside the design mockups, and confirms or overturns this.

## Evidence

- Two 13–15 minute pocket rides: no arming errors, longest gap between wakes 46 s, background hub reads fit
  the receiver budget (worst 6.3 s of 8.5 s).
- The power meter causes about 70% of wakes. Batched and first-seen profiles are untested.

## CompanionDeviceManager research (2026-10-16)

- **Association** uses a `BluetoothLeDeviceFilter` (e.g. `0xFE51` / name `^SRAM \d+$`). No bonding needed;
  one system dialog per device; one app can hold several associations (bikes, power meter).
- **Presence** (API 36+: `ObservingDevicePresenceRequest`, `CompanionDeviceService.onDevicePresenceEvent`)
  is a system scan filtered by the associated MAC address (`FIRST_MATCH | MATCH_LOST`, low power).
  "Appeared" = first advert; "disappeared" = the controller's match-lost timeout. No payloads are delivered.
- **What the app gets:** the system binds our `CompanionDeviceService` on appear (process doesn't need to be
  running, almost-perceptible priority) and unbinds about 5 s after disappear. With
  `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` the app may start a `connectedDevice`
  foreground service from the background.
- **Risks:** no support for rotating/random addresses unless bonded with a resolvable address; whether the
  hub's address stays stable across days and battery swaps is unverified. Short stops toggle presence
  (the hub sleeps about 5 minutes after motion stops). One Samsung report of companion background bugs.
- **Potential win:** about 1,000 wakes/hour on a ride drops to roughly two events per device per ride.

Proposed shape: associate each bike's hub; on appear, do the GATT reads and an in-process scan for
shifters, front derailleur and power meter while bound (or in a short `connectedDevice` service); keep the
PendingIntent scan as a fallback for unassociated devices or unstable addresses, without the power meter
filter.

A spike on the Pixel should test: unbonded association with the `0xFE51` filter; whether the associated
address matches the scanned one and survives sleep, a battery swap and the next day; appear/disappear
latency with the screen off; GATT and the foreground service from a cold process; Bluetooth toggles and
reboot (`adb shell cmd companiondevice simulate-device-event` helps iterate).

## Scan profiles (2026-10-19)

Two remain: **Low power** (the default; both validation rides used it) and **Batched 30 s** (the
controller holds results and wakes the app at most every 30 s, which may cut the ~1,000 wakes an hour
the power meter drives). Balanced only cost more battery, and First + lost overlapped the
CompanionDeviceManager spike; a stored choice of either reads as Low power. There's no profile
setting: a ride test can try Batched 30 s for one ride and puts Low power back when it stops
([0018](0018-ride-tests.md)). If Batched 30 s keeps up on a few rides, it becomes the default.

CompanionDeviceManager stays on the `spike/cdm` proof of concept (issue #2) and isn't ported.

## Open

- CompanionDeviceManager spike results.
- Batched 30 s on a ride, and a ride longer than 30 minutes.
