# 0016: Edge to edge

Date: 2026-10-18 · Status: accepted

Every screen in the phone app draws edge to edge. Backgrounds, panes, the bike rail and their dividers run
under the status and navigation bars, and content scrolls under them. Before this, the app padded every
screen inside the bars (`OpaqueScreens`). That left a strip of plain background at the top and bottom of a
tablet's rail and Settings' split, and only screens that measured the bars and drew past their own bounds
could reach the edges (the welcome's red, the component sheet).

## Who pads

Each screen pads its own content by `WindowInsets.safeDrawing`, so the navigation decoration no longer
does. A screen that scrolls puts `safeDrawingPadding()` after its `verticalScroll`, which lets the content
run under the bars and still clear them at either end. A screen with a fixed footer (Get started,
Continue, Add bike's footer) pads the whole column, so the button always sits above the navigation bar.
Coloured blocks that touch an edge (the welcome's red, the empty bike's and permissions' panels, the
tabletop's halves) take their background first and pad inside it.

## Panes

A pane only pads by the bars at its own edges. `SplitPanes` does this for its two panes; the bike rail,
Settings' list and its open category, and the component sheet use `startPane()` and `endPane()`
(`:common:compose`), which consume the other side's inset. A screen nested in a pane (Diagnostics in
Settings, the component in the sheet) then pads the right sides with the same `safeDrawingPadding()` it
uses on its own.

## The status bar

Content scrolls under the status bar as it does under the navigation bar, with no scrim: a scrim in the
background colour hid it and made the screen look padded again. The top padding keeps the content clear of
the clock until it scrolls, and the bottom padding clears the gesture handle at the end of the scroll.

## Out of scope

Wear has its own layouts for a round screen ([0013](0013-wear.md)). Material's bottom sheets (the Drop
update sheet) handle the bars themselves. Renders have no system bars, so they look as they did; check
inset changes on a device or emulator.
