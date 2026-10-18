# 0014: Foldables and tablets

Date: 2026-10-17 · Status: accepted

The phone app is laid out for one narrow column, so on an open foldable or a tablet it stretches or
floats. We support foldables and tablets (the Pixel 10 Pro Fold open, Android tablets, and the iPhone
foldable when M3 gets there); desktop is out of scope. Chosen over iterations 10 to 10d
([design process](../design-process.md)). Mockups: https://claude.ai/artifact/V4ZxNUPRnWq524H7FpHRYu
(private; the iteration 10 rows, 10d on top). The look is unchanged ([0012](0012-visual-direction.md)):
these are layouts of the same components.

## Sizes

Layouts follow the window, not the device, so split screen and resizing work. They come from Material's
window size classes (`currentWindowAdaptiveInfoV2()` in JetBrains' build of the Material 3 adaptive
library, so iOS gets the same), never from our own dp figures. `ScreenLayout` in `:common:compose` maps
them:

| Window size class | Example | Layout |
|---|---|---|
| Compact width, or compact height | Phones, the Fold closed, phones in landscape | `Column`: the phone layout, as today |
| Medium or expanded width | The Pixel 10 Pro Fold open (expanded, either way up), tablets in portrait | `Dashboard`: bikes as chips in the top bar; the component sheet covers most of the screen |
| Large or extra-large width | Tablets in landscape | `DashboardWithRail`: a bike rail on the left; a narrower sheet |

Phones in landscape keep the phone layout: they are wide but too short for the dashboard.

The rail waits for large width rather than starting at expanded. The first build guessed the Fold at
790 dp and drew the line at expanded; the open Fold is in fact expanded width, so it got the tablet
layout, and the 240 dp rail left its dashboard too little room. The iPhone foldable (rumoured 4:3, about
iPad mini size) gets whichever class its window is; M3 checks it.

## Bike screen: dashboard (iteration 10, C)

- The status block runs full width with Refresh inside it (on the right), then the components as tiles:
  the component to charge next gets a wide tile with its 30-day trend, the rest share a two-column grid
  (three with the rail) with a small trend line each.
- On large windows a rail lists the bikes (name, status icon and word, when seen), with Add bike and,
  at its foot, Settings. Otherwise the bikes are chips in the top bar. Both replace the bike switcher.
- Rejected: A · wide column (wastes the screen, runs across the hinge) and B · list–detail as the main
  layout.

## Component detail: a sheet over the dashboard (10b, 10c)

Tapping a tile slides the component screen in from the right as a sheet over the dashboard (about
340 ms, decelerating); the dashboard itself never re-lays out. The sheet has close, previous and next
(to reach tiles it covers), then the component screen's content: status block with the value, stats,
history chart and the link to raw data.

- **Dashboard:** about three quarters of the width (iteration 10c drew 600 of 790 dp), crossing the hinge.
  The strip of dashboard left showing is dimmed; tapping it closes the sheet.
- **Dashboard with rail:** 480 dp, over the dashboard's right side, with the rail still in reach. No
  dimming: tapping another visible tile switches the sheet.
- The phone layout keeps navigating to the component screen.

## Tabletop: refresh on a half-open fold (10b to 10d)

When the Fold is half open with the hinge horizontal (tabletop) on the bike screen, the screen splits at
the hinge: readings on the top half, the scan on the bottom half. The posture and the hinge's position
come from the same adaptive library (`windowPosture`: `isTabletop` and the hinge list).

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
  covers; wide windows show the list and the open category side by side, and with the rail Settings
  opens beside the bike rail. Rows keep the approved anatomy: icon, title, subtitle, switches in the text
  colour. Diagnostics' tools open in the category's pane. Rejected: S1 (the approved list with a detail
  pane) and S2 (two columns).
- Screens not listed (licences, hub tables, ride tests, permissions) cap their content at phone width
  and centre it, as `TitledScreen` already does.

Widgets, the Quick Settings tile, notifications and Wear are unaffected.
