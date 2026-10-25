# 0027: Picking a component for complications and widgets

Date: 2026-10-25 · Status: accepted

The complications and the small home widget showed the weakest component, or the one to charge. A
rider who keeps an eye on one battery in particular (a coin-cell shifter, the power meter) couldn't
keep it in view. Mockups (iteration 18, approved 2026-10-25), the road bike all good and then with
its front derailleur low: <https://claude.ai/artifact/8132RQ224cCviL3MFunNw1>.

## What a pick means

- **A complication shows the component it picked**, in that component's own status colour and icon,
  whatever the rest of the bike says. Two slots set to RD and PM stay RD and PM when the front
  derailleur runs low; a slot left on "Weakest" switches to it, as before.
- **The small home widget prefers its component** while everything is fine. Once something is low or
  needs charging now, it names that component instead: its headline is the bike's status ("Charge
  soon."), so the line under it has to agree.
- **"Weakest" is every surface's default** and works as it did: the weakest, or the one to charge.
- Rejected: the warning taking over every slot (two slots set to RD and PM would both say "FD LOW"),
  and the pick shown everywhere (the small widget would read "Charge soon." over "PM 92% · GOOD").

## A pick is a kind

A pick is a component kind (`ComponentKind`'s name: "Rear derailleur"), not one unit, so it
outlives a replaced part and works with "Default bike", whichever bike that is. A bike without that
kind shows the weakest; a bike with two of a kind shows the weaker.

## Surfaces

- **Wear OS:** putting the complication on a face opens its settings (`ComplicationConfigActivity`,
  two Circuit screens in the bike list's buttons): "Which bike?" (Default bike, then each bike with
  its status), then "Which one?" (Weakest, then each component with its value). A swipe goes back a
  step; backing out cancels. Wear's complications pick their bike now too, as the Apple Watch's
  already did. Picks are kept by complication id in the watch's preferences and dropped when the
  complication leaves its face; a face's own default slot skips the settings
  (`DEFAULT_CONFIG_SUPPORTED`) and gets the default bike's weakest. A picked component's ranged value
  is its level ("92", "PM"), and its short text its value ("92%", titled "PM").
- **Apple Watch:** "Weakest battery" is now "Battery" (its kind stays `weakest`, so faces keep it).
  watchOS configures complications only through the face editor's list, so the list holds every
  pick: "Default bike · weakest", "Default bike · RD", and so on for each bike. "Bike", the status,
  keeps its bike-only list.
- **Home widgets:** Android's widget settings ask "Which component?" after "Which bike?", with Back;
  the iPhone's edit menu adds "Component" under "Bike". iOS gives a widget one edit menu for all its
  sizes, so the row is there on medium, large and the Lock Screen too, where it changes nothing; the
  menu's description says it is for the small size. Medium and large already show every component.
- Android's widget settings split in two on a phone on its side, an open foldable and a tablet
  ([0022](0022-every-screen-every-size.md)), "Which bike?" included.

## Data

`ComponentRowModel` carries its kind, so the phone's widgets and the watch (which words Wear's
snapshot itself) pick from the summary as it is. The widgets' snapshot gains each component's kind
(`WidgetComponent.kind`) for the Swift widgets and the Apple Watch; it is optional, so a watch app a
release behind still reads it. The Apple Watch's complication pushes ([0017](0017-ios.md)) now
follow every component's value rather than only the weakest's, since a Battery complication can
show any of them.
