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

Sideload only for now, through Drop (our build distribution platform) at
https://drop.thescavengers.software. Play/Fastlane tooling is adopted but unused until a listing is decided.

- CI signs the debug build with one internal key, so every Drop build can update every other. The key lives
  only in repository secrets (`CASSETTE_KEYSTORE_BASE64`, `CASSETTE_KEYSTORE_PASSWORD`, `CASSETTE_KEY_ALIAS`,
  `CASSETTE_KEY_PASSWORD`), with its owner's copy kept outside the repo. Local builds keep the debug key,
  so an adb install and a Drop build can't update each other.
- Pushes to `main` upload to the `main` channel and same-repository pull requests to `pr/<number>`, with
  Drop's GitHub Action (OIDC, no upload secrets). Without the internal key, nothing is uploaded.
- Drop's Gradle plugin gives the debug build its SDK: Settings → App updates shows what's waiting and opens
  Drop's update sheet, and a red dot on the bike screen's Settings button says one is waiting. Version codes
  count first-parent commits.
