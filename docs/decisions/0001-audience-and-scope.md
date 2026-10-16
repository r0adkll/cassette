# 0001: Audience and v1 scope

Date: 2026-10-16 · Status: accepted

## Decision

Cassette is personal first, built to go public. v1 ships the phone app, a home screen widget, notifications
and a Quick Settings tile (milestone 1), then a Wear OS complication and tile (milestone 2).

## Why

The protocol has only been validated on one Red AXS bike, so building for every groupset now would be
guesswork. Keeping the code and design public-ready (no SRAM artwork or code, an "unofficial" notice,
graceful handling of unknown hardware) keeps the option to publish open. The surfaces match the original
goal: battery state that's reliable and visible without opening an app.

## Consequences

- Unknown component types, models and frame shapes must degrade gracefully and appear in diagnostics.
- SRAM artwork extracted during research stays out of the repo.
- Wear depends on phone-side data; the watch doesn't scan.
