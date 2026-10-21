# Store listings

The Play and App Store listings, for fastlane's `supply` and `deliver`. `fastlane android listing` and
`fastlane ios listing` upload the text and images only (see `Fastfile`). `fastlane ios privacy
username:<apple id>` uploads the App Store privacy answers from `app_privacy_details.json` (an Apple ID
login: Apple has no API key route for them).

| Path | What |
|---|---|
| `metadata/android/en-US/` | Play: title, short and full description, `changelogs/`, and `images/` (icon, feature graphic, phone, 7", 10" and Wear screenshots) |
| `metadata/ios/` | App Store: `en-US/` text and URLs, categories, `review_information/notes.txt` |
| `screenshots/ios/en-US/` | App Store screenshots: iPhone 6.9" and 6.3", iPad 13", Apple Watch (deliver sorts them by pixel size) |
| `assets/` | Sources: the caption and feature graphic pages, Android renders (`screens/`) and device captures (`captures/`) |

`scripts/store-assets` rebuilds every image from `assets/`. The captions are in that script.

The listings say Cassette is unofficial and not affiliated with SRAM, and the images show no SRAM names or
marks (the add-bike step that shows the hub's maker and serial is left out). The bike is the sample
(Diagnostics → Developer → Load sample bike), renamed "Road bike".

## Images

| Set | Size | Source |
|---|---|---|
| Play icon | 512 × 512 | `scripts/generate-ios-icon.py`, from the Android launcher icon |
| Feature graphic | 1024 × 500 | `assets/feature-graphic.html` |
| Play phone | 1080 × 1920, captioned | Screens: `StoreRenderTest` in `features/bikes/ui` (rendered by the script). Widgets: an emulator capture of the debug build's `WidgetPreviewActivity`, `assets/captures/android-widgets/`. Wear: the Wear captures. Both laid out by `assets/surfaces.html` |
| Play 7" and 10" | 1080 × 1920, 2560 × 1440, 1440 × 2560 | `StoreRenderTest` |
| Wear | 454 × 454 | Emulator captures, `assets/captures/wear/` |
| iPhone | 1320 × 2868 and 1206 × 2622, captioned | The bike and component: Simulator captures, `assets/captures/iphone-6.9/` and `iphone-6.3/`. Widgets and Apple Watch: the widget test's renders (`assets/captures/widgets/`) laid out by `assets/surfaces.html` |
| iPad 13" | 2064 × 2752 | Simulator captures, `assets/captures/ipad-13/` |
| Apple Watch | 416 × 496 (Series 10–12, 46 mm) | Simulator capture, `assets/captures/watch/` |

Play's rules: no device frames, Wear shots only the app's UI (no text, frames or masks), taglines under
20% of a phone shot, no alpha. The App Store takes no alpha either; the script flattens everything.

## Retaking device captures

All in dark mode with a clean status bar, on Simulators of your own:

    xcrun simctl ui <udid> appearance dark
    xcrun simctl status_bar <udid> override --time 9:41 --dataNetwork wifi --wifiMode active \
      --wifiBars 3 --cellularMode active --cellularBars 4 --batteryState discharging --batteryLevel 100

- iPhone and iPad: the `StoreScreenshots` UI test on an iPhone 17 Pro Max, an iPhone 17 Pro and an
  iPad Pro 13-inch (M5). It loads the sample bike, renames it "Road bike" and shoots the bike (`s01`)
  and a component (`s02`); copy those from `app/ios/Cassette/build/uitest-screens/`.

      IOS_UITEST_ONLY=CassetteUITests/StoreScreenshots TEST_RUNNER_STORE_SCREENSHOTS=1 \
        IOS_SIMULATOR=<udid> scripts/ios-uitest

  The iPad stays in portrait: the rotation the test asks for doesn't happen on the iPadOS 26
  Simulator.
- Apple Watch: a Series 11 (46 mm) Simulator paired (`xcrun simctl pair`) to the iPhone after its
  run, the `CassetteWatch` scheme installed, the phone app then the watch app launched;
  `xcrun simctl io <watch> screenshot`.
- Widgets: `IOS_SIMULATOR=<udid> scripts/ios-uitest` renders them to `app/ios/Cassette/build/uitest-screens/w-*.png`.
- Wear: the debug build on a large round Wear emulator with sample data (CLAUDE.md, Wear), and the tile;
  `adb exec-out screencap -p`.
- Android widgets: the debug build on a phone emulator in dark mode (`adb shell cmd uimode night yes`),
  the sample bike loaded, renamed "Road bike" and given a low front derailleur (Diagnostics →
  Developer), then `WidgetPreviewActivity` (CLAUDE.md, Widgets). `scripts/store-assets` crops the
  widgets by their boxes in `low-1.png`; update those if the capture changes. The Quick Settings tile
  is left out of the listing for now.

## Set by hand

Not in fastlane's metadata: Play's category (Tools), contact email and website, the App Store's age
rating, copyright and review contact, both content rating questionnaires, Play's App access note (the
same steps as `metadata/ios/review_information/notes.txt`) and Play's Data safety form (below).

## Privacy answers

Decision 0020: crash reports are the one thing collected. The privacy policy
(thescavengers.software/cassette/privacy) says the same; change all three together.

App Store (`app_privacy_details.json`): Crash Data and Other Diagnostic Data, purpose App
Functionality, not linked to the person, not used for tracking. The app's `PrivacyInfo.xcprivacy`
declares the same two.

Google Play Data safety (App content → Data safety), by hand:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (Crashlytics uses HTTPS) |
| Which account creation methods does your app support? | My app does not allow users to create an account |
| Do you provide a way for users to request that their data is deleted? | No: reports aren't linked to a person; Crashlytics deletes them after 90 days |
| Data types | App info and performance → Crash logs; App info and performance → Diagnostics; Device or other IDs (Crashlytics' installation id) |
| Each type: collected / shared | Collected, not shared (Google processes it for us as Firebase's processor) |
| Each type: processed ephemerally? | No |
| Each type: required or optional? | Optional: users can turn it off (Settings → About → Send crash reports) |
| Each type: purpose | App functionality |
