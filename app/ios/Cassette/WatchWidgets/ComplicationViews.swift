import SwiftUI
import WidgetKit

/// The watch's complications and Smart Stack card (decision 0017, iteration 17). Faces draw them in
/// full colour or tinted; tinted, the icon, the status word and the fills take the tint
/// (`widgetAccentable`), so the words still carry status, as on Wear.

/// The watch is always dark.
private let palette = Palette(dark: true)

/// Circular, "Weakest battery": the weakest component's level on a gauge, with its code ("OLD" once
/// the bike goes stale).
struct WeakestGaugeView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    let weakest = status.weakest
    Gauge(value: weakest?.fill ?? 0) {
      Text(status.tone == .stale ? "OLD" : weakest?.code ?? "NEW").font(.condensed(11))
    } currentValueLabel: {
      // This style shows only the value label: the code goes under the value, as on Wear.
      VStack(spacing: -1) {
        Text(weakest?.compact ?? "–").font(.condensed(18))
        Text(status.tone == .stale ? "OLD" : weakest?.code ?? "NEW")
          .font(.condensed(10))
          .tracking(0.6)
          .foregroundStyle(.secondary)
      }
    }
    // The ring fills to the level, as Wear's ranged value does.
    .gaugeStyle(.accessoryCircularCapacity)
    .tint(palette.status(status.tone))
    .widgetAccentable()
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(label(status))
  }

  private func label(_ status: WidgetStatus) -> String {
    guard let weakest = status.weakest else { return "\(bike.name): \(status.headline)" }
    return "\(bike.name): \(weakest.name) \(weakest.value), \(status.word.lowercased())"
  }
}

/// Circular, "Bike": the status icon over its word ("CHARGE" for charge now, as on Wear).
struct StatusCircleView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    ZStack {
      AccessoryWidgetBackground()
      VStack(spacing: 2) {
        Icon(name: status.tone.icon, size: 20)
          .foregroundStyle(palette.status(status.tone))
          .widgetAccentable()
        Text(status.tone == .critical ? "CHARGE" : status.short)
          .font(.condensed(12))
          .tracking(0.5)
          .lineLimit(1)
          .minimumScaleFactor(0.6)
      }
      .padding(.horizontal, 5)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(bike.name): \(status.headline)")
  }
}

/// Rectangular on a face: the status line, the headline and a bar per component (the Lock Screen's
/// L2, with room for the headline).
struct StatusRectangleView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    let color = palette.status(status.tone)
    VStack(alignment: .leading, spacing: 2) {
      HStack(spacing: 4) {
        Icon(name: status.tone.icon, size: 12).foregroundStyle(color).widgetAccentable()
        Text(status.short).foregroundStyle(color).widgetAccentable().layoutPriority(1)
        Text("· \(bike.name.uppercased())").foregroundStyle(.secondary)
      }
      .font(.condensed(12))
      .tracking(1.2)
      .lineLimit(1)
      Text(status.headline).font(.condensed(21)).lineLimit(1).minimumScaleFactor(0.6)
      Spacer(minLength: 0)
      HStack(spacing: 4) {
        ForEach(status.components.prefix(8)) { component in
          VStack(alignment: .leading, spacing: 2) {
            Text(component.code).font(.condensed(10)).foregroundStyle(.secondary).lineLimit(1)
            Bar(
              fraction: component.fill, track: .white.opacity(0.18),
              fill: palette.status(status.tone == .stale ? .stale : component.tone), radius: 2
            )
            .frame(height: 4)
          }
        }
      }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(bike.name): \(status.headline) \(status.summary)")
  }
}

/// The Smart Stack card (S1): the Wear tile, the bike's colour behind its headline. The entry view
/// puts the colour in the container background, which the stack shows and a face doesn't.
struct StatusCardView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    let block = palette.block(status.tone)
    VStack(alignment: .leading, spacing: 2) {
      Text(bike.name.uppercased()).font(.condensed(11)).tracking(2.2).lineLimit(1).opacity(0.85)
      HStack(spacing: 5) {
        Icon(name: status.tone.icon, size: 20)
        Text(status.headline).font(.condensed(26)).lineLimit(1).minimumScaleFactor(0.55)
      }
      Spacer(minLength: 0)
      Text(detail(status)).font(.barlow(12)).lineLimit(2)
    }
    .foregroundStyle(block.content)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(bike.name): \(status.headline) \(detail(status))")
  }

  /// "FD ≈11% · 2 min ago": the weakest component and when the bike was seen, on one line.
  private func detail(_ status: WidgetStatus) -> String {
    switch status.tone {
    case .stale: "Readings may be out of date."
    case .unknown: status.summary
    default:
      [status.weakest.map { "\($0.code) \($0.value)" }, bike.ago(at: date)].compactMap { $0 }
        .joined(separator: " · ")
    }
  }
}

/// Inline: the icon and a few words, in the system font (the only one it allows).
struct StatusInlineView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    let text: String =
      switch status.tone {
      case .low, .critical: status.weakest.map { "\($0.code) \($0.word) · \($0.value)" } ?? status.word
      case .stale: "\(bike.name) · OLD"
      default: "\(bike.name) · \(status.word)"
      }
    Label {
      Text(text)
    } icon: {
      Image(status.tone.icon, bundle: .widgets).renderingMode(.template)
    }
  }
}

/// No bike yet, or nothing from the phone yet.
struct NoBikeComplication: View {
  var body: some View {
    Icon(name: "circle-help", size: 20).accessibilityLabel("No bike yet")
  }
}
