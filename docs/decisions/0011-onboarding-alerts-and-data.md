# 0011: Onboarding, notifications and data

Date: 2026-10-16 · Status: accepted

## First run

1. Welcome: what Cassette does, and "Unofficial, not affiliated with SRAM".
2. Permissions, one explainer each: Nearby devices (scan + connect), Notifications.
3. Add the first bike ([0010](0010-product-details.md)), including the system companion-device dialog that
   links the bike's hub.
4. Done: background capture on, with a tip to wake the bike now and then.

The widget and Quick Settings tile are offered later as tips on the bike screen, not as onboarding steps.
The battery-optimisation exemption is only requested if the fallback scan needs it.

## Notification channels

| Channel | Importance | Content |
|---|---|---|
| Battery alerts | default | A component's status changed to low or charge now; one per change. |
| Pre-ride | high (heads-up) | The bike just woke and something is low. At most every 12 hours per bike; "Snooze until tomorrow" action. |
| Reminders | low | Bike not seen for 14 days, then 30. |
| Capture | min | Only while a foreground service reads the hub, if one is needed. |

Tapping a notification deep-links to the bike or component.

## Unknown hardware

Unknown types, models or frame shapes appear on the bike as "Unknown AXS component" with whatever we could
decode, never hidden. Diagnostics has "Share diagnostics": a zip of recent raw adverts, hub table dumps and
ride-test logs, shared through the system share sheet. Nothing is uploaded automatically.

## Backup

Android Auto Backup covers the database (bikes, components, history), bike photos and settings. Diagnostics
captures and ride logs are excluded. After a restore, companion associations are recreated the first time
each bike is seen.
