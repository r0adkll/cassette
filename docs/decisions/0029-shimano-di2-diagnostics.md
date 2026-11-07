# 0029: Shimano Di2, diagnostics first

Date: 2026-11-06 · Status: accepted

Riders asked about Shimano's electronic groupsets. Public projects say a 12-speed Di2 rear
derailleur exposes its system battery through the standard Battery Level characteristic with no
pairing, and everything richer through Shimano's own services (`docs/protocol-shimano.md`). Nobody
on the project has a Di2 bike, and [0001](0001-audience-and-scope.md) says protocols are validated
on hardware before features are built on them.

## Collect evidence before building support

- **Diagnostics hear Shimano.** Live adverts shows Shimano adverts beside AXS frames, with a
  Shimano filter. They're matched by any of company id `0x044A`, a `SHIMANO_BLE` service UUID or an
  `RDR` name, and kept raw.
- **They go in the capture log**, so a tester's export carries them: the first from each derailleur,
  then each change of its bytes, at most every 5 s, so a counter in the advert can't flood the
  5,000-event log.
- **A probe reads the open characteristics.** A Shimano row's "Read battery" makes one short
  connection that reads Battery Level and Device Information (not the serial number) and logs the
  result as a `gatt` event of kind `ShimanoProbe`. It lists the services the derailleur offers
  (on Android; iOS lists only those it reads). The row warns not to use it while riding until we
  know a phone's connection leaves shifting alone.
- **Only while Diagnostics is open.** The background scan, capture, bikes, alerts and surfaces are
  unchanged: no Shimano filter wakes the app, and nothing heard is saved to a bike.
- Rejected: **a Shimano background filter now.** It would wake the app for any Di2 derailleur or
  Shimano e-bike nearby, for riders who can't use the result yet.

## Never open E-TUBE's session

- Cassette doesn't use Shimano's E-TUBE service (`18FF`). Its key derivation comes only from
  decompiled Shimano apps, which conflicts with the rule against vendor code that keeps SRAM's code
  out of the repo. Getting around its authentication is a legal and store risk for a public app.
  Its session also leaves the derailleur in a mode where it doesn't shift, which a background read
  mid-ride must never cause. This matches AXS: `docs/protocol.md` documents SRAM's authenticated
  session and Cassette doesn't use it.
- SBI (`18EF`) is a documented Bluetooth service that needs only a normal bond, so it stays open for
  later. Bonding needs a system pairing prompt and a GATT client that writes, which today's
  read-only client doesn't do. Its own decision comes once a tester's data shows whether it carries
  more than `0x2A19`.

## Next

A tester with 12-speed Di2 sends an export from a session with Live adverts open: wake the bike,
read the battery, shift. That answers whether adverts carry battery and whether the probe disturbs
shifting (`docs/protocol-shimano.md` §5). Supporting a Di2 bike, with one battery and shifters shown
as not reported, gets its own decision and mockups ([0006](0006-design-process.md)) after that.
