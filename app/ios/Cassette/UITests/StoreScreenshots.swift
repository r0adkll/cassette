import XCTest

/// The App Store screenshots (fastlane/README.md): the sample bike, renamed as a rider would name
/// theirs, on an iPhone (a closed iPhone Duo too) or an iPad in landscape. Only runs when asked,
/// from a fresh install:
///
///   IOS_UITEST_ONLY=CassetteUITests/StoreScreenshots TEST_RUNNER_STORE_SCREENSHOTS=1 \
///     IOS_SIMULATOR=<udid> scripts/ios-uitest
final class StoreScreenshots: XCTestCase {
  private let app = XCUIApplication()
  private var pad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

  override func setUpWithError() throws {
    try XCTSkipUnless(
      ProcessInfo.processInfo.environment["STORE_SCREENSHOTS"] == "1", "Store screenshots only")
    continueAfterFailure = false
    app.launch()
    if UIDevice.current.userInterfaceIdiom == .pad {
      XCUIDevice.shared.orientation = .landscapeLeft
      sleep(2)
    }
  }

  func testStoreScreenshots() throws {
    // Through onboarding without a bike, then the sample from Diagnostics.
    tap("Get started")
    expect("Bluetooth")
    tap("Continue")
    expect("Wake your bike.")
    shot("s04-add-bike")
    tap("Close")
    expect("No bike yet.")
    tap("Settings")
    tap("Diagnostics")
    expect("scan (Core Bluetooth, FE51)")
    tap("Load sample bike", scrolling: true)
    if !pad {
      button("Back").tap()
      expect("Settings.")
    }

    // Renamed in its Settings page, as a rider would.
    tap("Bikes")
    tap("Sample bike")
    // Compose's field reaches XCUITest as a text view. Tapped at its end, so deleting clears it.
    let field = app.textViews.firstMatch
    XCTAssertTrue(field.waitForExistence(timeout: 10), "Expected the name field")
    field.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
    field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 20))
    field.typeText("Road bike")
    expect("Road bike.")
    button("Back").tap()
    if !pad {
      button("Back").tap()
      expect("Settings.")
    }
    shot("s05-settings")
    button("Back").tap()

    expect("Road bike")
    shot("s01-bike")
    tap("Rear derailleur")
    expect("Firmware")
    shot("s02-component")
    button(pad ? "Close" : "Back").tap()
  }

  // MARK: Helpers, as in CassetteUITests

  private func button(_ label: String, file: StaticString = #filePath, line: UInt = #line)
    -> XCUIElement
  {
    let match = app.buttons.matching(NSPredicate(format: "label ==[c] %@", label)).firstMatch
    XCTAssertTrue(match.waitForExistence(timeout: 10), "Expected button \"\(label)\"", file: file, line: line)
    return match
  }

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
    let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
