# 0021: Phones in landscape

Date: 2026-10-20 · Status: accepted

[0014](0014-large-screens.md) kept the phone layout for a phone on its side: wide, but too short for
the dashboard. That left one phone-width column in the middle of a wide screen, and the screens with
a fixed footer squeezed their middle into a sliver. A phone on its side now splits in two. Mockups:
https://claude.ai/artifact/8UmxnxNnrNH6NFWBCjkqxG (private; 892 × 412 dp, the sample bike). The look
is unchanged ([0012](0012-visual-direction.md)): these are layouts of the same components.

## Size

`ScreenLayout.Landscape` is compact height with at least medium width (a phone on its side). A
window that is compact both ways (split screen) keeps the phone's column, and its screens with a
footer scroll the footer with the rest (`FooterColumn`).

## Layout: two panes (direction A)

`SplitPanes` down the middle, with a divider; each pane scrolls on its own and pads only by its own
side's bars and the cutout. The left pane holds what the screen says and its one action, at the foot
when everything fits; the right holds what it lists.

- **Bike:** the wordmark (Add bike and Settings float over it), the name and the status block on
  the left, with Refresh fixed at its foot (a long status title scrolls above it); the components
  and any tip on the right.
- **Component:** back, the name and the status block with the value on the left; the stats and the
  history chart on the right. Components open as their own screen, as on the phone.
- **Add bike:** every step splits: the header, title, what to do and the step's buttons on the left;
  what it shows (the listening pulse, centred; the hub's components; the name and type; the power
  meters; the permission) on the right.
- **Welcome:** the red poster on the left, the wordmark at its top and the promise at its foot; the
  three points, Get started and the unofficial notice on the right.
- **Permissions:** the ask on the left in the panel colour, the two permissions and Continue on the
  right.
- **Settings and a bike's page:** the wide layouts (0014): the list beside the open category, and
  the bike beside its components. No bike yet: the wide empty screen. The open category's title
  sits level with the list's back button rather than with the list's title, so the category starts
  higher.
- Screens not listed (Diagnostics, licences, hub tables, ride tests) kept the capped phone column;
  [0022](0022-every-screen-every-size.md) splits them in two as well.

Rejected: B · cluster (a status band with Refresh inside, the components as five tiles across,
the whole bike without scrolling). A was preferred; it keeps the phone's component rows, source
lines included.

The renders include a phone on its side (`*landscape*`, 892 × 412 dp) in the bikes, onboarding and
Settings modules.
