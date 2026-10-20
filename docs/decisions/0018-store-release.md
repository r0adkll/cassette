# 0018: Store release

Date: 2026-10-20 · Status: accepted; the first version, backups and languages still open

Milestone 4 ([roadmap](../roadmap.md)): Cassette on Google Play (phone and Wear) and the App Store
(iPhone, iPad and Apple Watch). Distribution was sideload only ([0008](0008-tooling.md)); Drop stays for
internal builds, and the stores are added beside it.

## Decisions

- **Developer tools ship in store builds.** Diagnostics → Developer (Load sample bike, Demo refresh,
  Simulate a low battery, Remove bike) stays in every build, as Diagnostics does
  ([0005](0005-diagnostics.md)). Reviewers don't have AXS components: the sample bike is how they see
  the app, and Demo refresh is how they see the Live Activity. The review notes walk them to it
  (`fastlane/metadata/ios/review_information/notes.txt`, and Play's App access). The sample bike
  carries a month of history so its charts and estimates show; it stays named "Sample bike" in the
  app, and its keys never match a real component.
- **Tablets ship.** Android tablets and foldables, and the iPad (the iOS app keeps iPhone and iPad),
  with the large-screen layouts ([0014](0014-large-screens.md)). The listings have 7" and 10" tablet
  sets on Play and an iPad set on the App Store.
- **Listings** live in `fastlane/` for supply and deliver, text and images, rebuilt by
  `scripts/store-assets` (`fastlane/README.md`). They say Cassette is unofficial and not affiliated with
  SRAM, keep SRAM and AXS out of the app names and App Store keywords, and show no SRAM names or marks.
  Play's category is Tools; the App Store's are Utilities, then Sports. Support goes to GitHub issues.
- **Signing.** Play App Signing holds the app signing key, one for the phone and Wear apps (the Data
  Layer needs it, [0013](0013-wear.md)); we keep an upload key, made by `scripts/release-signing-wizard`
  ([0008](0008-tooling.md)).
- **Versions.** One version for every app, `cassette.version` in gradle.properties. Android release codes
  are `MMmmpp0F` (F: phone 0, Wear 1), Drop builds count first-parent commits, and iOS build numbers
  are the commit count.

## Still open

- **The first public version,** whether releases are tagged, and cutting CHANGELOG's `[Unreleased]`
  into it.
- **Backups.** The phone app leaves Android backup on by default; Wear sets `allowBackup="true"`. What
  the database and settings do on a new phone.
- **Languages.** English only for v1, said so in both consoles.
