import CoreText
import SwiftUI
import XCTest

/// Draws the refresh's Live Activity (iteration 16) through a refresh, as the widget test draws the
/// widgets: the Lock Screen card light and dark, StandBy, and the island's compact, minimal and
/// expanded looks, attached as `w-activity-*.png`. The system's own frames (the island's shape
/// around the camera, StandBy's scaling) are drawn here as plain black shapes, and the countdown
/// ring as it stands, since `ImageRenderer` can't draw the timer progress view the activity uses.
@MainActor
final class RefreshActivityRenderTests: XCTestCase {
  override class func setUp() {
    for url in Bundle(for: RefreshActivityRenderTests.self).urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [] {
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
  }

  func testLockScreen() {
    for dark in [false, true] {
      let palette = Palette(dark: dark)
      for (name, state) in RefreshSamples.all {
        let card = RefreshCard(state: state, palette: palette)
          .frame(width: 362)
          .background(palette.surface)
          .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
          .padding(14)
          .background(dark ? Color(hex: 0x1F2A33) : Color(hex: 0xD9D1C3))
          .environment(\.colorScheme, dark ? .dark : .light)
        attach("w-activity-lock-\(name)-\(dark ? "dark" : "light")", card)
      }
    }
  }

  func testStandBy() {
    for (name, state) in [("reading", RefreshSamples.reading4), ("done", RefreshSamples.done)] {
      let view = RefreshStandBy(state: state, palette: Palette(dark: true))
        .frame(width: 844, height: 390)
        .background(.black)
        .environment(\.colorScheme, .dark)
      attach("w-activity-standby-\(name)", view, scale: 2)
    }
  }

  func testDynamicIsland() {
    let palette = Palette(dark: true)
    for (name, state) in RefreshSamples.all {
      let compact = HStack {
        IslandMark(state: state, palette: palette, size: 22)
        Spacer()
        IslandCount(state: state, palette: palette)
      }
      .padding(.horizontal, 12)
      .frame(width: 250, height: 37)
      .background(Capsule().fill(.black))
      .padding(10)
      .background(Color(hex: 0x3A4652))
      attach("w-activity-compact-\(name)", compact)

      let minimal = IslandMark(state: state, palette: palette, size: 22)
        .frame(width: 37, height: 37)
        .background(Circle().fill(.black))
        .padding(10)
        .background(Color(hex: 0x3A4652))
      attach("w-activity-minimal-\(name)", minimal)

      let expanded = VStack(spacing: 8) {
        HStack(alignment: .top) {
          IslandExpandedLeading(state: state, palette: palette)
          Spacer(minLength: 8)
          RefreshDial(state: state, palette: palette, size: 64, line: 5, time: 20, count: nil)
            .padding(.trailing, 4)
        }
        RefreshColumns(state: state, palette: palette, code: 11, spacing: 6)
          .frame(height: 38)
          .padding(.horizontal, 4)
      }
      .padding(.horizontal, 18)
      .padding(.vertical, 14)
      .frame(width: 371, height: 160)
      .background(RoundedRectangle(cornerRadius: 44, style: .continuous).fill(.black))
      .padding(10)
      .background(Color(hex: 0x3A4652))
      .environment(\.colorScheme, .dark)
      attach("w-activity-expanded-\(name)", expanded)
    }
  }

  func testDecodesTheAppsState() throws {
    // `RefreshActivityCodec`'s output for the road bike two components in, with a newer field.
    let json = """
      {"startedAt":1792400000000,"phase":"Reading","endsAt":1792400060000,"totalSeconds":60,
      "bikeId":1,"label":"ROAD BIKE · READING","line":"Waiting for L, R and PM.",
      "headline":"2 of 5.","detail":"Waiting for L, R and PM.","tone":"Low","heard":2,
      "components":[{"code":"FD","value":"≈11%","level":0.11,"tone":"Low","answered":true}],
      "ended":false,"dismissAfterSeconds":300,"newer":1}
      """
    let state = try JSONDecoder().decode(RefreshState.self, from: Data(json.utf8))
    XCTAssertEqual(state.phase, .reading)
    XCTAssertTrue(state.scanning)
    XCTAssertEqual(state.span.lowerBound, Date(epochMillis: 1_792_400_000_000))
    XCTAssertEqual(state.link.absoluteString, "cassette://bike/1")
    XCTAssertEqual(state.components.first?.value, "≈11%")
  }

  private func attach(_ name: String, _ view: some View, scale: CGFloat = 3) {
    let renderer = ImageRenderer(content: view.environment(\.staticTimers, true))
    renderer.scale = scale
    guard let png = renderer.uiImage?.pngData() else { return XCTFail("Couldn't render \(name)") }
    let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}

/// The road bike through a refresh, as iteration 16 drew it, timed from now so the countdown reads
/// as it would (a timer text draws against the real clock).
enum RefreshSamples {
  private static let parts: [(String, String, Double, String)] = [
    ("FD", "≈11%", 0.11, "Low"), ("RD", "78%", 0.78, "Good"), ("L", "2.91", 0.90, "Good"),
    ("R", "2.89", 0.88, "Good"), ("PM", "85%", 0.85, "Good"),
  ]

  private static func state(
    _ phase: RefreshPhase, secondsIn: Double, heard: [String], label: String, line: String,
    headline: String, detail: String? = nil
  ) -> RefreshState {
    let start = Date.now.addingTimeInterval(-secondsIn)
    let millis = Int64(start.timeIntervalSince1970 * 1_000)
    return RefreshState(
      startedAt: millis, phase: phase, endsAt: millis + 60_000, totalSeconds: 60, bikeId: 1,
      label: label, line: line, headline: headline, detail: detail ?? line, tone: "Low",
      heard: heard.count,
      components: parts.map {
        RefreshComponent(code: $0.0, value: $0.1, level: $0.2, tone: $0.3, answered: heard.contains($0.0))
      },
      ended: ![.listening, .reading].contains(phase), dismissAfterSeconds: 300)
  }

  static let listening = state(
    .listening, secondsIn: 8, heard: [], label: "ROAD BIKE · LISTENING",
    line: "Shift once to wake the bike.", headline: "Listening.")
  static let reading2 = state(
    .reading, secondsIn: 19, heard: ["FD", "RD"], label: "ROAD BIKE · READING",
    line: "Waiting for L, R and PM.", headline: "2 of 5.")
  static let reading4 = state(
    .reading, secondsIn: 27, heard: ["FD", "RD", "L", "R"], label: "ROAD BIKE · READING",
    line: "Waiting for PM. Spin the crank.", headline: "4 of 5.")
  static let done = state(
    .done, secondsIn: 31, heard: ["FD", "RD", "L", "R", "PM"], label: "ROAD BIKE · READ IN 31 S",
    line: "Charge soon. Front derailleur at ≈11%.", headline: "Charge soon.",
    detail: "Front derailleur at ≈11%.")
  static let partial = state(
    .partial, secondsIn: 60, heard: ["FD", "RD", "L", "R"], label: "ROAD BIKE · 4 OF 5 READ",
    line: "No answer from PM.", headline: "4 of 5.")
  static let notReady = state(
    .notReady, secondsIn: 22, heard: [], label: "ROAD BIKE · NOT READY",
    line: "Bluetooth is off. Turn it on, then refresh.", headline: "Bluetooth is off.",
    detail: "Turn it on, then refresh.")

  static let all: [(String, RefreshState)] = [
    ("listening", listening), ("reading-2", reading2), ("reading-4", reading4), ("done", done),
    ("partial", partial), ("not-ready", notReady),
  ]
}
