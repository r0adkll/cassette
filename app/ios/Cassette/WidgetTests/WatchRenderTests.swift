import CoreText
import SwiftUI
import WidgetKit
import XCTest

/// Draws the Apple Watch app's pages and its complications (iteration 17) at a 46 mm watch's size
/// (208 × 248 pt), so they can be checked without a watch or a watchOS Simulator. Exported with the
/// widgets as `w-watch-*.png`. The system's own parts (the clock, glass, a face's tinting, the
/// corner's curve) aren't drawn.
@MainActor
final class WatchRenderTests: XCTestCase {
  private let date = Samples.now
  private let screen = CGSize(width: 208, height: 248)

  override class func setUp() {
    for url in Bundle(for: WatchRenderTests.self).urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [] {
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
  }

  func testBikePages() {
    for (name, bike) in [
      ("good", Samples.road(.good)), ("low", Samples.road()), ("critical", Samples.road(.critical)),
      ("stale", Samples.gravel),
    ] {
      watch("page-\(name)") { BikePage(bike: bike, date: date) }
    }
    watch("page-refreshing") {
      BikePage(
        bike: Samples.road(), date: date, refresh: .scanning(until: date.addingTimeInterval(41)), countdownAt: date)
    }
    watch("page-unreachable") { BikePage(bike: Samples.road(), date: date, problem: .unreachable) }
    watch("page-not-ready") { BikePage(bike: Samples.gravel, date: date, problem: .notReady) }
  }

  func testComponentsAndEmpty() {
    watch("components-low") { ComponentsPage(bike: Samples.road(), date: date) }
    watch("components-stale") { ComponentsPage(bike: Samples.gravel, date: date) }
    watch("empty") { EmptyPage(synced: true) }
    watch("waiting") { EmptyPage(synced: false) }
  }

  func testComplications() {
    for (name, bike) in [
      ("good", Samples.road(.good)), ("low", Samples.road()), ("critical", Samples.road(.critical)),
      ("stale", Samples.gravel),
    ] {
      face("gauge-\(name)", CGSize(width: 50, height: 50)) { WeakestGaugeView(bike: bike, date: date) }
      face("circle-\(name)", CGSize(width: 50, height: 50)) { StatusCircleView(bike: bike, date: date) }
      face("rectangular-\(name)", CGSize(width: 178, height: 76)) { StatusRectangleView(bike: bike, date: date) }
      face("inline-\(name)", CGSize(width: 184, height: 20)) { StatusInlineView(bike: bike, date: date) }
      // The Smart Stack card, on the bike's colour.
      let card = StatusCardView(bike: bike, date: date)
        .padding(12)
        .frame(width: 184, height: 92)
        .background(Palette(dark: true).block(bike.status(at: date).tone).fill)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
      face("stack-\(name)", CGSize(width: 184, height: 92)) { card }
      // Battery with the power meter picked (decision 0027): itself, whatever the bike says.
      face("gauge-picked-\(name)", CGSize(width: 50, height: 50)) {
        WeakestGaugeView(bike: bike, date: date, picked: "POWER_METER")
      }
    }
  }

  func testPicks() {
    let low = Samples.road().status(at: date)
    XCTAssertEqual(low.picked("POWER_METER")?.code, "PM")
    XCTAssertEqual(low.featured("POWER_METER")?.code, "FD")
    XCTAssertEqual(Samples.road(.good).status(at: date).featured("POWER_METER")?.code, "PM")
    XCTAssertEqual(Samples.road(.good).status(at: date).featured("FORK")?.code, "RD")
    XCTAssertEqual(ComponentEntity.choices(Samples.road()).map(\.id).first, "weakest")
    XCTAssertEqual(ComponentEntity.choices(Samples.road()).count, 6)
    let shown = BatteryShown(status: low, picked: "POWER_METER")
    XCTAssertEqual(shown.tone, .good)
    XCTAssertEqual(shown.label, "PM")
    XCTAssertEqual(BatteryShown(status: Samples.gravel.status(at: date), picked: nil).label, "OLD")
  }

  func testRefreshRelevantWords() {
    XCTAssertEqual(PhoneProblem.unreachable.title, "Can't reach your iPhone.")
    XCTAssertEqual(Samples.road().status(at: date).weakest?.code, "FD")
  }

  // MARK: Helpers

  /// A watch screen: black, the display's corners.
  private func watch<Content: View>(_ name: String, @ViewBuilder content: () -> Content) {
    let view = content()
      .frame(width: screen.width, height: screen.height)
      .background(.black)
      .clipShape(RoundedRectangle(cornerRadius: 40, style: .continuous))
      .padding(12)
      .background(Color(hex: 0x3A3A40))
      .environment(\.colorScheme, .dark)
    attach("w-watch-\(name)", view)
  }

  /// A complication in full colour on a black face.
  private func face<Content: View>(_ name: String, _ size: CGSize, @ViewBuilder content: () -> Content) {
    let view = content()
      .frame(width: size.width, height: size.height)
      .foregroundStyle(.white)
      .padding(12)
      .background(.black)
      .environment(\.colorScheme, .dark)
    attach("w-watch-\(name)", view)
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
