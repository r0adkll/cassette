import CoreText
import SwiftUI
import WidgetKit
import XCTest

/// Draws every widget in each family and state, light and dark, at iPhone sizes (390 pt wide), and
/// attaches the PNGs (`scripts/ios-uitest` exports them as `w-*.png`), so the widgets can be checked
/// without adding them to a home screen. The system's own treatment (tinting, Lock Screen
/// vibrancy) isn't drawn: tinted widgets are shown in their layout only.
@MainActor
final class WidgetRenderTests: XCTestCase {
  private let date = Samples.now

  override class func setUp() {
    // The extension registers Barlow through UIAppFonts; a test bundle has to do it itself.
    for url in Bundle(for: WidgetRenderTests.self).urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [] {
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
  }

  func testSmall() {
    for dark in [false, true] {
      let look = Look(dark: dark)
      for tone in [Tone.good, .low, .critical] {
        let bike = Samples.road(tone)
        home("small-\(tone.rawValue.lowercased())", .small, dark) {
          SmallView(bike: bike, date: date, look: look)
        } background: {
          SmallBackground(tone: tone, look: look)
        }
      }
      home("small-stale", .small, dark) {
        SmallView(bike: Samples.gravel, date: date, look: look)
      } background: {
        SmallBackground(tone: .stale, look: look)
      }
      // The power meter preferred (decision 0027): shown while all is good, then the one to charge.
      for tone in [Tone.good, .low] {
        home("small-preferred-\(tone.rawValue.lowercased())", .small, dark) {
          SmallView(bike: Samples.road(tone), date: date, look: look, preferred: "POWER_METER")
        } background: {
          SmallBackground(tone: tone, look: look)
        }
      }
      home("small-empty", .small, dark) {
        NoBikeView(look: look)
      } background: {
        look.palette.surface
      }
    }
    // Tinted home screen (layout only) and StandBy, where the background goes (S2).
    let tinted = Look(dark: true, accented: true)
    home("small-tinted", .small, true) {
      SmallView(bike: Samples.road(), date: date, look: tinted)
    } background: {
      SmallBackground(tone: .low, look: tinted)
    }
    let standby = Look(dark: true, background: false)
    home("small-standby", .small, true, wall: .black) {
      SmallView(bike: Samples.road(), date: date, look: standby)
    } background: {
      Color.clear
    }
  }

  func testMediumAndLarge() {
    for dark in [false, true] {
      let look = Look(dark: dark)
      home("medium-low", .medium, dark) {
        MediumView(bike: Samples.road(), date: date, look: look)
      } background: {
        look.palette.surface
      }
      home("large-low", .large, dark) {
        LargeView(bike: Samples.road(), date: date, look: look)
      } background: {
        look.palette.surface
      }
      home("large-stale", .large, dark) {
        LargeView(bike: Samples.gravel, date: date, look: look)
      } background: {
        look.palette.surface
      }
    }
    let look = Look(dark: true)
    home("medium-critical", .medium, true) {
      MediumView(bike: Samples.road(.critical), date: date, look: look)
    } background: {
      look.palette.surface
    }
    // An MTB's eight components still fit.
    home("medium-mtb", .medium, true) {
      MediumView(bike: mtb, date: date, look: look)
    } background: {
      look.palette.surface
    }
    home("large-mtb", .large, true) {
      LargeView(bike: mtb, date: date, look: look)
    } background: {
      look.palette.surface
    }
  }

  func testAllBikes() {
    for dark in [false, true] {
      let look = Look(dark: dark)
      for large in [false, true] {
        home("all-bikes-\(large ? "large" : "medium")", large ? .large : .medium, dark) {
          AllBikesView(bikes: Samples.snapshot.bikes, date: date, look: look, large: large)
        } background: {
          look.palette.surface
        }
      }
    }
  }

  func testLockScreen() {
    for (name, bike) in [("low", Samples.road()), ("critical", Samples.road(.critical)), ("stale", Samples.gravel)] {
      lock("rectangular-\(name)", CGSize(width: 160, height: 72)) { RectangularView(bike: bike, date: date) }
      lock("circular-\(name)", CGSize(width: 72, height: 72)) { CircularView(bike: bike, date: date) }
      lock("inline-\(name)", CGSize(width: 248, height: 26)) { InlineView(bike: bike, date: date) }
    }
  }

  func testClockWords() {
    let gravel = Samples.gravel
    XCTAssertEqual(gravel.seen(at: date), "Seen 14 days ago")
    XCTAssertEqual(gravel.status(at: date.addingTimeInterval(2 * 86_400)).headline, "Not seen in 16 days.")
    XCTAssertEqual(Samples.road().seen(at: date), "Seen 2 min ago")
    XCTAssertEqual(agoText(30), "just now")
    XCTAssertEqual(agoText(3 * 3_600), "3 h ago")
    XCTAssertEqual(agoText(30 * 3_600), "yesterday")

    // The road bike goes stale on its own, at the time the app wrote down.
    var road = Samples.road()
    let staleAt = date.addingTimeInterval(3_600)
    road.staleAt = Int64(staleAt.timeIntervalSince1970 * 1_000)
    road.stale = Samples.gravel.current
    XCTAssertEqual(road.status(at: date).tone, .low)
    XCTAssertEqual(road.status(at: staleAt).tone, .stale)

    // Every minute for the first hour, then hourly; the stale moment too.
    let changes = clockChanges(after: date, seen: [road.lastSeenDate!], staleAt: [staleAt])
    XCTAssertEqual(changes.first, road.lastSeenDate!.addingTimeInterval(180))
    XCTAssertTrue(changes.contains(staleAt))
    XCTAssertTrue(changes.allSatisfy { $0 > date && $0 <= date.addingTimeInterval(86_400) })
  }

  /// The app's own output (`WidgetPublisher`, from the sample bike on a Simulator), trimmed to
  /// one component, with a field and a tone from some newer app.
  func testDecodesTheAppsSnapshot() throws {
    let json = """
      {"version":1,"writtenAt":1792372113875,"defaultBikeId":1,"newer":true,"bikes":[{"id":1,
      "name":"Sample bike","lastSeenAt":1792371981552,"current":{"tone":"Good","word":"GOOD",
      "headline":"All good.","summary":"Lowest: rear derailleur, 78%.","weakestId":1,"components":[
      {"id":1,"code":"RD","name":"Rear derailleur","value":"78%","level":0.78,"tone":"Glowing",
      "word":"GOOD"}]},"staleAt":1793581581553,"stale":{"tone":"Stale","word":"OLD",
      "headline":"Not seen in 14 days.","summary":"Readings below may be out of date.",
      "weakestId":1,"components":[]}}]}
      """
    let snapshot = try JSONDecoder().decode(WidgetSnapshot.self, from: Data(json.utf8))
    let bike = try XCTUnwrap(snapshot.bike(nil))
    XCTAssertEqual(bike.name, "Sample bike")
    XCTAssertEqual(bike.current.weakest?.code, "RD")
    XCTAssertEqual(bike.current.components.first?.tone, .unknown)
    XCTAssertEqual(bike.status(at: Date(epochMillis: 1_793_581_581_553)).tone, .stale)
    XCTAssertEqual(bike.link.absoluteString, "cassette://bike/1")
    XCTAssertNil(snapshot.bike(99).flatMap { $0.id == 99 ? $0 : nil })
  }

  // MARK: Helpers

  private enum Family {
    case small, medium, large

    var size: CGSize {
      switch self {
      case .small: CGSize(width: 158, height: 158)
      case .medium: CGSize(width: 338, height: 158)
      case .large: CGSize(width: 338, height: 354)
      }
    }
  }

  private var mtb: WidgetBike {
    let parts: [(String, String, String, Double, Tone)] = [
      ("RD", "Rear derailleur", "64%", 0.64, .good), ("DP", "Dropper post", "≈22%", 0.22, .low),
      ("FK", "Fork", "81%", 0.81, .good), ("SH", "Rear shock", "77%", 0.77, .good),
      ("L", "Left controller", "2.94 V", 0.91, .good), ("R", "Right controller", "2.92 V", 0.89, .good),
      ("PM", "Power meter", "70%", 0.70, .good), ("BB", "Blipbox", "2.99 V", 0.95, .good),
    ]
    let components = parts.enumerated().map { index, part in
      WidgetComponent(
        id: Int64(20 + index), code: part.0, name: part.1, value: part.2, level: part.3, tone: part.4,
        word: part.4 == .low ? "LOW" : "GOOD")
    }
    var bike = Samples.road()
    bike.id = 3
    bike.name = "Trail bike"
    bike.current = WidgetStatus(
      tone: .low, word: "LOW", headline: "Charge soon.", summary: "Dropper post at ≈22%.", weakestId: 21,
      components: components)
    return bike
  }

  /// A home-screen widget on a wallpaper, clipped to the widget's corners.
  private func home<Content: View, Background: View>(
    _ name: String, _ family: Family, _ dark: Bool, wall: Color? = nil,
    @ViewBuilder content: () -> Content, @ViewBuilder background: () -> Background
  ) {
    let widget = ZStack {
      background()
      content()
    }
    .frame(width: family.size.width, height: family.size.height)
    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    .padding(16)
    .background(wall ?? (dark ? Color(hex: 0x2E2F35) : Color(hex: 0xC9CBC6)))
    .environment(\.colorScheme, dark ? .dark : .light)
    attach("w-\(name)-\(dark ? "dark" : "light")", widget)
  }

  /// A Lock Screen widget, in the white the system draws it in over a dark wallpaper.
  private func lock<Content: View>(_ name: String, _ size: CGSize, @ViewBuilder content: () -> Content) {
    let widget = content()
      .frame(width: size.width, height: size.height)
      .foregroundStyle(.white)
      .padding(16)
      .background(Color(hex: 0x1F2A33))
      .environment(\.colorScheme, .dark)
    attach("w-lock-\(name)", widget)
  }

  private func attach(_ name: String, _ view: some View) {
    let renderer = ImageRenderer(content: view)
    renderer.scale = 3
    guard let png = renderer.uiImage?.pngData() else { return XCTFail("Couldn't render \(name)") }
    let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
