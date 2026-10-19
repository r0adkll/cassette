import SwiftUI
import WidgetKit

/// The widgets' palette: Android's `WidgetColors` and the app's status colours (decision 0012).
struct Palette {
  var dark: Bool

  var surface: Color { dark ? Color(hex: 0x0E0E10) : Color(hex: 0xF7F7F5) }
  var panel: Color { dark ? Color(hex: 0x141416) : .white }
  var line: Color { dark ? Color(hex: 0x2A2A2F) : Color(hex: 0xD6D6D2) }
  var text: Color { dark ? Color(hex: 0xF4F4F2) : Color(hex: 0x111113) }
  var muted: Color { dark ? Color(hex: 0xA2A2A8) : Color(hex: 0x55555B) }
  var track: Color { dark ? Color(hex: 0x1E1E22) : Color(hex: 0xE6E6E2) }

  func status(_ tone: Tone) -> Color {
    switch tone {
    case .good: dark ? Color(hex: 0x4ADE80) : Color(hex: 0x1E8E3E)
    case .low: dark ? Color(hex: 0xFFB020) : Color(hex: 0xB86E00)
    case .critical: .cassetteRed
    case .stale, .unknown: dark ? Color(hex: 0x6A6A70) : Color(hex: 0x9A9A96)
    }
  }

  /// The status block's fill and content; an old reading is a panel with an outline.
  func block(_ tone: Tone) -> (fill: Color, content: Color, outline: Color?) {
    switch tone {
    case .good: (Color(hex: 0x2DB36A), Color(hex: 0x03200F), nil)
    case .low: (Color(hex: 0xFFB020), Color(hex: 0x1A1200), nil)
    case .critical: (.cassetteRed, .white, nil)
    case .stale, .unknown: (panel, muted, line)
    }
  }

  /// Thick-bar fills: a tint of the status colour, readable under text in both themes.
  func thickFill(_ tone: Tone) -> Color {
    switch tone {
    case .good: dark ? Color(hex: 0x1D5C38) : Color(hex: 0xBFE8CC)
    case .low: dark ? Color(hex: 0x6B4800) : Color(hex: 0xFFE0A3)
    case .critical: dark ? Color(hex: 0x8A0019) : Color(hex: 0xFFC7CE)
    case .stale, .unknown: dark ? Color(hex: 0x2A2A2F) : Color(hex: 0xE2E2DE)
    }
  }
}

/// How the system is drawing the widget, read from the environment by the entry views and passed
/// down, so the render tests can draw every case.
struct Look {
  var palette: Palette
  /// Tinted or clear home screens: the system recolours everything, accented parts in the tint.
  var accented = false
  /// False in StandBy (and wherever else iOS removes the background).
  var background = true

  init(dark: Bool, accented: Bool = false, background: Bool = true) {
    palette = Palette(dark: dark)
    self.accented = accented
    self.background = background
  }
}

extension Color {
  static let cassetteRed = Color(hex: 0xE4002B)

  init(hex: UInt32) {
    self.init(
      .sRGB,
      red: Double((hex >> 16) & 0xFF) / 255,
      green: Double((hex >> 8) & 0xFF) / 255,
      blue: Double(hex & 0xFF) / 255)
  }
}

extension Font {
  /// Barlow Condensed Bold: display text, numerals and small uppercase labels.
  static func condensed(_ size: CGFloat) -> Font { .custom("BarlowCondensed-Bold", fixedSize: size) }

  /// Barlow for body text.
  static func barlow(_ size: CGFloat, semibold: Bool = false) -> Font {
    .custom(semibold ? "Barlow-SemiBold" : "Barlow-Medium", fixedSize: size)
  }
}

/// The extension's own bundle, which also holds the icons when the render tests draw the views.
private final class BundleToken {}

extension Bundle {
  static let widgets = Bundle(for: BundleToken.self)
}

/// A Lucide icon from the asset catalog, tinted like text.
struct Icon: View {
  var name: String
  var size: CGFloat

  var body: some View {
    Image(name, bundle: .widgets)
      .renderingMode(.template)
      .resizable()
      .frame(width: size, height: size)
      .accessibilityHidden(true)
  }
}

/// A rounded track with a fill, horizontal or vertical (from the bottom).
struct Bar: View {
  var fraction: Double
  var track: Color
  var fill: Color
  var radius: CGFloat
  var vertical = false

  var body: some View {
    GeometryReader { size in
      ZStack(alignment: vertical ? .bottom : .leading) {
        RoundedRectangle(cornerRadius: radius).fill(track)
        RoundedRectangle(cornerRadius: radius)
          .fill(fill)
          .frame(
            width: vertical ? size.size.width : size.size.width * fraction,
            height: vertical ? size.size.height * fraction : size.size.height)
          // Tinted and clear home screens draw the fill in the tint.
          .widgetAccentable()
      }
    }
  }
}

/// The road bike and an old gravel bike, as iteration 15 drew them: the gallery's preview, the
/// placeholder, and the render tests.
enum Samples {
  static let now = Date(timeIntervalSince1970: 1_792_400_000)

  private static func component(
    _ id: Int64, _ code: String, _ name: String, _ value: String, _ level: Double, _ tone: Tone
  ) -> WidgetComponent {
    let words: [Tone: String] = [
      .good: "GOOD", .low: "LOW", .critical: "CHARGE NOW", .stale: "OLD", .unknown: "UNKNOWN",
    ]
    return WidgetComponent(
      id: id, code: code, name: name, value: value, level: level, tone: tone, word: words[tone]!)
  }

  static func road(_ tone: Tone = .low) -> WidgetBike {
    let fd: WidgetComponent
    let status: (String, String, String)
    switch tone {
    case .good:
      fd = component(2, "FD", "Front derailleur", "≈96%", 0.96, .good)
      status = ("GOOD", "All good.", "Lowest: rear derailleur, 78%.")
    case .critical:
      fd = component(2, "FD", "Front derailleur", "≈4%", 0.04, .critical)
      status = ("CHARGE NOW", "Charge now.", "Front derailleur is critical, ≈4%.")
    default:
      fd = component(2, "FD", "Front derailleur", "≈11%", 0.11, .low)
      status = ("LOW", "Charge soon.", "Front derailleur at ≈11%.")
    }
    let rest = [
      component(1, "RD", "Rear derailleur", "78%", 0.78, .good),
      component(3, "L", "Left shifter", "2.91 V", 0.90, .good),
      component(4, "R", "Right shifter", "2.89 V", 0.88, .good),
      component(5, "PM", "Power meter", "85%", 0.85, .good),
    ]
    let components = tone == .good ? [rest[0], fd] + rest.dropFirst() : [fd] + rest
    let current = WidgetStatus(
      tone: tone == .stale ? .low : tone, word: status.0, headline: status.1, summary: status.2,
      weakestId: tone == .good ? 1 : 2, components: components)
    return WidgetBike(
      id: 1, name: "Road bike", lastSeenAt: millis(now.addingTimeInterval(-120)), current: current,
      staleAt: nil, stale: nil)
  }

  static let gravel: WidgetBike = {
    let components = [
      component(11, "RD", "Rear derailleur", "64%", 0.64, .stale),
      component(12, "L", "Left shifter", "2.95 V", 0.92, .stale),
      component(13, "R", "Right shifter", "2.93 V", 0.90, .stale),
    ]
    let current = WidgetStatus(
      tone: .stale, word: "OLD", headline: "Not seen in 14 days.",
      summary: "Readings below may be out of date. Wake the bike or refresh to update.",
      weakestId: 11, components: components)
    return WidgetBike(
      id: 2, name: "Gravel bike", lastSeenAt: millis(now.addingTimeInterval(-14 * 86_400 - 600)),
      current: current, staleAt: nil, stale: nil)
  }()

  static let snapshot = WidgetSnapshot(
    version: 1, writtenAt: millis(now), defaultBikeId: 1, bikes: [road(), gravel])

  private static func millis(_ date: Date) -> Int64 { Int64(date.timeIntervalSince1970 * 1_000) }
}
