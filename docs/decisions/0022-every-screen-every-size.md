# 0022: Every screen, every size

Date: 2026-10-21 · Status: accepted

[0014](0014-large-screens.md) and [0021](0021-phone-landscape.md) gave the main screens layouts for an
open foldable, a tablet and a phone on its side, and left the rest ("screens not listed": licences,
Diagnostics, hub tables, ride tests) on the phone's column, capped and centred. On a phone on its side
that column is a strip in the middle of a wide, short screen; on a tablet it is a third of the width.
The new Licences screens showed it again. That exception ends: every screen supports every size.

## Rule

- A screen works as a phone column, a phone on its side, an open foldable and a tablet, read from
  `ScreenLayout` (window size classes, as 0014). Capping at phone width is not a layout.
- Wider than a phone, a screen splits in two with `SplitPanes`, as 0021 does for a phone on its side:
  what it says and its one action on the left, what it lists on the right, each pane scrolling on its
  own, split at an open fold's hinge. Pane content keeps the phone's width (480 dp) and sits against
  the split, so a tablet's panes read as one page instead of drifting to its edges.
- A screen with its own reason to differ (the bike's dashboard, the component sheet, Settings' list
  beside its category) keeps its 0014 layout; this is the default for everything else.
- New screens are mocked up at each size before they're built ([0006](0006-design-process.md));
  applying this split to a screen needs no new round.
- Every screen's `jvmTest` renders each size, light and dark.

## Built

- **Titled screens** (Settings' module): `TitledScreen` on a phone, `TitledPanes` wider. The left pane
  has back, the kicker and title, and what the screen says; the right pane is a lazy list.
- **Licences:** the intro, fonts and icons, and licence texts on the left; the libraries by project on
  the right. **A project:** its licences and website on the left; its libraries on the right. **A
  licence:** who uses it on the left; the text on the right.

## Still to do

Diagnostics and its tools (live adverts, hub tables, ride tests and their setup, recording and
results) keep the capped column for now; each moves to the split as it is next touched.
