# Design process

([0006](decisions/0006-design-process.md))

## Principles

- **SRAM red is the hero colour.** Material 3 is the fallback component layer, not the look.
- **Unique and modern.** The UI should not read as a stock Material app.
- **No UI is built before its design is approved.**

## Iteration

1. **Explore**: interactive HTML mockups (published Artifacts) of real screens, using real data from the
   validation bike. Directions are shown side by side.
2. **Choose**: pick a direction, then iterate on its screens and surfaces until approved.
3. **Spike**: build the design system in Compose (colour, type, shape, core components) and check it on a
   real device before any feature UI.
4. **Build**: features use only design-system components.

## Directions to explore

All use SRAM red as the hero:

| Direction | Character |
|---|---|
| Instrument cluster | Dark-first and cockpit-like: big numerals, gauges and arcs, red as the signal colour. Feels like a bike computer. |
| Editorial / Swiss | Light, bold grotesk type, strong grid, red blocks, generous whitespace. Feels like a race poster. |
| Technical / blueprint | Component line drawings, monospace details, a schematic of the bike with live status. |
| Soft modern | Expressive Material 3, rounded layered cards, red tonal palette. The safest option. |
