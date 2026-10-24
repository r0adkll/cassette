import XCTest

/// Walks the app on a Simulator without Bluetooth, from a fresh install, saving a screenshot at
/// each step (`scripts/ios-uitest` exports them). Compose's semantics reach XCUITest as
/// accessibility labels, so elements are found by their text or content description.
final class CassetteUITests: XCTestCase {
  private let app = XCUIApplication()

  override func setUp() {
    continueAfterFailure = false
    app.launch()
  }

  func testFirstRunToSampleBike() throws {
    try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "The phone walk-through")
    // Welcome (decision 0017: no widgets or Quick Settings on iOS yet).
    expect("The bike screen and notifications.")
    shot("01-welcome")
    tap("Get started")

    // Permissions: iOS calls it Bluetooth. Notifications stay unasked, for the Settings row below.
    expect("Bluetooth")
    shot("02-permissions")
    tap("Continue")

    // Add bike: no Bluetooth on the Simulator.
    expect("Wake your bike.")
    shot("03-add-bike")
    button("Close").tap()

    // The empty bike screen, then Settings.
    expect("No bike yet.")
    tap("Settings")
    expect("Settings.")
    shot("04-settings")

    tap("Notifications")
    expect("Notifications are off")
    shot("05-notifications")
    button("Back").tap()

    tap("Diagnostics")
    expect("scan (Core Bluetooth, FE51)")
    shot("06-diagnostics")
    // G1: the content scrolls under the glass back button (iOS 26 glass, or the frosted fallback).
    app.swipeUp(velocity: .slow)
    shot("06b-diagnostics-scrolled")
    tap("Load sample bike", scrolling: true)
    shot("07-sample-loaded")
    button("Back").tap()
    expect("Settings.")
    button("Back").tap()

    // The sample bike's screen (its title, which Diagnostics doesn't show).
    expect("Sample bike")
    XCTAssertFalse(element("Share diagnostics").exists, "Still on Diagnostics")
    shot("08-bike")
    tap("Front derailleur")
    expect("Firmware")
    shot("09-component")
    button("Back").tap()

    switchBikes()
    demoRefreshLiveActivity()
  }

  /// Two more bikes, made up in Diagnostics, then the bikes sheet (decision 0026) from the bikes
  /// pill, or from the vertical bar's Bikes on an iPhone Duo; both are labelled "3 bikes, …".
  private func switchBikes() {
    tap("Settings")
    tap("Diagnostics")
    expect("scan (Core Bluetooth, FE51)")
    tap("Add a random bike", scrolling: true)
    tap("Add a random bike")
    button("Back").tap()
    expect("Settings.")
    button("Back").tap()

    expect("Sample bike")
    shot("10-bikes")
    tap("3 bikes")
    expect("Your bikes")
    shot("11-bikes-sheet")
    button("Close").tap()
    XCTAssertTrue(
      element("Your bikes").waitForNonExistence(timeout: 5), "The bikes sheet stayed open")
  }

  /// An iPad in landscape is a large window: the bike rail beside the dashboard (decision 0014).
  func testTabletRail() throws {
    try XCTSkipIf(UIDevice.current.userInterfaceIdiom != .pad, "The tablet walk-through")
    XCUIDevice.shared.orientation = .landscapeLeft

    // Wide onboarding: the welcome and permissions split, add bike with its found pane.
    expect("Never ride")
    shot("t01-welcome")
    tap("Get started")
    expect("Bluetooth")
    tap("Continue")
    expect("Wake your bike.")
    shot("t02-add-bike")
    button("Close").tap()

    // The wide empty screen, then Settings with Diagnostics in its pane.
    expect("No bike yet.")
    shot("t03-empty")
    tap("Settings")
    tap("Diagnostics")
    expect("scan (Core Bluetooth, FE51)")
    tap("Load sample bike", scrolling: true)
    shot("t04-settings-diagnostics")
    button("Back").tap()

    // The rail: the bikes, Add bike, and Settings at its foot.
    expect("Bikes")
    expect("Sample bike")
    shot("t05-rail")

    // The component sheet over the dashboard, with the rail still in reach.
    tap("Front derailleur")
    expect("Firmware")
    shot("t06-sheet")
    button("Close").tap()

    // Settings from the rail.
    tap("Settings")
    expect("Settings.")
    shot("t07-rail-settings")
  }

  /// The refresh's Live Activity (iteration 16) through Diagnostics' demo refresh, where the
  /// sample bike answers without Bluetooth: the Dynamic Island, then the Lock Screen to Done. The
  /// end of the phone walk-through, from the bike screen (tests share the app's data, so it can't
  /// be a test of its own).
  private func demoRefreshLiveActivity() {
    tap("Settings")
    tap("Diagnostics")
    expect("scan (Core Bluetooth, FE51)")
    tap("Demo refresh", scrolling: true)

    // Leave the app: the activity moves to the Dynamic Island.
    XCUIDevice.shared.press(.home)
    sleep(3)
    shot("a01-island-listening")
    sleep(6)
    shot("a02-island-reading")
    // A long press on the island expands it.
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.025))
      .press(forDuration: 1.2)
    sleep(1)
    shot("a03-island-expanded")
    XCUIDevice.shared.press(.home)

    // The Lock Screen, while it reads and once it's done (the meter answers at 30 s).
    let lock = NSSelectorFromString("pressLockButton")
    guard XCUIDevice.shared.responds(to: lock) else { return }
    XCUIDevice.shared.perform(lock)
    sleep(3)
    shot("a04-lock-reading")
    sleep(22)
    shot("a05-lock-done")
  }

  // MARK: Helpers

  /// A button by its exact label: "Back" would otherwise match "background" in Diagnostics.
  private func button(_ label: String, file: StaticString = #filePath, line: UInt = #line)
    -> XCUIElement
  {
    let match = app.buttons.matching(NSPredicate(format: "label ==[c] %@", label)).firstMatch
    XCTAssertTrue(match.waitForExistence(timeout: 10), "Expected button \"\(label)\"", file: file, line: line)
    return match
  }

  /// The first element whose label starts with [text], ignoring case: Compose merges a row's
  /// texts into one label ("Notifications, 3 of 3 on") and some buttons are upper-cased.
  private func element(_ text: String) -> XCUIElement {
    app.descendants(matching: .any)
      .matching(NSPredicate(format: "label BEGINSWITH[c] %@", text))
      .firstMatch
  }

  private func expect(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertTrue(
      element(text).waitForExistence(timeout: 15), "Expected \"\(text)\"", file: file, line: line)
  }

  private func tap(
    _ text: String, scrolling: Bool = false, file: StaticString = #filePath, line: UInt = #line
  ) {
    let target = element(text)
    if scrolling {
      // Compose lists are lazy: a row below the fold doesn't exist until it's scrolled to.
      var swipes = 0
      while !(target.exists && target.isHittable) && swipes < 8 {
        app.swipeUp()
        swipes += 1
      }
      // A tap on a list that's still scrolling only stops it, and an iPhone Duo's list keeps
      // scrolling, too slowly to see, for several seconds after a swipe.
      sleep(10)
    }
    expect(text, file: file, line: line)
    target.tap()
  }

  private func shot(_ name: String) {
    // The screen's capture, not the app's: the app's comes out offset and cropped in landscape.
    let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
