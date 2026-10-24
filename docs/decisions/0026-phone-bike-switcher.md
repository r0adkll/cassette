# 0026: Switching bikes on a phone

Date: 2026-10-23 · Status: accepted

Wider windows list the bikes (chips in the top bar, or the rail: [0014](0014-large-screens.md)),
but the phone's column only had "1 OF 2" and a chevron beside the bike's name, which couldn't be
tapped. With two bikes or more, a phone had no way to switch. Mockups of three directions, upright and
on the iPhone Duo with its vertical bar: <https://claude.ai/artifact/AVV1fhREXv4CVVrRvcPE52>.

- **A · The name is the button:** tap the bike's name to open the bikes. It's the biggest target on the
  screen, but nothing says it's a button, and it says nothing about the other bikes.
- **B · A bikes pill in the top bar (chosen):** where the wordmark was, beside Add bike and Settings.
- **C · Chips, as on the open foldable:** one tap and no sheet, but they take a row of the phone's
  height and run off the edge after a few bikes.

## The pill

`BikesPill` (`:common:compose`): the bike icon and "3 BIKES", then, when other bikes need charging,
a divider, the worst tone's icon and "1 LOW" or "2 CHARGE NOW" in its colour, and a chevron. The bike
on screen doesn't count; its status block already says. Charge now outranks low; a bike not seen for a
while isn't a reason to switch. Screen readers hear "3 bikes, Gravel bike is low. Switch bike".

- It shows with two bikes or more, in the phone's column and on a phone on its side (the left pane),
  and replaces the "Cassette" wordmark there. One bike keeps the wordmark.
- It takes the width Add bike and Settings leave. Too narrow for its word (a 360 dp phone with "CHARGE
  NOW", large text), the word goes and the icon and number stay.
- Solid on Android, Liquid Glass on iOS, as the bike chips: UIKit lacks the app's fonts, so the row
  is drawn into an image on the glass capsule.

## The Duo's vertical bar

On an iPhone Duo the top bar's buttons are in the vertical bar ([0017](0017-ios.md)), which holds
buttons, not a pill. The bikes are its first action, above Add bike and Settings: the bike icon,
labelled as the pill, with a `UIBarButtonItem.Badge` counting the bikes that need charging in their
status colour (`BarCount`). No wordmark is replaced, so "Cassette" stays.

## The bikes sheet

`BikesSheet` (`:features:bikes:ui`) rises from the bottom over a scrim, up to the phone's width
(480 dp) and to 48 dp below the status bar. A kicker ("3 bikes · 1 low"), "Your bikes", Close, then
a row per bike: its status icon, name, status word and when it was seen, and for a bike that is low or
needs charging, why ("Front derailleur at 18%, about 5 days left"). The bike on screen is outlined with
a check. Tapping a row shows that bike and closes the sheet.

- Close, the scrim, back, or dragging its top down closes it (96 dp, or a fling).
- Add bike is at its foot; on a phone on its side, too short for that, it sits beside Close. On the
  Duo, Close and Add bike go in the vertical bar, as a sheet's controls do there.
- The top bar's glass and the vertical bar's buttons give way while it is open (`LocalTopBarShown`).
- It closes when the window grows into one that lists the bikes, or when a bike removed elsewhere
  leaves just one.

## Not changed

Chips and the rail stay where they were. The short medium window (the Duo closed on its side,
[0024](0024-short-medium-windows.md)) shows the dashboard, with its chips.
