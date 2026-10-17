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

Sideload only for now. CI builds a signed APK on merges to `main` and uploads it to Drop (our build
distribution platform); until CI exists, install with adb. Play/Fastlane tooling is adopted but unused until
a listing is decided.
