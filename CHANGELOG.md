# Changelog

All notable changes to Cassette are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Wear tile (decision 0013): the default bike's status icon, headline, weakest component and when it was
  seen, in our colours. Refresh asks the phone to scan and counts down on the tile; tapping the tile
  opens the app at that bike. It updates whenever the phone sends new data.
- Wear app (decision 0013): your bikes with their weakest status, then a bike's status block and a bar
  per component, worded and coloured like the phone. Refresh asks the phone to run its 60-second scan
  and counts down on the watch; it says so when the phone can't be reached or can't scan. Debug builds
  can load sample data without a phone.
- Wear sync (decision 0013): the phone sends its bikes, components and latest readings to a paired watch
  over the Wear Data Layer whenever they change, and runs a 60-second refresh when the watch asks
  (one at a time, shared with the Quick Settings tile). Diagnostics has a Wear panel (watches, last
  sync, last refresh asked), and the capture log records both.
- Kotlin Multiplatform scaffold (Android, iOS, JVM) with Cassette's convention plugins and ktfmt.
- `:protocol`: AXS advert and hub-table decoding as shared code.
- Design system: Cassette colours and status tones, Barlow and JetBrains Mono, Lucide icons, the status
  block, component rows, bars, buttons and the history chart, shown in a gallery app.
- Domain models (`:core`) and the SQLDelight database (`:data:db`): bikes, components, battery history
  with year-long retention and daily down-sampling, and alert state.
- `scripts/ktfmt`: the ktfmt CLI, the only formatter (CI and the pre-push hook), covering build-logic.
  Google style: 2-space indents.
- Onboarding: a welcome (with "Unofficial. Not affiliated with SRAM."), one explainer each for Nearby
  devices and notifications (allow, not now, or open settings once Android stops asking), then
  add-bike. Shown once; replaces the permission prompt at launch.
- Notifications: battery alerts when a component drops to low or charge now (once per change), a
  heads-up pre-ride nudge when the bike wakes with something already low (at most every 12 hours,
  "Snooze until tomorrow"), and not-seen reminders at 14 and 30 days, each with its switch in Settings.
  Diagnostics can simulate a low battery to test them.
- Quick Settings tile: the default bike's weakest status (icon and words, since Android draws the
  tile); tapping runs the 60-second refresh with a countdown, which keeps going after the shade closes.
- Home-screen widgets (Glance): a bike widget that adapts to its size (status block; a bar per
  component; thick bars with values inside) and an all-bikes widget, worded and coloured like the app,
  updated whenever capture writes and every 30 minutes.
- Capture log: background arms, wakes (with the raw adverts heard), GATT reads (with timing and the
  hub table's bytes) and wakes that ran out of time, kept as capped JSON lines. Ride tests mark
  sessions in it and summarise wake rate, gaps, read success and phone battery used (ported from the
  POC). "Share diagnostics" zips the log, a database snapshot and device info into the share sheet.
- Settings: default bike (pin one, or follow the most recently seen), background capture on/off,
  notification switches, diagnostics, licences. Stored with DataStore Preferences.
- Diagnostics: capture health (armed, last background wake, hub read), live adverts with raw bytes and
  frame filters, the hub's battery and firmware tables (decoded and raw), scan profile, and developer
  actions (load the sample bike, remove a bike).
- Component screen: the big value and status, voltage, shift count, firmware, model, serial and last
  read, and a 30-day / 90-day / 1-year history chart that marks charges and coin-cell replacements.
- Add a bike: wake it, Cassette finds the hub and reads its component list, then name and type it,
  pick a power meter heard nearby, and background capture starts (design iteration 5).
- Capture: the background scan (PendingIntent, re-armed after reboot or update) and the 60-second
  Refresh record known components, read the hub's battery table (every component, firmware, shift
  counts) and the power meter's Battery Level into the database. Components join from the hub table
  and are marked removed after 30 days unlisted. Ported from the POC into `:data:ble` and
  `:features:capture`.
- The bike screen: status block, component rows with reported or estimated values, and stale and unknown
  states, backed by the database (Circuit + Metro). A developer action loads the validation bike's
  readings until capture is ported.
- CI on GitHub Actions (formatting, tests, Android build, iOS compile, design-gallery renders), Danger
  checks and Renovate.
