# 0028: Bikes with more than one hub

Date: 2026-10-27 · Status: proposed; the Found step and "Add a system" need mockups first
([0006](0006-design-process.md))

[0002](0002-bike-centric-model.md) made a bike one AXS system, named by its hub's serial. A tester's
mountain bike (diagnostics export of 2026-10-26) is two: the dropper heads one system (fork, pod,
dropper) and the rear shock another (most likely the rear derailleur and an older AXS controller, but
we haven't read its table). Both flag themselves as the bridge. Add bike stopped at the first hub it
heard, read the dropper's table and saved 4 of the bike's 7 components. The other three advertised
all day, battery included, and capture dropped every frame as someone else's bike. ShannoG's
published captures look like a second such bike: an Eagle Transmission rear derailleur and a Reverb
AXS dropper, both flagged as the bridge and heard by one receiver seconds apart (`docs/protocol.md` §2).

## A bike is one or more systems

- A **system** is a hub and the components in its tables, as before. A **bike** holds one or more
  systems, plus the power meter the user attaches. Most bikes keep one.
- Components still join a bike only through a hub's table (or, for a power meter, the user). A
  component heard nearby but in no table of ours stays someone else's: on a group ride that's
  exactly what it is.
- Rejected: **one bike per system**. The tester would get two bikes for one frame, the default bike,
  widgets and the pre-ride nudge would each see half of it, and "Charge soon." would depend on which
  half was picked.
- Rejected: **attaching every status frame heard alongside the bike**. Riding partners' bikes would
  join the first time they rode together.

## Adding a bike

- After the first hub, Add bike keeps listening for a few seconds and reads every hub it hears.
  Found lists each system as its own group under its hub ("Dropper post system · 3 components").
- Every system starts **included**. A system that's someone else's (two bikes woken in one garage)
  can be taken out before saving. Open: whether a second system heard much more weakly than the
  first should start out instead.
- An existing bike gets **"Add a system"** in its settings: the same wake and listen, offering only
  hubs that aren't on a bike yet. That's how the tester fixes their bike without starting over.
- Removing a system from a bike removes its components, as removing the bike does today.

## Capture

- Each hub is read on its own schedule, with the rules every hub has now: at most once a minute
  while the app is open and every 10 minutes in the background. A background wake that hears both
  hubs of a due bike can read both, so a two-system bike can cost two short GATT sessions per wake.
- The 30-day removal rule stays per component (last seen), so a system that sleeps for a while
  doesn't take the other one's components with it.
- Diagnostics dumps every hub's tables, each with its serial.

## Data

- Hubs move out of `bike` into their own table: hub serial, bike, last table read. The migration
  turns each bike's `hub_serial` and `hub_read_at` into its first row. Nothing else about existing
  bikes changes.
- `BatteryRefresher` treats a heard component as a hub when its serial is one of its bike's hubs,
  rather than the bike's single `hubSerial`.
- Discovery keeps listing hubs that aren't on any bike. Once the tester adds the rear shock's system
  to their bike, it stops showing up as a new bike.

## Not covered

- How SRAM's own app groups two systems on one bike. The 2.54.0 analysis didn't look, and nothing
  here depends on it.
- Pod sides and controller names (`docs/protocol.md` §4.1) are labelling questions, settled
  separately.
