# 0019: Store release

Date: 2026-10-20 · Status: accepted; languages still open

Milestone 4 ([roadmap](../roadmap.md)): Cassette on Google Play (phone and Wear) and the App Store
(iPhone, iPad and Apple Watch). Distribution was sideload only ([0008](0008-tooling.md)); Drop stays for
internal builds, and the stores are added beside it.

## Decisions

- **Developer tools ship in store builds.** Diagnostics → Developer (Load sample bike, Add a random
  bike, Demo refresh, Simulate a low battery, Remove bike) stays in every build, as Diagnostics does
  ([0005](0005-diagnostics.md)). Reviewers don't have AXS components: the sample bike is how they see
  the app, and Demo refresh is how they see the Live Activity. The review notes walk them to it
  (`fastlane/metadata/ios/review_information/notes.txt`, and Play's App access). The sample bike
  carries a month of history so its charts and estimates show; it stays named "Sample bike" in the
  app, and its keys never match a real component. Add a random bike (2026-10-23) adds made-up bikes
  beside it, for testing several: random names, parts and levels, some low or critical, built the
  same way (negative hub serials and sample keys, so capture never matches them).
- **Tablets ship.** Android tablets and foldables, and the iPad (the iOS app keeps iPhone and iPad),
  with the large-screen layouts ([0014](0014-large-screens.md)). The listings have 7" and 10" tablet
  sets on Play and an iPad set on the App Store.
- **Listings** live in `fastlane/` for supply and deliver, text and images, rebuilt by
  `scripts/store-assets` (`fastlane/README.md`). They say Cassette is unofficial and not affiliated with
  SRAM, keep SRAM and AXS out of the app names and App Store keywords, and show no SRAM names or marks.
  Play's category is Tools; the App Store's are Utilities, then Sports. Support goes to GitHub issues.
  The store name is "Cassette: Bike Battery Monitor" (2026-10-23), with the subtitle "Shifters,
  derailleurs & more": "Cassette" alone is taken on the App Store, and Play matches it. The app is
  still Cassette on the home screen and in the app. Words in the name and subtitle count for App Store
  search, so the keywords don't repeat them.
- **Signing.** Play App Signing holds the app signing key, one for the phone and Wear apps (the Data
  Layer needs it, [0013](0013-wear.md)); we keep an upload key, made by `scripts/release-signing-wizard`
  ([0008](0008-tooling.md)).
- **Versions.** One version for every app, `cassette.version` in gradle.properties. Android release codes
  are `MMmmpp0F` (F: phone 0, Wear 1), Drop builds count first-parent commits, and iOS build numbers
  are the commit count.
- **Releases** (2026-10-21). The first public version is 1.0.0. `scripts/release` cuts each one
  from main, as Campfire's does: it regenerates the baseline profiles ([0023](0023-baseline-profiles.md),
  locally, with no emulator.wtf), rolls CHANGELOG's `[Unreleased]` into the version with its date,
  and writes each store's notes from the entries for its platform (`[Android]` or `[iOS]` tags;
  untagged ones go to both). Play's "What's new" is a file per version code, phone and Wear, at most
  500 characters; the App Store's What's New is `release_notes.txt`, none for the first version.
  Claude Code drafts both from the entries, which are written for us rather than for riders, and the
  release manager takes, edits or redoes the draft. Then it commits, pushes main and publishes a
  GitHub release tagged with the bare version (`1.0.0`), which starts the Release workflow; CI's
  TestFlight job builds the same commit. Last, it moves `cassette.version` on (a `[skip ci]` commit),
  so main's Drop and TestFlight builds stop carrying the released version.
- **Backups.** Everything the app keeps goes to a new phone, automatically: the database (bikes,
  components and their history) and settings. The capture log doesn't: it records this phone's
  scans, and it shouldn't eat into Android's 25 MB backup quota.
  - Android: Auto Backup with `data_extraction_rules.xml`, for cloud backup and device transfer
    alike, excluding `capture-log.jsonl`.
  - iOS: Application Support is in iCloud and device backups already; the capture log moves to
    `Application Support/Diagnostics/`, a folder marked excluded from backup.
  - Wear: no backup (`allowBackup="false"`). Its data is the phone's, sent over the Data Layer.
  - On a new iPhone, a component's Bluetooth identifier differs until it is heard again; bikes and
    components match by serial, so this is to check on hardware, not to handle.

## Still open

- **Languages.** English only for v1, said so in both consoles.
