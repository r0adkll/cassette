# 0008: Tooling and process

Date: 2026-10-16 · Status: accepted

## Decision

- **Formatting:** ktfmt, Google style (2-space indents, including continuations; changed from KotlinLang's
  4 spaces on 2026-10-17). `scripts/ktfmt` runs the ktfmt CLI in CI and the pre-push hook (installed by
  `./gradlew bootstrap`) and is the only formatter: the ktfmt Gradle plugin was removed because its Google
  style didn't match the CLI's line for line. Not ktlint, unlike Campfire.
- **No Kover** or coverage gate. Protocol and battery logic are covered by fixture tests from captured bytes.

Adopt from Campfire:
- GitHub Actions: build and unit tests on pull requests;
- CHANGELOG.md (Keep a Changelog) with the changelog plugin;
- Renovate;
- Danger checks;
- baseline profiles;
- the release script with Play/Fastlane.

Commits keep their `Co-Authored-By` attribution lines. Unlike Campfire, Cassette doesn't adopt that rule.

## Distribution

Internal builds go out through Drop (our build distribution platform) at
https://drop.thescavengers.software. Store releases on Google Play and the App Store, with their listings
in `fastlane/`, are [0019](0019-store-release.md); this was sideload only until then.

- CI uploads the `internal` build type: the release build's R8 optimisation and resource shrinking, under
  the debug build's application id (`com.r0adkll.cassette.debug`) and dev icon, so it updates the debug
  installs. The watch app (`:app:wear`) has the same `internal` build type, built alongside it and
  uploaded in the same run as a Wear Build (Drop 0.4.0 tells them apart by the watch feature in the
  manifest, so phones are never offered it); with the same id and key it pairs with the phone's Drop
  build. Both R8 mappings are kept as CI artifacts for retracing. Release gets R8 too, with drop-noop.
- CI signs the internal build with one internal key, so every Drop build can update every other. The key lives
  only in repository secrets (`CASSETTE_KEYSTORE_BASE64`, `CASSETTE_KEYSTORE_PASSWORD`, `CASSETTE_KEY_ALIAS`,
  `CASSETTE_KEY_PASSWORD`), with its owner's copy kept outside the repo. Local builds keep the debug key,
  so an adb install and a Drop build can't update each other.
- Release builds for Play are signed with an upload key; Play App Signing holds the app signing key, one
  for the phone and Wear apps. `scripts/release-signing-wizard` makes the key (kept in 1Password, and in
  `~/.cassette/release.env` for local builds) and sets `CASSETTE_UPLOAD_KEYSTORE_BASE64`,
  `CASSETTE_UPLOAD_KEYSTORE_PASSWORD`, `CASSETTE_UPLOAD_KEY_ALIAS` and `CASSETTE_UPLOAD_KEY_PASSWORD`.
  Without `CASSETTE_UPLOAD_KEYSTORE`, release builds are unsigned. The Release workflow, started by
  the GitHub release `scripts/release` publishes (or by hand), builds both bundles with it, checks their manifests (`scripts/check-release-manifests`) and
  signatures, and keeps them and their R8 mappings as artifacts.
- The required check ("Test and build") is unit tests and a debug compile, without secrets. The internal
  build and its uploads run in a separate "Internal build" job once that passes, and isn't required, so a
  slow or broken R8 run doesn't block a merge.
- Pushes to `main` upload to the `main` channel and same-repository pull requests to `pr/<number>`, with
  Drop's GitHub Action (OIDC, no upload secrets). Without the internal key, nothing is uploaded.
- Drop's Gradle plugin gives the debug and internal builds its SDK: Settings → App updates shows what's waiting and opens
  Drop's update sheet, and a red dot on the bike screen's Settings button says one is waiting. Version codes
  count first-parent commits.
- **Open in App** (Drop 0.6.0, added 2026-10-25): the phone upload passes `open-in-app:
  cassette://drop/{build}`, so a phone Build's page offers the Build to the installed app, whichever
  channel it follows. The link is on Cassette's own scheme, beside the notification and widget links
  (`DeepLinks`), on a host none of them use; the intent filter (`src/drop/AndroidManifest.xml`) is
  added only to the variants with Drop's SDK, so a store build never answers it, and Drop's page
  aims it at the debug package anyway. It also makes `MainActivity` `singleTop` there: a browser
  can't pass the flags notification links use, and without it Android stacks a second activity for
  a new link and ignores a repeat of the link that opened the app. The app asks Drop about that one
  Build and opens the update sheet, which offers it ("Install this Build", saying when it moves the
  app to another channel) or says it's already installed or has expired. Installing it moves the install to that Build's
  channel: a `pr/<number>` Build gets that pull request's updates until it closes, then `main`'s.
  While the sheet is up, App updates shows the opened Build; closing it checks the channel again
  unless a download of it is in hand. A check that fails (no answer, or a Build of another app)
  opens no sheet: App updates says why. Wear Build pages don't offer it; the watch app ignores it.
