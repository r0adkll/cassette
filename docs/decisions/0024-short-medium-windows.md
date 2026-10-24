# 0024: Short windows too narrow to split

Date: 2026-10-23 · Status: accepted

[0021](0021-phone-landscape.md) split every short window that wasn't compact width in two. It was
designed for a phone on its side, about 892 × 412 dp, where each pane gets around 445 dp. The iPhone
Duo closed and turned on its side is 678 × 466 pt, less its vertical bar (84 pt) and home indicator
(34 pt): split in two, each pane got about 297 pt, narrower than a phone. Names ran into "1 OF 2",
stat labels and serials wrapped mid-word, component sources were cut short. A small phone on its side
(an iPhone SE, 667 × 375) and a passport-shaped Android fold's outer screen on its side are squeezed
the same way.

## Size

These windows are compact height and **medium** width; a phone on its side is usually expanded
(840 dp and up). So the split (`ScreenLayout.Landscape`) now needs expanded width, and a short
medium-width window (`isShortMediumWindow()`) gets `ScreenLayout.Column`. Size classes only, as 0014
asks: no breakpoint of our own.

## Layout: the dashboard for the bike, the column for the rest

Compared on renders at both sizes (the Duo with its insets kept clear): today's split, the phone's
column, and the dashboard for the bike screen.

- **Bike:** the dashboard (0014): the bikes as chips in the top bar (Add bike and Settings in the
  vertical bar on the Duo), the status block with Refresh beside the status, the components as
  tiles, which open in its sheet. In the phone's column the status block alone filled the window and
  Refresh scrolled away. It also matches the open Duo, so folding keeps the same bike screen.
- **Everything else:** the phone's column, at the phone's width (480 dp) and centred, with footers
  that scroll (`FooterColumn`), as split screen already did. The labels and values have room again.

Rejected: the phone's column for the bike screen too (one component visible, Refresh at the foot),
and a narrower split with smaller type (still cramped, and a new layout to design and maintain).

The renders include this size (`*passport-landscape*`, 678 × 466 dp) in the bikes, onboarding and
Settings modules.
