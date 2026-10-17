# 0012: Visual direction

Date: 2026-10-16 · Status: accepted

Chosen after four design iterations ([design process](../design-process.md), issue #1). Mockups:
https://claude.ai/artifact/V4ZxNUPRnWq524H7FpHRYu (private; iteration 4 is the approved row).

## Direction

The instrument-cluster base (dark-first, condensed numerals, panel rows), with the editorial big header and
full-width status block, and soft-modern charts (thick lines, filled area).

## Colour

Status uses traffic-light colours, and the status block's colour is the status:

| Status | Block | Text on block | Bars and labels (dark / light) |
|---|---|---|---|
| Good | `#2DB36A` | `#03200F` | `#4ADE80` / `#1E8E3E` |
| Low | `#FFB020` | `#1A1200` | `#FFB020` / `#B86E00` |
| Charge now | SRAM red `#E4002B` | white | SRAM red |
| Old reading (not seen in 14+ days) | panel grey, outlined | muted | grey |

SRAM red is also the brand accent (live dot, charge markers in brand surfaces, app identity), so the only
time it fills a large area is when something needs charging now. Colour is never the only signal: every
status also has an icon and a word.

Neutrals: dark `#0A0A0B` background, `#141416` panels, `#2A2A2F` lines; light `#EDEDEB` background, white
panels, `#D6D6D2` lines. Light and dark follow the system; no dynamic colour.

## Type

- **Barlow Condensed 700** for display (bike name, status, titles), numerals and small uppercase labels.
- **Barlow** 400–600 for body text.

Archivo was tried for display and rejected: wider, so long bike names and statuses wrap.

## Icons

[Lucide](https://lucide.dev) (ISC licence; ship its notice) for every icon. Status: `circle-check` good,
`battery-low` low, `battery-warning` charge now, `history` old reading. 2 px stroke.

Component icons are parked. Components show their code (RD, FD, L, R, PM) until we have outline icons of
SRAM-app quality, which must be our own (never SRAM's artwork).

## Screens

- **Bike**: bike name (switcher), then the status block. The block summarises instead of repeating the
  list: overall status, what to charge next and roughly when, last seen and whether background capture is
  on. Below it, one panel row per component: code badge, name, source line, value, status icon and word, a
  thin status-coloured bar. Refresh is the one primary button.
- **Component**: name, status block with the big value and its source, a stat grid (voltage, shifts,
  firmware, model, serial, last read), history chart (4 px line, filled area, charge markers, dashed 20%
  line, 30 D / 90 D / 1 Y), link to raw data in diagnostics.
- **Widgets**: small is the status block; medium is one vertical bar per component; large and all-bikes use
  thick bars with the name and value inside the bar (like Pixel's battery widget).

## Remaining surfaces (iteration 5, approved)

- **Add bike**: wake (listening pulse, timer, "not finding it?"), found (components read from the hub),
  the system companion-link dialog, name and type (road / gravel / MTB / other) with an optional local
  photo, power meter picked from nearby Quarqs by signal strength (spin the crank to confirm), then the
  background-capture permissions (one screen, see Onboarding below).
- **Quick Settings tile**: Android draws it, so status shows as the Lucide status icon plus words in the
  subtitle. States: good, low, refreshing (60 s countdown), not seen (inactive).
- **Notifications**: SRAM red accent; pre-ride heads-up with "Snooze until tomorrow", status-change alerts,
  not-seen reminders, and a minimised capture notice. The small icon waits on the app icon.
- **Settings**: default bike, background capture and refresh length, one switch per notification channel,
  diagnostics, licences (Barlow, Lucide), "Unofficial. Not affiliated with SRAM."
- **Diagnostics**: capture-health panel, then live adverts (monospace hex, frame tag, RSSI, filters), nearby
  devices, hub tables (decoded battery and firmware tables plus raw hex), ride tests, scan profile, and
  "Share diagnostics". Monospace is JetBrains Mono.
- **Wear**: bike list, bike screen (status block then one bar per component), a tile with Refresh (asks the
  phone to scan), and complications (weakest component as a ranged value, status as short text). Watch
  faces may override our colours, so icons and words carry status there too.

## Onboarding (iteration 6, approved 2026-10-17)

- **Welcome (C · Poster)**: a full-bleed SRAM red block ("Know before you ride.") running up under the status
  bar, three numbered points (hears the bike when it wakes, shows it at a glance, warns you before a ride),
  Get started, and the unofficial notice as a one-line footnote.
- **Permissions (P1 · Checklist)**: "Two things to allow." Each permission is a panel with its own Allow chip,
  one system prompt per tap; granted shows a green check and ALLOWED, blocked shows a Settings chip and a
  line in amber. Continue is always enabled. States are re-read when the app resumes, so a change made in
  system settings shows straight away.
- Two screens, then add bike. Rejected: A · Product hero, B · Everywhere, P2 · One button.

Defaults taken where nothing was decided: power-meter choice by signal strength; scan profile lives only in
Diagnostics; no activity log on the bike screen yet.
