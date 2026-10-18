# 0015: Rotating status titles

Date: 2026-10-17 · Status: accepted

The bike screen's status block can show a playful title in place of its plain one ("No single-speed
today." for "All good."), picked at random from a short list each time the app opens. It plays on the
name and the welcome tagline ("Never ride single-speed by accident.", [0012](0012-visual-direction.md)).
Iteration 12 on the design canvas shows every title in its status block.

## Only the title changes

The block's colour, icon, sentence ("Front derailleur at ≈11%.") and seen line stay exactly as they are
([0003](0003-battery-presentation.md)), so the status reads the same whatever the title says. Screen readers
get the plain title.

## Where

The phone app's bike screen only: the phone layout and the dashboard on foldables and tablets
([0014](0014-large-screens.md)). The tabletop refresh keeps the plain title: it's one line at 72 sp and
shares its slot with the scan's progress ("2 of 3."). Widgets, the Quick Settings tile, notifications, Wear
and Diagnostics keep the plain titles: they are glanced at, and a reworded title slows that down.
`BikeSummary` (`:features:bikes:summary`) keeps producing the plain title for every surface; the bike screen
swaps it, so nothing else has to opt out.

## When it changes

A title is picked when the app comes to the foreground and kept until it next does, so it never changes while
the screen is open or as it recomposes. `StatusTitles` (`:features:bikes:ui`, app-scoped) holds the picks;
`MainActivity` starts a new round when it stops for real (not for a configuration change). The plain title is
in each list on equal terms, and the same title isn't picked twice in a row for a status. Picking is a pure
function of the status, the previous pick and a `Random`, so tests seed it; screenshot tests set the title
directly.

## Tone by status

Funnier when things are fine, plainer as they get urgent: a joke must never make a warning feel optional.
Every Charge now title still says to charge. No title claims something we can't measure, such as how many
shifts or rides are left.

| Status | Plain title | Rotation |
|---|---|---|
| Good | All good. | No single-speed today. · Fully wound. · Charged in every gear. · Full tape. Press play. · Wireless, not careless. · Send it. |
| Low | Charge soon. | Side A's nearly done. · Charge it tonight. · Plug in after this one. · Getting low. |
| Critical | Charge now. | Single-speed incoming. · The tape's running out. · Charge before you ride. |
| Stale | Not seen in N days. | Wake me up. · Gathering dust? · Shift once to check in. |

"No readings yet." and "Status unknown." stay plain: they ask the user to do something, so they need to say
exactly what's going on. The stale titles drop the day count; the seen line still shows it.

## Line breaks

A title with punctuation inside it ("Full tape. Press play.", "Wireless, not careless.") breaks after that
punctuation when it doesn't fit on one line, rather than wherever the width runs out. A title that fits
stays on one line. Titles without punctuation inside wrap normally.

## Turning it off

Settings → Bikes → Playful titles, on by default. Off, the bike screen shows the plain titles. The bike
screen waits for the setting before it shows a title, so a playful one never replaces the plain one (or the
other way round) as it opens.
