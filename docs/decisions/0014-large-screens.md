# 0014: Foldables and tablets

Date: 2026-10-17 · Status: accepted

The phone app is laid out for one narrow column, so on an open foldable or a tablet it stretches or
floats. We support foldables and tablets (the Pixel 10 Pro Fold open, Android tablets, and the iPhone
foldable when M3 gets there); desktop is out of scope. Chosen over iterations 10 to 10d
([design process](../design-process.md)). Mockups: https://claude.ai/artifact/V4ZxNUPRnWq524H7FpHRYu
(private; the iteration 10 rows, 10d on top). The look is unchanged ([0012](0012-visual-direction.md)):
these are layouts of the same components.

## Sizes

Layouts follow the window, not the device, so split screen and resizing work.

| Window | Example | Layout |
|---|---|---|
| Narrow (under 600 dp wide, or under 480 dp tall) | Phones, the Fold closed, phones in landscape | The phone layout, as today |
| Medium (600 to 839 dp wide) | The Pixel 10 Pro Fold open (≈790 × 820 dp at default display size; to confirm on the device) | Dashboard; the component sheet covers most of the screen |
| Expanded (840 dp and wider) | Tablets; the iPhone foldable (rumoured 4:3, about iPad mini size) | A bike rail on the left, then the dashboard; a narrower sheet |

Phones in landscape keep the phone layout: they are wide but too short for the dashboard. The Fold's dp
size is computed from its 2076 × 2152 px panel at Pixel's usual density; check it with `adb shell wm size`
and `wm density` before tuning.

## Bike screen: dashboard (iteration 10, C)

- The status block runs full width with Refresh inside it (on the right), then the components as tiles:
  the component to charge next gets a wide tile with its 30-day trend, the rest share a two-column grid
  (three on expanded) with a small trend line each.
- On expanded windows a rail lists the bikes (name, status icon and word, when seen), with Add bike and,
  at its foot, Settings. The rail replaces the bike switcher.
- Rejected: A · wide column (wastes the screen, runs across the hinge) and B · list–detail as the main
  layout.

## Component detail: a sheet over the dashboard (10b, 10c)

Tapping a tile slides the component screen in from the right as a sheet over the dashboard (about
340 ms, decelerating); the dashboard itself never re-lays out. The sheet has close, previous and next
(to reach tiles it covers), then the component screen's content: status block with the value, stats,
history chart and the link to raw data.

- **Medium:** 600 of the open Fold's ≈790 dp, crossing the hinge. The strip of dashboard left showing is
  dimmed; tapping it closes the sheet.
- **Expanded:** 480 dp, over the dashboard's right side, with the rail still in reach. No dimming: tapping
  another visible tile switches the sheet.
- Narrow windows keep navigating to the component screen.

## Tabletop: refresh on a half-open fold (10b to 10d)

When the Fold is half open with the hinge horizontal (tabletop) on the bike screen, the screen splits at
the hinge: readings on the top half, the scan on the bottom half.

- **Top:** the status block, then one tall column per component filled to its level. During a scan,
  components not heard from yet are grey and marked waiting; each one rises into colour as it answers.
- **Bottom:** status, a big number and one line on the left; a circle on the right that holds the one
  action:
  1. **Ready:** the circle is the Refresh button (filled, refresh icon, "REFRESH", "60 S SCAN"), breathing
     slowly. The left says when the bike was last read.
  2. **Listening and reading:** a red ring counts the scan down, with a beating centre dot. Pulse rings
     leave it every 0.9 s at its thickness (6 dp), thin to 1 dp and fade as they grow to about 3.8 times
     its size, under the text and buttons; the bottom half clips them, so none crosses the hinge. Stop and
     Add 60 s sit on the left.
  3. **Done:** the circle fills green with a check, "DONE" and "5 OF 5 READ", landing with a small
     overshoot, and sends one last green ring. Tapping it finishes; Scan again is on the left.
- Rejected: markers for each component around the ring (the columns already say who answered) and
  spinning cassette reels.

## Other screens

- **Welcome:** the poster splits at the hinge: the red half has the wordmark, then the tape mark above
  the title, anchored left; the three points, Get started and the unofficial notice are on the other
  half (10b).
- **Add bike:** waking and found are one screen when wide: the listening pulse and timer on the left, the
  components appearing on the right as they answer (iteration 10).
- **Settings, every size (10c, S3):** settings become categories: Bikes, Capture, Notifications, App
  updates, Diagnostics, Licences, each with a live summary ("2 of 3 on", a red dot when a build is
  waiting). On a phone the category list opens one category screen at a time, with a sentence on what it
  covers; wide windows show the list and the open category side by side, and on expanded windows Settings
  opens beside the bike rail. Rows keep the approved anatomy: icon, title, subtitle, switches in the text
  colour. Diagnostics' tools open in the category's pane. Rejected: S1 (the approved list with a detail
  pane) and S2 (two columns).
- Screens not listed (licences, hub tables, ride tests, permissions) cap their content at phone width
  and centre it, as `TitledScreen` already does.

Widgets, the Quick Settings tile, notifications and Wear are unaffected.
