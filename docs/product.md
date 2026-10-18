# Cassette: product

Cassette keeps track of the batteries in SRAM AXS components without the official app. It listens for the
components' Bluetooth broadcasts, reads the rear derailleur's system tables, and shows battery state on the
phone, a home screen widget, Wear OS and notifications.

Status: design phase. The proof of concept (commit `4face31`) proved the protocol on a Red AXS bike.

## Audience

Personal first, built to go public ([0001](decisions/0001-audience-and-scope.md)). It is validated on one
Red AXS bike, but the code and design stay public-ready:

- no SRAM artwork, logos or copied code in the repo;
- clearly marked as unofficial and not affiliated with SRAM;
- unknown hardware is handled gracefully and surfaced in diagnostics, so other groupsets can be added.

## Surfaces (v1)

| Surface | Milestone |
|---|---|
| Phone app | 1 |
| Home screen widget | 1 |
| Notifications | 1 |
| Quick Settings tile | 1 |
| Wear OS complication | 2 |
| Wear OS tile | 2 |

Wear reads the phone's data; the watch does not scan for components itself.

## Core model

Bike-centric ([0002](decisions/0002-bike-centric-model.md)):

- A **bike** is an AXS system, identified by its hub's serial (the rear derailleur, `masterSerial` in the hub
  tables). Bikes have user-given names.
- **Components** belong to a bike. Their identity is the AXS serial; type, side, model and firmware come
  from the protocol.
- A **power meter** isn't in the AXS tables, so the user attaches it to a bike.
- You **add a bike** by waking it near the phone, confirming the detected system and naming it.
- Components from bikes you haven't added are ignored (they still appear in diagnostics).

## Battery presentation

([0003](decisions/0003-battery-presentation.md)) For each component, in priority order:

1. The component's **reported status**: good, low or charge now. These are the same states SRAM's app shows.
2. A **percentage**, only where the component reports one (the rear derailleur's broadcast, the power meter's
   Battery Level).
3. For packs without a reported percentage, our **≈ estimate** from voltage, always marked as an estimate.
4. **Voltage**, how it was read, and when, in the component detail.

When a component reports no status, we derive one with SRAM's thresholds: coin cells low at ≤ 2.4 V and
critical at ≤ 2.2 V; percentages low at ≤ 20 and critical at ≤ 5.

## Alerts

([0004](decisions/0004-alerts.md)) v1 notifies on:

- a component's status **changing to low or charge now**, once per change;
- a **pre-ride nudge**: the bike wakes and something is already low;
- **stale data**: the bike hasn't been seen for a while.

## Diagnostics

([0005](decisions/0005-diagnostics.md)) A Diagnostics area, reached from Settings and present in every build.
It holds raw adverts, nearby SRAM-looking devices, hub table dumps, ride tests and scan profiles. Component
screens link into it, but the main screens stay clean.

## More

- Product details: [0010](decisions/0010-product-details.md), Wear: [0013](decisions/0013-wear.md), foldables and tablets: [0014](decisions/0014-large-screens.md), onboarding, notifications and data:
  [0011](decisions/0011-onboarding-alerts-and-data.md)
- [Architecture](architecture.md) · [Design process](design-process.md) · [Roadmap](roadmap.md)
