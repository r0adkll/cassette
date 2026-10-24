# 0025: Size classes for Apple's devices

Date: 2026-10-23 · Status: accepted

Layouts follow Material's window size classes ([0014](0014-large-screens.md)), and the bike rail
waits for large width (1200 dp) so the open Pixel Fold (expanded, 852 dp) keeps its chips. On an
iPad Air (820 × 1180 pt), checked on a device, that left landscape without the rail: at 1180 pt it is
expanded, the Fold's class. So were every 11-inch iPad and the iPad mini on their sides; only the
13-inch got the rail. Android's layouts felt right and stay as they are.

## iOS gets its own breakpoints

`androidx.window` supports breakpoint sets of an app's own: `WindowSizeClass(minWidthDp,
minHeightDp)` is public, and `Set<WindowSizeClass>.computeWindowSizeClass` picks from any set.
Material's adaptive library (`currentWindowAdaptiveInfoV2`) computes only Material's, so the app
measures the window as it does and picks from the platform's set (`SizeClasses`, `:common:compose`).
Nothing is forked or overridden.

- **Android, and the renders:** `SizeClasses.Material`, `BREAKPOINTS_V2` unchanged.
- **iOS:** `SizeClasses.Apple`, the same except that large width starts at 1100 pt.

| Window (pt) | Material | Apple |
|---|---|---|
| iPad mini, iPad, iPad Air 11", iPad Pro 11" on their sides (1133 to 1210 wide) | Dashboard | Dashboard with rail |
| iPad Pro 13" on its side (1376) | Dashboard with rail | Dashboard with rail |
| iPad Pro 13" upright (1032) | Dashboard | Dashboard |
| Every other iPad upright (744 to 834) | Dashboard | Dashboard |
| iPhone Duo open, either way up (951 × 669) | Dashboard | Dashboard |
| iPhones, the Duo closed | as Android's phones | as Android's phones |

Compact, medium and expanded width and the height classes already fit Apple's devices: iPhones
upright are compact, iPhones on their sides are compact height (a small one too narrow to split,
[0024](0024-short-medium-windows.md)), iPads upright are medium. Only the step to the rail moved,
between the 13-inch iPad upright and the smallest iPad on its side, which also keeps the open Duo
on the dashboard approved for it.

Rejected: the rail from expanded width on both platforms (it puts the rail on the open Fold, which
0014 had rejected), and the rail at expanded width only when the window has no hinge (a device test
dressed as a size class, and the open Duo reports no fold while flat).

The renders include an 11-inch iPad on its side with iOS's classes (`bike-ipad-landscape`, 1180 ×
820), and `ScreenLayoutTest` checks both sets.
