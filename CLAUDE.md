# Cassette

Unofficial battery monitor for SRAM AXS bike components. Kotlin Multiplatform (Android first, iOS later),
Compose Multiplatform, Circuit + Metro, SQLDelight. Product, design and architecture decisions live in
`docs/` (start with `docs/product.md`, `docs/architecture.md` and `docs/decisions/`). Read the relevant
decision record before changing behaviour it covers; add a new record for new decisions.

The current code is the proof of concept (single Android module). The KMP scaffold described in
`docs/architecture.md` replaces it; until then, don't build new features on the POC.

## Conventions

- **Modules:** `api` modules depend only on `:core` or other `api` modules; `impl` modules contribute the
  bindings. Split into `api`/`impl` only where there's a real seam.
- **Circuit + Metro** (as in Campfire): presenters take the screen and `Navigator` as plain constructor
  parameters; use `fun interface` instead of function typealiases; Android entry points (receivers, tile
  service, widgets, `CompanionDeviceService`, Wear listener) reach the graph through component accessors.
- **Database:** every `.sq` change ships a `.sqm` migration.
- **Changelog:** behaviour changes get a CHANGELOG.md entry under `[Unreleased]` (Keep a Changelog).
- **Notifications:** keep a ledger of notification ids and channels in this file; give each PendingIntent
  its own request code.
- **Formatting:** ktfmt, KotlinLang style. Run `./gradlew ktfmtFormat` before committing.
- **Commits** keep their `Co-Authored-By` attribution lines.

## Diagnostics stay

The protocol is reverse engineered and validated on one bike. Keep the diagnostic layers (raw advert and
GATT bytes, nearby SRAM-looking devices, hub table dumps, ride tests, scan profiles) in every build. When
polishing UI, move them into the Diagnostics area; never delete them. New features should log to the same
diagnostic surfaces.

## SRAM material

- No SRAM artwork, logos, fonts or code in the repo, even if extracted for reference.
- The app is unofficial and must say so; don't imitate SRAM's branding beyond using red as our hero colour.
- Protocol knowledge is documented in our own words with its source (captures, public projects).

## Notification ledger

| Id | Channel | Purpose |
|---|---|---|
| (none yet) | | |
