# 0010: Product details

Date: 2026-10-16 · Status: accepted

## Add bike

1. Wake the bike (shake it or press an AXS button).
2. Detect the hub, read its tables and list the components found.
3. Name the bike, pick a type (road / gravel / MTB / other), and optionally add a photo.
4. Optionally attach a power meter heard nearby.
5. Switch on background capture, with the permission explainers.

## Default bike

The most recently seen bike, with an option in Settings to pin one. It feeds the Quick Settings tile,
complications and pre-ride nudge. Widgets pick their bike per instance.

## Components joining or leaving

The hub table is the authority. New components attach automatically with an activity entry
("Left shifter added"). Components absent from the table for 30 days are marked removed; their history stays.

## Bike photo

Optional and local only (cropped, resized, never uploaded). Used as the bike-detail hero, in the bike picker
and on the large widget; otherwise the illustration for the bike type is shown.

## Wear (milestone 2)

A minimal Wear app (bikes → component statuses), a tile and complications, fed from the phone over the
Wear Data Layer. Refresh on the watch asks the phone to run its 60-second scan; the watch doesn't scan.

## Widgets

Glance, responsive wherever possible: small (bike name + weakest status), medium (every component's
status), large (component list with last-seen time), and an all-bikes widget.

## Quick Settings tile

Shows the default bike's weakest status. Tapping runs a 60-second refresh scan (wake the bike, read its hub)
and updates the tile.

## Stale data

Remind after 14 days without seeing a bike, once more at 30, then stop until it's seen again.

## History

A per-component history chart, plus detection of pack charges and coin-cell swaps. Keep samples for a year,
down-sampled to one per day after 30 days.

## Themes

Light and dark, following the system. No dynamic colour; SRAM red stays the hero.

## First mockup iteration

Bike overview, component detail and widgets, in all four directions, light and dark, with real data.
