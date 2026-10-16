# 0002: Bike-centric model

Date: 2026-10-16 · Status: accepted

## Decision

Organise everything by bike. A bike is an AXS system identified by its hub's serial (the rear derailleur's
`masterSerial`). Components belong to a bike. Bikes are added by waking them near the phone and naming
them; components from bikes that haven't been added are ignored outside diagnostics. Power meters are
attached to a bike by the user.

## Why

The hub tables already group components by system, so bikes come for free. A flat list (the POC) mixes in
other riders' bikes and doesn't scale to more than one bike.

## Consequences

- Needs an add-bike flow and bike management (rename, remove).
- Components heard before their bike is added need a holding state (diagnostics only).
- Power meter attachment is manual, since Quarq meters aren't in the AXS tables.
