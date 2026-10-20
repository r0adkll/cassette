# 0018: Friendlier ride tests

Date: 2026-10-19 · Status: accepted

Mockups: https://claude.ai/artifact/W1iFXbSa6jYzPEvSF9Fksn (private; row A, "Guided steps", is the
approved one; B was a one-screen setup with a report card).

## Decision

Ride tests (Diagnostics, decision 0005) become a short guided flow with results in plain words:

1. **What to test**: "Low power mode" or "Batched 30 s mode", each with a line on what it does (the
   everyday one first), and the ride's name. Options are named, not asked as questions. The name
   starts empty with an example ("Morning commute"), and Next waits until there is one. iOS sets the
   scan's cadence itself (0017), so it shows only the name.
2. **Before you go**: Bluetooth, Nearby devices, Background capture and when the bike was last heard,
   each with a green tick or an amber warning (Start is never blocked), and three "on the ride" steps.
3. **Recording**: time ridden, when the bike was last heard, the longest quiet stretch so far and a
   strip of wakes. Leaving it keeps the test running; the Ride tests screen shows it as a row.
4. **Results**: a verdict block in the status colours, a sentence on what happened, the wakes across
   the ride, each component by name with its share of wakes, battery reads and phone battery. The raw
   numbers stay under Details (decision 0005).
5. **Ride tests** lists past tests with their verdict and compares the two profiles: rides, wakes an
   hour, the longest quiet stretch and phone battery per 15 minutes (tests of 5 minutes or more).

Setup is two steps on one screen: the step's content slides (forward from the end, back from the
start) under a title that stays put, and system back on step 2 goes back a step. The only progress
cue is the kicker ("Step 1 of 2"). The recording isn't a step: it's the same screen whether Start
or the Ride tests list opens it, so it carries no progress. A step bar that filled into the
recording was tried and dropped: it ran after the screen's transition, and was missing when the
recording was opened again from the list.

A test that tries another profile switches to it for the ride and puts the everyday one back on
Stop. The profile to restore is stored in the session's start event in the capture log, so it
survives the app being killed mid-ride.

## Verdicts

- **Kept up**: never quiet for more than 2 minutes, and every background read worked. Battery
  levels change far more slowly than that.
- **Patchy**: a longer quiet stretch, or reads that failed or ran out of time. A stop long enough for
  the bike to sleep does this too, and the sentence says so.
- **Lost the bike**: no wakes at all, or the scan couldn't be armed.

The timeline tints quiet stretches of 45 s or more; shorter empty slices (early in a test, or between
Batched 30 s wakes) are a baseline.

## Why

The old screen was a label field, a Start button and monospace numbers, and the scan profile was a
global setting in Diagnostics that a test only recorded. Comparing profiles meant changing everyday
capture by hand and remembering to change it back.
