# Changelog

All notable changes to Cassette are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Diagnostics → Developer → Add a random bike adds a made-up bike beside the sample, with random
  parts and levels (some low), for trying the app with several bikes.
- With two bikes or more, a phone switches bikes from a pill at the top of the bike screen: how many
  bikes there are and how many others need charging ("3 BIKES · 1 LOW"). It opens a sheet listing
  every bike with its status and when it was seen, and why one needs charging. It replaces the
  "1 OF 2" beside the bike's name, which couldn't be tapped.
- [iOS] On iPhone Duo, the bikes are the vertical bar's first button, with a badge counting those
  that need charging.
- The bike widget can prefer a component when it's small: it shows that one while everything's
  fine, and the one to charge once something is low. On Android it's the widget's settings' second
  question, after the bike; on iPhone, "Component" in its edit menu.
- [Android] Putting Cassette's complication on a Wear OS watch face asks which bike and which
  component it shows. A picked component always shows itself, in its own colour; "Weakest" works as
  before and is what a face's own default slot gets.
- [iOS] The Apple Watch's "Weakest battery" complication is now "Battery", and the face editor lists
  each bike's components beside the weakest. A picked component always shows itself, in its own
  colour.
- [Android] Open in App on a phone Build's Drop page opens Cassette's Drop build and offers that
  build in the update sheet, from whichever channel (Drop 0.6.0). Installing a pull request's build
  moves the phone to that pull request's builds until it closes.
- A bike can have more than one AXS system, each with its own hub, as on a mountain bike whose
  dropper post and rear shock each head one. Adding a bike listens a few seconds more after the
  first hub and lists every system that answers, each with a switch to leave out one that's another
  bike's. Each hub is read on its own.
- "Add a system" in a bike's settings finds a system the bike is missing, such as one that didn't
  wake while the bike was added, without adding the bike again.
- A bike's settings list its components under their systems, and a bike with two can remove one.
- Shared diagnostics say what adding a bike or a system heard: each hub with its signal and table,
  whether its system started on or off, and when the rider continued. They also list every device
  the background scan heard and which bike and system it's on, or that it's on none; say why a
  Bluetooth read failed; and say when each scan started and how it ended.

### Changed

- [Android] The widget's settings split in two on a phone on its side, an open foldable and a
  tablet, as the app's screens do.

- On an open foldable or a tablet held upright, the bike chips stay in the top bar with Add bike and
  Settings when the bike screen scrolls.
- [iOS] The bike chips are Liquid Glass, the selected bike's smoked.
- [iOS] On iPhone Duo, every screen's top-bar buttons are in the vertical bar under the status bar,
  as iOS places them there: Back or Close at the top, then actions such as Add bike and Settings.
  The component sheet's Close, Previous and Next go there too.
- [iOS] On iPhone Duo standing half open, the bike screen splits at the fold as on a half-open
  Android foldable: the readings above, Refresh below.
- A short window too narrow to split in two (a small phone on its side, or a passport-shaped
  foldable's outer screen on its side, such as iPhone Duo closed) keeps the phone's screens, with
  room for their labels again; the bike screen shows the dashboard, with Refresh beside the status
  and the components as tiles.
- [iOS] Every iPad held sideways lists the bikes on the left, not only the 13-inch.
- On an open foldable or a tablet, every Add bike step splits in two as waking the bike does, instead
  of a phone's column for naming it, the power meter and keeping it fresh.
- An AXS controller is called that, not a Reverb controller: it can run more than the dropper post.
- A battery pack's estimated percentage follows SRAM's down to empty: 10 % at 7.37 V and 0 % at
  7.36 V, where it said about 8 %.

### Fixed

- On an open foldable or a tablet, Diagnostics in Settings lines up with the other categories.

- [iOS] On an open foldable or an iPad held upright, the bike screen's Add bike and Settings buttons
  no longer show over an open component.
- A battery pack estimated at 0 % shows as Charge now instead of unknown.
- Adding a bike names the component that's its hub, rather than always calling it the rear
  derailleur.
- Diagnostics → Hub tables reads every hub of the bike.
- A component's model no longer changes to a number that isn't one when it sends a frame without
  its device type.

## [1.0.0] - 2026-10-21

### Added

- `scripts/release` cuts a release from main, as Campfire's does (decision 0019): the baseline
  profiles, this file's `[Unreleased]` rolled into the version, the Play and App Store notes (drafted
  by Claude Code from the entries for each platform), a GitHub release tagged with the version, which
  now starts the Release workflow, and then the next version. Entries for one platform start with
  `[Android]` or `[iOS]`.
- Crash reports (decision 0020): release and internal builds on Android, and Release builds on
  iOS, send crashes to Firebase Crashlytics, with Kotlin stacks on iOS through CrashKiOS. Settings →
  About → Privacy → Send crash reports turns them off. `scripts/firebase-wizard` sets up Firebase.
- Settings → About → Open-source licences lists every library the app ships, one row per project
  (AndroidX, Compose Multiplatform, Circuit…) that opens its libraries and licences, plus the bundled
  fonts and icons and each licence's full text. The list is collected at build time for each app
  (AboutLibraries), so the Android and iPhone apps each show their own. On a phone on its side, an
  open foldable or a tablet, each of these screens splits in two (decision 0022).
- An iPhone app (decision 0017): the shared screens on iOS, Core Bluetooth scanning and
  unpaired GATT reads (a peripheral's identifier stands in for its Bluetooth address), the background
  scan with state restoration, and the three notifications with "Snooze until tomorrow". On iOS,
  Settings → Capture says how iOS lets Cassette listen and when the bike was last heard, and a
  permission turned off points to its place in the Settings app.
- iPhone widgets (decision 0017): a bike's status block, its components as bars or thick bars, and
  every bike at once on the home screen; the bike's status and bars on the Lock Screen, and in
  StandBy. Each widget picks its bike, and taps open the bike or the component.
- A Live Activity on the iPhone while a refresh runs (decision 0017): on the Lock Screen, in the
  Dynamic Island and in StandBy, the scan counts down in a red ring and each component fills in as it
  answers, then the result stays for five minutes. The expanded island has Stop and Add 60 s; a tap
  opens the bike. Its starts and ends go into the capture log.
- Diagnostics → Demo refresh: the sample bike answers a refresh without Bluetooth, to see the Live
  Activity (or the Quick Settings tile refreshing) without a bike.
- The sample bike comes with a month of history (its derailleurs charged 12 days ago), so its charts
  and estimates show, and its components show their serials.
- Store listings for Google Play and the App Store under `fastlane/`, with `scripts/store-assets` to
  rebuild their images.
- An Apple Watch app (decision 0017): a page per bike in its status colour, paged with the crown, the
  components as thick bars, and Refresh, which asks the iPhone to listen and counts down on the
  watch. Complications for the weakest battery (circular and corner) and the bike's status
  (circular, rectangular and inline), and a Smart Stack card that rises when the bike wakes with
  something low. On iOS, Diagnostics shows the Apple Watch: whether one is paired and has Cassette,
  the last update sent and the last refresh it asked for.
- Settings → Notifications asks again, or opens the system settings, while notifications are off.
- Settings → About: the installed version, who made Cassette and its source code, Report a bug (a
  GitHub issue with the version filled in), the privacy policy and terms of service, and the
  open-source licences, which move here from their own category.
- Back, close, settings and add-bike buttons float over the screen, so scrolling no longer takes them
  off screen. On iOS they're Liquid Glass (a frosted material before iOS 26).
- Settings → Appearance: System, Light or Dark, each shown as a small picture of the bike screen.
  The app and its system bars follow the choice; widgets, the Quick Settings tile and the watch keep
  following the phone.
- Welcome and add bike unfolded (decision 0014): on an open foldable the welcome's red poster takes
  the half before the hinge (wordmark, tape mark, tagline) and the three points and Get started the
  other; adding a bike shows waking and found on one screen, the listening pulse on
  the left and the components arriving on the right. The permissions screen splits the same way: the
  ask on one half, the two permissions and Continue on the other. Tablets split down the middle.
- Tabletop refresh (decision 0014): on a foldable standing half open, the bike screen splits at the
  fold. Above, the status and a tall column per component filled to its level; during a scan the
  ones not heard yet are grey and each rises into colour as it answers. Below, a circle that is the
  Refresh button, then the scan's countdown ring with pulses (Stop, Add 60 s), then Done with how
  long it took, or how many answered.
- The bike screen on open foldables and tablets (decision 0014): the status block with Refresh inside
  it, then a tile per component with its 30-day trend, the one to charge next on a wide tile. Tablets
  in landscape list the bikes on the left, with Add bike and Settings; an open foldable (and a tablet
  in portrait) puts them in the top bar as chips. Either switches bikes in place. Phones keep the
  column.
- On those screens a component opens in a sheet that slides over the dashboard, with close, previous
  and next. On an open foldable it covers about three quarters of the screen and tapping the dimmed
  edge closes it; on a tablet it is narrower and tapping another tile switches it. Back closes it, and
  folding the phone with it open carries the component over as its own screen.
- Playful status titles (decision 0015): the bike screen's status title is sometimes a joke ("No
  single-speed today.", "Full tape. Press play."), picked again each time the app opens. The colour, icon
  and sentence don't change, screen readers hear the plain title, and widgets, the tile, notifications and
  the watch stay plain. Settings → Bikes → Playful titles turns them off. Long titles break after their
  inner punctuation ("Full tape." / "Press play.").
- Watch app updates from Drop: an "Update ready" row under the bikes, an update screen with Install and a
  progress ring, "Updated to build N." after, and a one-time step to allow updates that shows where the
  switch is (App info, Advanced, Install unknown apps). With Drop 0.5.0 the update installs without
  asking to confirm.
- Wear complications (decision 0013): the default bike's weakest component as a ranged value ("78",
  "RD") and its status as short text ("GOOD", "FD LOW", "CHARGE"), each with the status icon so they
  still read when the watch face recolours them. Tapping opens the bike; they update when the phone
  sends data, and hourly.
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

### Changed

- The Android app starts faster (decision 0023): it ships a baseline profile, so Android compiles
  startup, the first run and the bike screen ahead of time, and a startup profile, which puts the
  classes the first frame needs first in the app's code. Drop builds install the profile themselves.
- Android release version codes come from the version name (`cassette.version`), with the form factor
  as the last digit, so the phone and Wear apps never share one. Drop builds keep counting commits,
  now with the build type and form factor as the last two digits. Builds installed from Drop between
  the two schemes (codes 10010 and 10011) need one reinstall.
- Settings switches animate as each platform's do: Material 3's on Android, and on iOS one sized
  and moving like the system's, whose thumb slides on a spring and stretches while pressed. A press
  anywhere on the row shows on the switch.
- Settings lists App updates after Diagnostics, just above About.
- About's Privacy policy row has no subtitle, like Terms of service.
- About's Made by names The Scavenger's Software only.
- Settings' Bikes category has a bike icon instead of the pin.
- Ride tests are a guided flow (decision 0018): choose what to test, check what's ready, ride, then a
  verdict in plain words (Kept up, Patchy or Lost the bike), the wakes across the ride, each component
  by name, reads and phone battery, with the raw numbers under Details. Ride tests compares Low power
  and Batched 30 s. A test that tries Batched 30 s puts Low power back when it stops.
- Android's background scan has two profiles, Low power and Batched 30 s (decision 0009); Balanced and
  First + lost are gone, and a phone set to either goes back to Low power. The scan profile chips left
  Diagnostics: a ride test chooses the profile for its ride.
- Backups (decision 0019): your bikes, their history and your settings go to a new phone with Android's
  backup and device transfer, as they already did with iCloud. The capture log stays on the phone
  (on iOS it moves to a `Diagnostics` folder kept out of backups), and the Wear app isn't backed up.
- Every screen draws edge to edge (decision 0016): backgrounds, panes, the tablet rail and dividers run
  under the status and navigation bars, and content scrolls under both.
- Settings are grouped by category (decision 0014): Bikes, Capture, Notifications, App updates,
  Diagnostics and Licences, each with a line on where it stands ("2 of 3 on", a red dot for a waiting
  build). On a phone each category opens its own screen; on an open foldable or a tablet the list sits
  beside the open category, and Diagnostics' tools open in that pane.
- Settings' Bikes page is a garage: a card per bike with its components and status, an "Open the last
  bike seen" switch, and the bike that opens first marked. Each bike has its own page to rename it,
  change its type, make it open first, see its components and remove it; on an open foldable or a
  tablet the bike's details sit on the left and its components on the right. Remove bike is red with
  a trash can; after the first tap (a light tick) it fills red and the can shakes until the second
  tap (a firmer buzz), or resets after 5 seconds.
- Settings' App updates page says what's waiting: the build, what's new, its size and when it was
  uploaded, the installed build, and one action (Install, or Reinstall with a warning that it clears
  the bikes). Up to date and couldn't-check states say when it last checked.
- The welcome screen's tagline, folded and unfolded, is now "Never ride single-speed by accident."
  (was "Know before you ride.").
- The app icon (phone and watch, release and dev) drops its four small screw dots.
- The watch app's internal build goes to Drop as a Wear Build in the same run as the phone's (Drop
  0.4.0), with the phone's commit-count version code, so it installs from its Build page instead of a
  CI artifact.
- Builds uploaded to Drop are the R8-optimised `internal` build type (same app id, key and icon as the
  debug build), built in a separate, non-required CI job after the compile passes. The Wear app gets
  the same build type, kept as a CI artifact so it pairs with the phone's Drop build. Release builds
  use R8 too.

### Fixed

- Phones in landscape use the width (decision 0021): the bike, component, add-bike, welcome and
  permissions screens split in two, what the screen says and its button on the left and what it
  lists on the right; Settings and a bike's page show their two panes. Before, a phone-width column
  sat in the middle, and the screens with buttons at the foot squeezed their middle into a sliver
  (add bike's type chips and power meters didn't show). In split screen, those screens now scroll
  their buttons with the rest.
- Turning the phone while waking a bike no longer restarts the listening timer.
- The Android release build is called "Cassette"; only debug and internal builds say "(dev)".
- The iPhone app, its widgets and the watch app carry the real version and build number (they all
  said 1.0 (1)): the version from `cassette.version`, shared with Android, and CI's build number.
- In-app updates work again in the builds Drop ships: since the R8 `internal` build, they were switched
  off because that build isn't debuggable.
- On a tablet, Settings sits at the foot of the bike rail again instead of floating halfway down it,
  and is now an outlined button to match Add bike.
- The dividers between Settings and Diagnostics rows sit midway between them; they hugged the row
  above.

[Unreleased]: https://github.com/r0adkll/cassette/compare/1.0.0...HEAD
[1.0.0]: https://github.com/r0adkll/cassette/releases/tag/1.0.0
