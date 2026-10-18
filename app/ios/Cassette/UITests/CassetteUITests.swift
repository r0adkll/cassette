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

  func testFirstRunToSampleBike() {
    // Welcome (decision 0016: no widgets or Quick Settings on iOS yet).
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
    tap("Close")

    // The empty bike screen, then Settings.
    expect("No bike yet.")
    tap("Settings")
    expect("Settings.")
    shot("04-settings")

    tap("Notifications")
    expect("Notifications are off")
    shot("05-notifications")
    tap("Back")

    tap("Diagnostics")
    expect("scan (Core Bluetooth, FE51)")
    shot("06-diagnostics")
    tap("Load sample bike", scrolling: true)
    shot("07-sample-loaded")
    tap("Back")
    tap("Back")

    // The sample bike's screen.
    expect("Front derailleur")
    shot("08-bike")
    tap("Front derailleur")
    expect("Firmware")
    shot("09-component")
  }

  // MARK: Helpers

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
    expect(text, file: file, line: line)
    if scrolling {
      var swipes = 0
      while !target.isHittable && swipes < 6 {
        app.swipeUp()
        swipes += 1
      }
    }
    target.tap()
  }

  private func shot(_ name: String) {
    let attachment = XCTAttachment(screenshot: app.screenshot())
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
