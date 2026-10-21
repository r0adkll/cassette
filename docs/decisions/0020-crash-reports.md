# 0020: Crash reports

Date: 2026-10-20 · Status: accepted

## Decision

The phone apps send crash reports to Firebase Crashlytics. It's the one thing Cassette sends
anywhere, so it replaces "nothing collected" in the privacy answers (M4), and it can be turned off.

- **On by default, with a switch.** Settings → About → Privacy → "Send crash reports". Off stops
  collection and deletes reports not yet sent, so a crash from while it was off never goes out later.
  Collection starts off in each manifest (`firebase_crashlytics_collection_enabled`,
  `FirebaseCrashlyticsCollectionEnabled`) and `followCrashReports` (`:app:shared`) applies the
  setting once it loads; Crashlytics remembers the last value across launches.
- **Which builds.** Android: the non-debuggable ones, release and the internal builds on Drop (testers'
  crashes are the useful ones). iOS: Release (TestFlight and the App Store). Debug builds never report.
- **Phones only.** `:app:wear` and the watchOS app don't link Firebase; revisit once the phone apps'
  reports show whether the watches need it.
- **Nothing of ours is attached.** No custom keys, logs, user ids or non-fatals, and no Analytics (so no
  breadcrumbs). What goes is what Crashlytics always collects: the stack, app state, device model and
  OS, and its installation id. Bikes, readings and locations stay on the phone, as About says.
- **Kotlin crashes on iOS.** Touchlab's CrashKiOS hooks Kotlin's unhandled exceptions, so they arrive
  as Kotlin stacks rather than a bare `abort`. `CassetteKit` builds with `-Xadd-light-debug=enable`,
  so its frames have line numbers in the app's dSYM, which the Crashlytics build phase uploads.
  The framework is static, so CrashKiOS's link plugin isn't needed.
- **Configs stay out of git.** `google-services.json` and `GoogleService-Info.plist` belong to one
  Firebase project, and the repo is public: forks shouldn't report into it. `scripts/firebase-wizard`
  sets the project up and writes both (and the CI secrets); CI writes them for the internal and
  TestFlight builds. Without them everything builds and nothing reports: the google-services plugin
  only warns, the R8 mapping upload is off, Swift doesn't configure Firebase.

## Why

The protocol is validated on one bike (0005) and the app goes public on two platforms; crashes on
phones and bikes we don't have would otherwise reach us only when someone files an issue. Crashlytics
is free, symbolicates both platforms, and isn't tracking under either store's rules (Apple: not linked
to the person, not tracking; the same two types its SDK's privacy manifest declares).

## Store answers

- App Store: Crash Data and Other Diagnostic Data; not linked to the person, not used for tracking;
  purpose App Functionality. The app's `PrivacyInfo.xcprivacy` says the same.
- Google Play Data safety: App info and performance → Crash logs and Diagnostics, and Device or other
  IDs (Firebase's installation id); collected, not shared, encrypted in transit, optional (the switch);
  purpose App functionality.
- The privacy policy (thescavengers.software/cassette/privacy, effective 2026-10-20) has a crash
  reports section: what a report holds, the switch, Firebase as a third party, 90-day deletion.
- `fastlane/README.md` has the full Play form and `fastlane ios privacy` uploads Apple's answers.

## Alternatives

- **Opt-in.** The most private, but few people would turn it on and the reports are the point.
- **Sentry, Bugsnag.** Paid past small volumes, or self-hosted; Crashlytics has CrashKiOS for Kotlin
  on iOS.
- **Drop's crash reports only.** Internal Android builds only; nothing from the stores or iOS.
