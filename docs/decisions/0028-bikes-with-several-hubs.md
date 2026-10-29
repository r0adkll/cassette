# 0028: Bikes with more than one hub

Date: 2026-10-27 · Status: accepted. Mockups ([0006](0006-design-process.md)), with the tester's
readings: https://claude.ai/artifact/ABGtCdE6tbtFhZFDTC8F2w (private; the "chosen" and B boards are
the approved ones, except the faint hub, rejected below)

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
  Continue works throughout, so a one-hub bike doesn't wait the window out. Found lists each system
  as its own group, named after its hub ("Dropper post system", with the hub's serial); the text
  names the hub too, rather than assuming a rear derailleur.
- With two systems or more, each group has a switch, and every system starts **on**. A
  switched-off system folds to one line and its components stay someone else's.
- Rejected: starting a much fainter hub **off** (20 dB below the first; the mockups' "Heard
  faintly"), to keep two bikes woken in one garage from merging. Signal strength can't tell a
  bike's hubs apart. In the tester's second export (2026-10-28) each of their two hubs ranged from
  −27 to −77 dBm, depending on which one the phone was near. Replayed, the rule started one of
  them off in 4 of 51 cases, and averaged over the 10 s window they were still up to 25 dB apart.
  Another bike is only heard if it was moved in the last few minutes, and a system that isn't the
  bike's comes off with "Remove this system"; one wrongly left off halves the bike without a word.
- An existing bike gets **"Add a system"** in its settings: two steps, wake and found, offering
  only hubs that aren't on a bike yet. While it listens it lists the hubs it hears that are on the
  bike already, so the rider knows the phone hears the bike. That's how the tester fixes their bike
  without starting over.
- Add bike and Add a system write to the capture log: each hub heard, its signal, how its system
  started, hubs heard after the window, the rider's switches, when they continued, and each hub
  read with its table's bytes. The tester's second export (2026-10-28) saved one system again and
  couldn't say why: the flow logged nothing.
- Bike settings group the components under their system (hub kind and serial), then the power
  meter on its own. Each group of a bike with two or more ends in "Remove this system", which asks
  for a second tap as Remove bike does and removes its components and their history. A one-system
  bike shows its one group with no remove (Remove bike does that). Rejected: a separate Systems
  section under an unchanged component list.

## Capture

- Each hub is read on its own schedule, with the rules every hub has now: at most once a minute
  while the app is open and every 10 minutes in the background. A background wake that hears both
  hubs of a due bike can read both, so a two-system bike can cost two short GATT sessions per wake.
- The 30-day removal rule stays per component (last seen), so a system that sleeps for a while
  doesn't take the other one's components with it.
- Diagnostics dumps every hub's tables, each with its serial.

## Data

- Each system is a row of a new `hub` table: hub serial, bike, when it was added and when its
  table was last read. A component's `hub_serial` says which system listed it (none for a power
  meter); it keeps the first one, should two tables ever list it.
- The migration (v3 → v4) turns each bike's `hub_serial` and `hub_read_at` into its first `hub`
  row and puts all its components but the power meter in that system. The sample bikes' made-up
  hubs get no row: their serials are only shown, and a real bike may have them.
- `bike.hub_serial` stays, as the hub the bike was added with; removing that system hands it to
  another of the bike's hubs, so the removed hub can start a bike of its own. `bike.hub_read_at`
  becomes the last read of any of its hubs, for the screens that show one.
- `BatteryRefresher` treats a heard component as a hub when it has a `hub` row on its bike, and
  throttles each by its own read time.
- Discovery keeps listing hubs that aren't on any bike, and now also reports hubs it hears that are
  a bike's systems already (for Add a system). Once the tester adds the rear shock's system to their
  bike, it stops showing up as a new bike.

## Not covered

- How SRAM's own app groups two systems on one bike. The 2.54.0 analysis didn't look, and nothing
  here depends on it.
- Pod sides and controller names (`docs/protocol.md` §4.1) are labelling questions, settled
  separately.
